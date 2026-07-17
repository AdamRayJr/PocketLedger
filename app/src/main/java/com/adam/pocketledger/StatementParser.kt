package com.adam.pocketledger

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

data class ParsedTransaction(val date: Long, val description: String, val amount: Double, val category: String)
data class RecurringCandidate(val name: String, val averageAmount: Double, val nextDueDate: Long, val occurrences: Int)
data class StatementResult(val transactions: List<ParsedTransaction>, val recurring: List<RecurringCandidate>)

object StatementParser {
    private val linePatterns = listOf(
        Regex("""^\s*(\d{1,2}/\d{1,2}(?:/\d{2,4})?)\s+(.+?)\s+([(-]?\$?[\d,]+\.\d{2}\)?)\s*$"""),
        Regex("""^\s*(\d{4}-\d{2}-\d{2})\s+(.+?)\s+([(-]?\$?[\d,]+\.\d{2}\)?)\s*$""")
    )
    private val ignored = Regex("balance|subtotal|total|payment due|statement", RegexOption.IGNORE_CASE)

    fun parse(text: String, statementYear: Int = Calendar.getInstance().get(Calendar.YEAR)): StatementResult {
        val parsed = text.lineSequence().mapNotNull { line ->
            val match = linePatterns.firstNotNullOfOrNull { it.matchEntire(line) } ?: return@mapNotNull null
            val description = match.groupValues[2].trim().replace(Regex("\\s+"), " ")
            if (ignored.containsMatchIn(description)) return@mapNotNull null
            val raw = match.groupValues[3]
            val number = raw.replace("$", "").replace(",", "").replace("(", "").replace(")", "").toDoubleOrNull() ?: return@mapNotNull null
            val debit = raw.startsWith("-") || raw.startsWith("(") || !Regex("deposit|credit|refund|payroll|interest", RegexOption.IGNORE_CASE).containsMatchIn(description)
            ParsedTransaction(parseDate(match.groupValues[1], statementYear) ?: return@mapNotNull null, description, if (debit) -abs(number) else abs(number), category(description))
        }.distinctBy { Triple(it.date, normalize(it.description), it.amount) }.sortedByDescending { it.date }.toList()
        return StatementResult(parsed, detectRecurring(parsed))
    }

    private fun parseDate(value: String, year: Int): Long? {
        val formats = if (value.count { it == '/' } == 1) listOf("M/d/yyyy") else listOf("M/d/yyyy", "M/d/yy", "yyyy-MM-dd")
        val input = if (value.count { it == '/' } == 1) "$value/$year" else value
        return formats.firstNotNullOfOrNull { runCatching { SimpleDateFormat(it, Locale.US).apply { isLenient=false }.parse(input)?.time }.getOrNull() }
    }

    private fun category(d: String): String = when {
        Regex("rent|mortgage",RegexOption.IGNORE_CASE).containsMatchIn(d)->"Housing"
        Regex("electric|water|gas|internet|phone|utility",RegexOption.IGNORE_CASE).containsMatchIn(d)->"Utilities"
        Regex("grocery|market|walmart|sam's|sams",RegexOption.IGNORE_CASE).containsMatchIn(d)->"Groceries"
        Regex("restaurant|cafe|coffee|doordash|uber eats",RegexOption.IGNORE_CASE).containsMatchIn(d)->"Dining"
        Regex("fuel|shell|exxon|chevron|gas station",RegexOption.IGNORE_CASE).containsMatchIn(d)->"Transportation"
        Regex("insurance",RegexOption.IGNORE_CASE).containsMatchIn(d)->"Insurance"
        Regex("payroll|deposit|salary",RegexOption.IGNORE_CASE).containsMatchIn(d)->"Income"
        else->"General"
    }

    private fun normalize(d: String) = d.uppercase(Locale.US).replace(Regex("""\b\d{3,}\b|[^A-Z ]"""), "").replace(Regex("\\s+"), " ").trim()
    private fun detectRecurring(items: List<ParsedTransaction>): List<RecurringCandidate> = items.filter { it.amount < 0 }.groupBy { normalize(it.description) }.mapNotNull { (name, group) ->
        if (name.length < 3 || group.size < 2) return@mapNotNull null
        val sorted=group.sortedBy { it.date }; val gaps=sorted.zipWithNext { a,b -> (b.date-a.date)/86_400_000 }
        if (gaps.none { it in 25..35 }) return@mapNotNull null
        val amounts=group.map { abs(it.amount) }; val avg=amounts.average()
        if (amounts.any { abs(it-avg) > maxOf(5.0,avg*0.20) }) return@mapNotNull null
        val next=Calendar.getInstance().apply { timeInMillis=sorted.last().date;add(Calendar.MONTH,1) }.timeInMillis
        RecurringCandidate(group.first().description,avg,next,group.size)
    }.sortedByDescending { it.occurrences }
}

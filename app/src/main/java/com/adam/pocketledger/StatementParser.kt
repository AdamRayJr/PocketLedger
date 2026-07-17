package com.adam.pocketledger

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

data class ParsedTransaction(val date: Long, val description: String, val amount: Double, val category: String)
data class RecurringCandidate(val name: String, val averageAmount: Double, val nextDueDate: Long, val occurrences: Int)
data class StatementResult(val transactions: List<ParsedTransaction>, val recurring: List<RecurringCandidate>)

object StatementParser {
    private val datePrefix = Regex("""^\s*((?:\d{1,2}[/.-]\d{1,2}(?:[/.-]\d{2,4})?)|(?:[A-Za-z]{3,9}\s+\d{1,2}(?:,?\s+\d{2,4})?))\s+(.+)$""")
    private val amountToken = Regex("""(?<![\d])(?:CR\s*)?[-+]?\(?\$?\d[\d,]*\.\d{2}\)?(?:\s*(?:CR|DR))?(?![\d])""", RegexOption.IGNORE_CASE)
    private val ignored = Regex("opening balance|closing balance|beginning balance|ending balance|available balance|daily balance|subtotal|total fees|payment due|statement period", RegexOption.IGNORE_CASE)
    private val creditWords = Regex("deposit|credit|refund|payroll|salary|interest paid|payment received|transfer from|ach credit", RegexOption.IGNORE_CASE)
    private val debitWords = Regex("withdrawal|purchase|debit|fee|check|payment to|transfer to|ach debit|card", RegexOption.IGNORE_CASE)

    fun parse(text: String, statementYear: Int = inferYear(text)): StatementResult {
        val normalizedLines = text.replace('\u00A0',' ').lineSequence().map { it.replace(Regex("\\s+")," ").trim() }.filter { it.isNotBlank() }.toList()
        val parsed = normalizedLines.mapNotNull { parseLine(it, statementYear) }
            .distinctBy { Triple(it.date, normalize(it.description), it.amount) }
            .sortedByDescending { it.date }
        return StatementResult(parsed, detectRecurring(parsed))
    }

    private fun parseLine(line:String, year:Int):ParsedTransaction? {
        val dateMatch=datePrefix.find(line) ?: return null
        val date=parseDate(dateMatch.groupValues[1],year) ?: return null
        val remainder=dateMatch.groupValues[2]
        if(ignored.containsMatchIn(remainder)) return null
        val amounts=amountToken.findAll(remainder).toList()
        if(amounts.isEmpty()) return null
        // Statements with withdrawal/deposit/balance columns normally place the transaction amount first.
        val chosen=amounts.first()
        val description=remainder.substring(0,chosen.range.first).trim(' ','-','|')
        if(description.length<2 || ignored.containsMatchIn(description)) return null
        val raw=chosen.value
        val numeric=raw.replace(Regex("(?i)CR|DR"),"").replace("$","").replace(",","").replace("(","").replace(")","").trim().removePrefix("+").removePrefix("-").toDoubleOrNull() ?: return null
        val explicitCredit=raw.contains("CR",true)||raw.trim().startsWith("+")||creditWords.containsMatchIn(description)
        val explicitDebit=raw.contains("DR",true)||raw.trim().startsWith("-")||raw.contains("(")||debitWords.containsMatchIn(description)
        val amount=when{explicitCredit&&!explicitDebit->abs(numeric);else->-abs(numeric)}
        return ParsedTransaction(date,description,amount,category(description))
    }

    private fun inferYear(text:String):Int {
        val current=Calendar.getInstance().get(Calendar.YEAR)
        return Regex("""\b20\d{2}\b""").findAll(text).mapNotNull{it.value.toIntOrNull()}.filter{it in 2000..current+1}.groupingBy{it}.eachCount().maxByOrNull{it.value}?.key ?: current
    }

    private fun parseDate(value:String,year:Int):Long? {
        val cleaned=value.replace('.', '/').replace('-', '/').replace(Regex("\\s+")," ").trim()
        val hasYear=Regex("""(?:[/ ]\d{2,4})$""").containsMatchIn(cleaned) && (cleaned.count{it=='/'}>=2 || Regex("""\b\d{4}$""").containsMatchIn(cleaned))
        val input=if(hasYear) cleaned else "$cleaned $year"
        val formats=listOf("M/d/yyyy","M/d/yy","MMM d yyyy","MMMM d yyyy","MMM d, yyyy","MMMM d, yyyy")
        return formats.firstNotNullOfOrNull{runCatching{SimpleDateFormat(it,Locale.US).apply{isLenient=false}.parse(input)?.time}.getOrNull()}
    }

    private fun category(d:String):String=when{
        Regex("rent|mortgage",RegexOption.IGNORE_CASE).containsMatchIn(d)->"Housing"
        Regex("electric|water|internet|phone|utility",RegexOption.IGNORE_CASE).containsMatchIn(d)->"Utilities"
        Regex("grocery|market|walmart|sam's|sams",RegexOption.IGNORE_CASE).containsMatchIn(d)->"Groceries"
        Regex("restaurant|cafe|coffee|doordash|uber eats",RegexOption.IGNORE_CASE).containsMatchIn(d)->"Dining"
        Regex("fuel|shell|exxon|chevron|gas station",RegexOption.IGNORE_CASE).containsMatchIn(d)->"Transportation"
        Regex("insurance",RegexOption.IGNORE_CASE).containsMatchIn(d)->"Insurance"
        creditWords.containsMatchIn(d)->"Income"
        else->"General"
    }

    private fun normalize(d:String)=d.uppercase(Locale.US).replace(Regex("""\b\d{3,}\b|[^A-Z ]"""),"").replace(Regex("\\s+")," ").trim()
    private fun detectRecurring(items:List<ParsedTransaction>):List<RecurringCandidate> = items.filter{it.amount<0}.groupBy{normalize(it.description)}.mapNotNull{(name,group)->
        if(name.length<3||group.size<2)return@mapNotNull null
        val sorted=group.sortedBy{it.date};val gaps=sorted.zipWithNext{a,b->(b.date-a.date)/86_400_000}
        if(gaps.none{it in 25..35})return@mapNotNull null
        val amounts=group.map{abs(it.amount)};val avg=amounts.average()
        if(amounts.any{abs(it-avg)>maxOf(5.0,avg*.20)})return@mapNotNull null
        val next=Calendar.getInstance().apply{timeInMillis=sorted.last().date;add(Calendar.MONTH,1)}.timeInMillis
        RecurringCandidate(group.first().description,avg,next,group.size)
    }.sortedByDescending{it.occurrences}
}

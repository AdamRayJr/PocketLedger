package com.adam.pocketledger

data class Account(val id: Long, val name: String, val type: String, val openingBalance: Double)
data class MoneyTransaction(val id: Long, val accountId: Long, val title: String, val amount: Double, val category: String, val date: Long, val recurring: String)
data class Budget(val id: Long, val category: String, val monthlyLimit: Double)
data class Bill(val id: Long, val name: String, val amount: Double, val dueDate: Long, val repeatMonths: Int, val reminderDays: Int, val paid: Boolean)
data class Payment(val id: Long, val billId: Long, val amount: Double, val paidAt: Long)

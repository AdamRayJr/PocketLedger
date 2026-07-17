package com.adam.pocketledger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LedgerDb(context: Context) : SQLiteOpenHelper(context, "pocket_ledger.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,type TEXT NOT NULL,opening REAL NOT NULL)")
        db.execSQL("CREATE TABLE transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,account_id INTEGER NOT NULL,title TEXT NOT NULL,amount REAL NOT NULL,category TEXT NOT NULL,date INTEGER NOT NULL,recurring TEXT NOT NULL DEFAULT 'None')")
        db.execSQL("CREATE TABLE budgets(id INTEGER PRIMARY KEY AUTOINCREMENT,category TEXT NOT NULL UNIQUE,monthly_limit REAL NOT NULL)")
        db.execSQL("CREATE TABLE bills(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,amount REAL NOT NULL,due_date INTEGER NOT NULL,repeat_months INTEGER NOT NULL,reminder_days INTEGER NOT NULL,paid INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE payments(id INTEGER PRIMARY KEY AUTOINCREMENT,bill_id INTEGER NOT NULL,amount REAL NOT NULL,paid_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun addAccount(name: String, type: String, opening: Double) = writableDatabase.insert("accounts", null, ContentValues().apply { put("name",name); put("type",type); put("opening",opening) })
    fun deleteAccount(id:Long) { writableDatabase.beginTransaction();try{writableDatabase.delete("transactions","account_id=?",arrayOf(id.toString()));writableDatabase.delete("accounts","id=?",arrayOf(id.toString()));writableDatabase.setTransactionSuccessful()}finally{writableDatabase.endTransaction()} }
    fun accounts(): List<Account> = readableDatabase.rawQuery("SELECT * FROM accounts ORDER BY name",null).use { c -> buildList { while(c.moveToNext()) add(Account(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3))) } }
    fun addTransaction(accountId:Long,title:String,amount:Double,category:String,date:Long,recurring:String) = writableDatabase.insert("transactions",null,ContentValues().apply { put("account_id",accountId);put("title",title);put("amount",amount);put("category",category);put("date",date);put("recurring",recurring) })
    fun transactions():List<MoneyTransaction> = readableDatabase.rawQuery("SELECT * FROM transactions ORDER BY date DESC",null).use { c -> buildList { while(c.moveToNext()) add(MoneyTransaction(c.getLong(0),c.getLong(1),c.getString(2),c.getDouble(3),c.getString(4),c.getLong(5),c.getString(6))) } }
    fun deleteTransaction(id:Long)=writableDatabase.delete("transactions","id=?",arrayOf(id.toString()))
    fun importTransactions(accountId:Long,items:List<ParsedTransaction>):Int { var count=0;writableDatabase.beginTransaction();try{items.forEach{val exists=readableDatabase.rawQuery("SELECT 1 FROM transactions WHERE account_id=? AND title=? AND amount=? AND date=? LIMIT 1",arrayOf(accountId.toString(),it.description,it.amount.toString(),it.date.toString())).use{c->c.moveToFirst()};if(!exists){addTransaction(accountId,it.description,it.amount,it.category,it.date,"None");count++}};writableDatabase.setTransactionSuccessful()}finally{writableDatabase.endTransaction()};return count }
    fun addBudget(category:String,limit:Double) { writableDatabase.insertWithOnConflict("budgets",null,ContentValues().apply { put("category",category);put("monthly_limit",limit) },SQLiteDatabase.CONFLICT_REPLACE) }
    fun budgets():List<Budget> = readableDatabase.rawQuery("SELECT * FROM budgets ORDER BY category",null).use { c -> buildList { while(c.moveToNext()) add(Budget(c.getLong(0),c.getString(1),c.getDouble(2))) } }
    fun deleteBudget(id:Long)=writableDatabase.delete("budgets","id=?",arrayOf(id.toString()))
    fun addBill(name:String,amount:Double,due:Long,repeat:Int,reminder:Int):Long = writableDatabase.insert("bills",null,ContentValues().apply { put("name",name);put("amount",amount);put("due_date",due);put("repeat_months",repeat);put("reminder_days",reminder);put("paid",0) })
    fun bills():List<Bill> = readableDatabase.rawQuery("SELECT * FROM bills ORDER BY paid,due_date",null).use { c -> buildList { while(c.moveToNext()) add(Bill(c.getLong(0),c.getString(1),c.getDouble(2),c.getLong(3),c.getInt(4),c.getInt(5),c.getInt(6)==1)) } }
    fun deleteBill(id:Long) { writableDatabase.beginTransaction();try{writableDatabase.delete("payments","bill_id=?",arrayOf(id.toString()));writableDatabase.delete("bills","id=?",arrayOf(id.toString()));writableDatabase.setTransactionSuccessful()}finally{writableDatabase.endTransaction()} }
    fun markPaid(bill:Bill) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insert("payments",null,ContentValues().apply { put("bill_id",bill.id);put("amount",bill.amount);put("paid_at",System.currentTimeMillis()) })
            writableDatabase.update("bills",ContentValues().apply { put("paid",1) },"id=?",arrayOf(bill.id.toString()))
            if (bill.repeatMonths > 0) {
                val next = java.util.Calendar.getInstance().apply { timeInMillis=bill.dueDate; add(java.util.Calendar.MONTH,bill.repeatMonths) }.timeInMillis
                addBill(bill.name,bill.amount,next,bill.repeatMonths,bill.reminderDays)
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
    fun payments():List<Payment> = readableDatabase.rawQuery("SELECT * FROM payments ORDER BY paid_at DESC",null).use { c -> buildList { while(c.moveToNext()) add(Payment(c.getLong(0),c.getLong(1),c.getDouble(2),c.getLong(3))) } }
    fun deletePayment(id:Long)=writableDatabase.delete("payments","id=?",arrayOf(id.toString()))

    fun processRecurringTransactions() {
        val now=System.currentTimeMillis()
        transactions().filter { it.recurring.equals("Weekly",true) || it.recurring.equals("Monthly",true) }.groupBy { listOf(it.accountId,it.title,it.amount,it.category,it.recurring) }.values.forEach { group ->
            val latest=group.maxBy { it.date }
            var next=java.util.Calendar.getInstance().apply { timeInMillis=latest.date; if(latest.recurring.equals("Weekly",true)) add(java.util.Calendar.WEEK_OF_YEAR,1) else add(java.util.Calendar.MONTH,1) }.timeInMillis
            while(next<=now) {
                addTransaction(latest.accountId,latest.title,latest.amount,latest.category,next,latest.recurring)
                next=java.util.Calendar.getInstance().apply { timeInMillis=next; if(latest.recurring.equals("Weekly",true)) add(java.util.Calendar.WEEK_OF_YEAR,1) else add(java.util.Calendar.MONTH,1) }.timeInMillis
            }
        }
    }
}

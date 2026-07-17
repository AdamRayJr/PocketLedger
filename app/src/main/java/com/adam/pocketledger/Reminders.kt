package com.adam.pocketledger

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    fun schedule(context:Context,bill:Bill) {
        val trigger = bill.dueDate - TimeUnit.DAYS.toMillis(bill.reminderDays.toLong())
        val intent = Intent(context,ReminderReceiver::class.java).putExtra("bill_id",bill.id).putExtra("name",bill.name).putExtra("amount",bill.amount).putExtra("due",bill.dueDate)
        val pending = PendingIntent.getBroadcast(context,bill.id.toInt(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(AlarmManager::class.java)
        if (trigger > System.currentTimeMillis()) {
            if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pending)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pending)
        }
    }
    fun rescheduleAll(context:Context) = LedgerDb(context).bills().filter { !it.paid }.forEach { schedule(context,it) }
}

class ReminderReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val channelId="bill_reminders"
        val manager=context.getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel(channelId,"Bill reminders",NotificationManager.IMPORTANCE_HIGH))
        val due=intent.getLongExtra("due",0)
        val overdue=due<System.currentTimeMillis()
        val name=intent.getStringExtra("name") ?: "Bill"
        val amount=intent.getDoubleExtra("amount",0.0)
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,channelId).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(if(overdue) "$name is overdue" else "$name is coming up").setContentText("$${"%.2f".format(amount)} ${if(overdue) "was due" else "is due soon"}").setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(open).build()
        manager.notify(intent.getLongExtra("bill_id",0).toInt(),notification)
    }
}

class BootReceiver:BroadcastReceiver() { override fun onReceive(context:Context,intent:Intent) { if(intent.action==Intent.ACTION_BOOT_COMPLETED) ReminderScheduler.rescheduleAll(context) } }

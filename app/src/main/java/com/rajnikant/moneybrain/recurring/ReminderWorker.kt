package com.rajnikant.moneybrain.recurring

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rajnikant.moneybrain.MoneyBrainApp
import com.rajnikant.moneybrain.money.Money
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MoneyBrainApp
        val due = RecurringMath.dueWithin(app.database.recurringDao().observeAll().first().map { it.toItem() }, LocalDate.now().toString(), 3)
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Upcoming bills", NotificationManager.IMPORTANCE_DEFAULT))
        due.forEach { item -> manager.notify(item.id.toInt(), NotificationCompat.Builder(applicationContext, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Upcoming bill").setContentText("${item.name} ${Money.formatPaise(item.expectedAmountPaise)} due ${item.nextDueIso}").build()) }
        return Result.success()
    }
    companion object { const val CHANNEL = "upcoming_bills"; const val UNIQUE = "recurring_reminders"
        fun schedule(context: Context) { WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()) }
    }
}

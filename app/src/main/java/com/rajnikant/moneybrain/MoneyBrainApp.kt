package com.rajnikant.moneybrain

import android.app.Application
import com.rajnikant.moneybrain.data.MoneyBrainDatabase
import com.rajnikant.moneybrain.recurring.ReminderWorker

class MoneyBrainApp : Application() {
    val database: MoneyBrainDatabase by lazy { MoneyBrainDatabase.create(this) }
    override fun onCreate() { super.onCreate(); ReminderWorker.schedule(this) }
}

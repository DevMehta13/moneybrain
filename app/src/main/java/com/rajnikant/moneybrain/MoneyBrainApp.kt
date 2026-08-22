package com.rajnikant.moneybrain

import android.app.Application
import com.rajnikant.moneybrain.data.MoneyBrainDatabase

class MoneyBrainApp : Application() {
    val database: MoneyBrainDatabase by lazy { MoneyBrainDatabase.create(this) }
}

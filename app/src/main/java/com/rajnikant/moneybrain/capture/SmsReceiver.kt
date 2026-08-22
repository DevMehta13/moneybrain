package com.rajnikant.moneybrain.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.room.withTransaction
import com.rajnikant.moneybrain.MoneyBrainApp
import com.rajnikant.moneybrain.data.RoomCaptureStore
import com.rajnikant.moneybrain.recurring.applyRecurringMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val fullBodiesBySender = messages
            .groupBy { it.originatingAddress.orEmpty() }
            .filterKeys { it.isNotBlank() }
            .mapValues { (_, parts) -> parts.joinToString(separator = "") { it.messageBody.orEmpty() } }
        if (fullBodiesBySender.isEmpty()) return

        val pendingResult = goAsync()
        val app = context.applicationContext as MoneyBrainApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val processor = CaptureProcessor(RoomCaptureStore(app.database))
                fullBodiesBySender.forEach { (sender, fullBody) ->
                    app.database.withTransaction {
                        val outcome = processor.process(sender, fullBody, System.currentTimeMillis())
                        if (outcome is CaptureOutcome.Captured) {
                            app.database.transactionDao().getById(outcome.transactionId)?.let { applyRecurringMatch(app.database, it) }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

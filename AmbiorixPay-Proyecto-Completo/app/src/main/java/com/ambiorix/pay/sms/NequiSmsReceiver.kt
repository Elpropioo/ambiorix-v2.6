package com.ambiorix.pay.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.ambiorix.pay.data.Payment
import com.ambiorix.pay.data.PaymentRepository
import com.ambiorix.pay.data.SettingsRepository
import com.ambiorix.pay.data.PaymentSource
import com.ambiorix.pay.notification.NotificationPaymentEvents

/**
 * Receives new SMS broadcasts and only emits payments whose body matches Nequi's
 * supported payment-notification structure. SMS content is processed locally.
 */
class NequiSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val appContext = context.applicationContext
        val settings = SettingsRepository.getInstance(appContext).getSettings()
        if (!settings.isServiceEnabled || !settings.smsPaymentEnabled) return
        if (!settings.businessHours.isWithinBusinessHours()) return

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron leer los SMS entrantes", e)
            return
        }

        // Combine multipart SMS parts from the same sender before parsing.
        messages.groupBy { it.originatingAddress.orEmpty() }.forEach { (address, parts) ->
            val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
            when (val parsed = NequiSmsParser.parse(address, body)) {
                is SmsPaymentParseResult.Success -> {
                    val p = parsed.payment
                    val fingerprint = PaymentDeduplicator.computeSmsFingerprint(
                        p.amount, p.senderName, p.receiverBusiness,
                        p.transactionDateTimeText, p.channel, p.rawBody
                    )
                    if (PaymentDeduplicator.isDuplicateSms(fingerprint)) return@forEach
                    if (PaymentDeduplicator.isDuplicateCrossSourceForSms(p.amount)) {
                        PaymentDeduplicator.markSmsProcessed(fingerprint, p.amount, p.senderName)
                        return@forEach
                    }
                    PaymentDeduplicator.markSmsProcessed(fingerprint, p.amount, p.senderName)

                    val payment = Payment(
                        amount = p.amount,
                        appName = "Nequi SMS",
                        packageName = "sms.nequi",
                        timestampMillis = System.currentTimeMillis(),
                        senderName = p.senderName,
                        channel = p.channel,
                        receiverBusiness = p.receiverBusiness,
                        transactionDateTimeText = p.transactionDateTimeText,
                        source = PaymentSource.SMS
                    )
                    PaymentRepository.getInstance(appContext).recordPayment(payment)
                    NotificationPaymentEvents.emitRealPayment(payment)
                }
                is SmsPaymentParseResult.Ignored -> {
                    Log.d(TAG, "SMS ignorado: estructura no compatible")
                }
            }
        }
    }

    companion object {
        private const val TAG = "NequiSmsReceiver"
    }
}

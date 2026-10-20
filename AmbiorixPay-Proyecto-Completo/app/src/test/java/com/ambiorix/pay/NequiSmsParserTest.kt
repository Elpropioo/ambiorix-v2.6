package com.ambiorix.pay

import com.ambiorix.pay.sms.NequiSmsParser
import com.ambiorix.pay.sms.SmsPaymentParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NequiSmsParserTest {
    @Test
    fun parsesBreBPaymentNotification() {
        val body = "Notificacion Nequi: el negocio (TECNICABLES, nombre ficticio) recibió $1 de SANTIAGO ALEJANDRO MORGAN AVILA el 19/10/2026 a las 07:11 p.m. a través de Bre-B."
        val result = NequiSmsParser.parse("890806", body)
        assertTrue("Expected a supported payment SMS", result is SmsPaymentParseResult.Success)
        val payment = (result as SmsPaymentParseResult.Success).payment
        assertEquals(1L, payment.amount)
        assertEquals("SANTIAGO ALEJANDRO MORGAN AVILA", payment.senderName)
        assertEquals("TECNICABLES", payment.receiverBusiness)
        assertEquals("Bre-B", payment.channel)
    }

    @Test
    fun ignoresOtpMessage() {
        val result = NequiSmsParser.parse("890806", "Tu código de seguridad es 123456")
        assertTrue(result is SmsPaymentParseResult.Ignored)
    }

    @Test
    fun ignoresUnstructuredMoneyMessage() {
        val result = NequiSmsParser.parse("890806", "Recuerda pagar $35.000 hoy")
        assertTrue(result is SmsPaymentParseResult.Ignored)
    }
}

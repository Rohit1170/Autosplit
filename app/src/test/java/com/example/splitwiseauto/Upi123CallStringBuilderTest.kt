package com.example.splitwiseauto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Upi123CallStringBuilderTest {

    @Test
    fun buildsValidDialString() {
        val result = Upi123CallStringBuilder.build(
            serviceNumber = "8035009999",
            recipientPhone = "9876543210",
            amountRupees = 250.0
        )

        assertTrue(result is Upi123CallStringBuilder.BuildResult.Success)
        assertEquals(
            "tel:8035009999,,1,9876543210,,250,,1",
            (result as Upi123CallStringBuilder.BuildResult.Success).dialString
        )
    }

    @Test
    fun rejectsInvalidServiceNumber() {
        val result = Upi123CallStringBuilder.build(
            serviceNumber = "abc",
            recipientPhone = "9876543210",
            amountRupees = 100.0
        )
        assertEquals(
            Upi123CallStringBuilder.BuildResult.Failure(Upi123CallStringBuilder.Reason.INVALID_SERVICE_NUMBER),
            result
        )
    }

    @Test
    fun rejectsServiceNumberTooShort() {
        val result = Upi123CallStringBuilder.build(
            serviceNumber = "12",
            recipientPhone = "9876543210",
            amountRupees = 100.0
        )
        assertEquals(
            Upi123CallStringBuilder.BuildResult.Failure(Upi123CallStringBuilder.Reason.INVALID_SERVICE_NUMBER),
            result
        )
    }

    @Test
    fun rejectsRecipientNotTenDigits() {
        val result = Upi123CallStringBuilder.build(
            serviceNumber = "8035009999",
            recipientPhone = "98765432100",
            amountRupees = 100.0
        )
        assertEquals(
            Upi123CallStringBuilder.BuildResult.Failure(Upi123CallStringBuilder.Reason.INVALID_RECIPIENT_NUMBER),
            result
        )
    }

    @Test
    fun rejectsRecipientNotStartingWithValidDigit() {
        val result = Upi123CallStringBuilder.build(
            serviceNumber = "8035009999",
            recipientPhone = "1234567890",
            amountRupees = 100.0
        )
        assertEquals(
            Upi123CallStringBuilder.BuildResult.Failure(Upi123CallStringBuilder.Reason.INVALID_RECIPIENT_NUMBER),
            result
        )
    }

    @Test
    fun rejectsFractionalAmount() {
        val result = Upi123CallStringBuilder.build(
            serviceNumber = "8035009999",
            recipientPhone = "9876543210",
            amountRupees = 100.5
        )
        assertEquals(
            Upi123CallStringBuilder.BuildResult.Failure(Upi123CallStringBuilder.Reason.AMOUNT_NOT_WHOLE_RUPEE),
            result
        )
    }

    @Test
    fun rejectsAmountBelowMinimum() {
        val result = Upi123CallStringBuilder.build(
            serviceNumber = "8035009999",
            recipientPhone = "9876543210",
            amountRupees = 0.0
        )
        assertEquals(
            Upi123CallStringBuilder.BuildResult.Failure(Upi123CallStringBuilder.Reason.AMOUNT_TOO_LOW),
            result
        )
    }

    @Test
    fun rejectsAmountAboveMaximum() {
        val result = Upi123CallStringBuilder.build(
            serviceNumber = "8035009999",
            recipientPhone = "9876543210",
            amountRupees = 200_000.0
        )
        assertEquals(
            Upi123CallStringBuilder.BuildResult.Failure(Upi123CallStringBuilder.Reason.AMOUNT_TOO_HIGH),
            result
        )
    }
}

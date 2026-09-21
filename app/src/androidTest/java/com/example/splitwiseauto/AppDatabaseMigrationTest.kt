package com.example.splitwiseauto

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Proves the 9->10 migration is genuinely additive: every existing
 * expenses/pending_expense_notifications row survives with every existing
 * value untouched, the two new nullable columns come back null on old
 * rows, and the new payment_attempts table is there and writable.
 *
 * This is an INSTRUMENTED test (MigrationTestHelper needs a real SQLite
 * environment) — it runs via `./gradlew connectedAndroidTest` on a device
 * or emulator, NOT with `./gradlew test` or assembleDebug. It also needs
 * app/schemas/9.json to exist, which Room's annotation processor generates
 * the next time this module is compiled (see the room.schemaLocation
 * config in build.gradle.kts) — if this test fails with a "cannot find
 * schema" error, that's why: run a build once first.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDbName = "migration-test-9-10"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate9To10_keepsExistingDataAndAddsPaymentAttempts() {
        helper.createDatabase(testDbName, 9).apply {
            execSQL(
                "INSERT INTO expenses " +
                    "(id, amount, merchant, bank, date, upi, payer, category, tripId) " +
                    "VALUES (1, 100.0, 'Test Merchant', 'Test Bank', '01-Jan-26', " +
                    "'test@upi', 'Me', 'Other', NULL)"
            )
            execSQL(
                "INSERT INTO pending_expense_notifications " +
                    "(id, amount, merchant, bank, date, balance, upi, timestampMillis, createdAtMillis) " +
                    "VALUES (1, '50.00', 'Pending Merchant', 'Test Bank', '01-Jan-26', " +
                    "'1000.00', 'pending@upi', 1700000000000, 1700000000000)"
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            testDbName,
            10,
            true,
            AppDatabase.MIGRATION_9_10
        )

        val expenseCursor = migratedDb.query(
            "SELECT amount, merchant, paymentAttemptId FROM expenses WHERE id = 1"
        )
        assertTrue(expenseCursor.moveToFirst())
        assertEquals(
            100.0,
            expenseCursor.getDouble(expenseCursor.getColumnIndexOrThrow("amount")),
            0.001
        )
        assertEquals(
            "Test Merchant",
            expenseCursor.getString(expenseCursor.getColumnIndexOrThrow("merchant"))
        )
        assertTrue(
            "new paymentAttemptId column must default to null on pre-existing rows",
            expenseCursor.isNull(expenseCursor.getColumnIndexOrThrow("paymentAttemptId"))
        )
        expenseCursor.close()

        val pendingCursor = migratedDb.query(
            "SELECT amount, merchant, source, paymentAttemptId FROM pending_expense_notifications WHERE id = 1"
        )
        assertTrue(pendingCursor.moveToFirst())
        assertEquals(
            "50.00",
            pendingCursor.getString(pendingCursor.getColumnIndexOrThrow("amount"))
        )
        assertEquals(
            "Pending Merchant",
            pendingCursor.getString(pendingCursor.getColumnIndexOrThrow("merchant"))
        )
        assertTrue(
            "new source column must default to null on pre-existing rows",
            pendingCursor.isNull(pendingCursor.getColumnIndexOrThrow("source"))
        )
        assertTrue(
            pendingCursor.isNull(pendingCursor.getColumnIndexOrThrow("paymentAttemptId"))
        )
        pendingCursor.close()

        // New table exists and is usable.
        migratedDb.execSQL(
            "INSERT INTO payment_attempts " +
                "(id, upiId, expectedAmount, status, createdAtMillis, rail) " +
                "VALUES (1, 'friend@upi', 25.0, 'SUCCESS', 1700000000000, 'USSD_99')"
        )
        val attemptCursor = migratedDb.query("SELECT upiId, status FROM payment_attempts WHERE id = 1")
        assertTrue(attemptCursor.moveToFirst())
        assertEquals("friend@upi", attemptCursor.getString(attemptCursor.getColumnIndexOrThrow("upiId")))
        assertEquals("SUCCESS", attemptCursor.getString(attemptCursor.getColumnIndexOrThrow("status")))
        attemptCursor.close()

        migratedDb.close()
    }
}

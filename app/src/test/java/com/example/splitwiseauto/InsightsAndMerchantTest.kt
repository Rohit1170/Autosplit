package com.example.splitwiseauto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightsAndMerchantTest {

    // ---- Fix 1: percentages add up to 100 ----

    @Test fun `percents always sum to 100`() {
        listOf(
            listOf(73.0, 20.0, 6.0, 1.0),
            listOf(1.0, 1.0, 1.0),
            listOf(38901.0, 22000.0, 4100.5, 312.25, 99.0),
            listOf(5.0)
        ).forEach { values ->
            assertEquals(values.toString(), 100, InsightsMath.percents(values).sum())
        }
    }

    @Test fun `percents of zero total are zero`() {
        assertEquals(listOf(0, 0), InsightsMath.percents(listOf(0.0, 0.0)))
    }

    // ---- Fix 2: pace excludes trips ----

    @Test fun `pace uses only non-trip spending`() {
        // 7 days in, ₹7,000 regular spend (+ a trip that the caller excluded).
        val p = InsightsMath.pace(nonTripSpent = 7000.0, daysElapsed = 7, daysInMonth = 31, isCurrentMonth = true)
        assertEquals(1000.0, p.dailyAverage, 0.001)
        assertEquals(31000.0, p.projectedMonthEnd!!, 0.001)
    }

    @Test fun `no projection when there is no regular spending`() {
        val p = InsightsMath.pace(0.0, 7, 31, isCurrentMonth = true)
        assertNull(p.projectedMonthEnd)
    }

    @Test fun `no projection for a finished month`() {
        assertNull(InsightsMath.pace(5000.0, 30, 30, isCurrentMonth = false).projectedMonthEnd)
    }

    // ---- Fix 3: merchant keys + seed map ----

    @Test fun `merchant key normalisation`() {
        assertEquals("ZEPTO", MerchantCategorizer.normalizeKey("ZEPTO*"))
        assertEquals("ZEPTO", MerchantCategorizer.normalizeKey("Zepto Pvt Ltd"))
        assertEquals("ZEPTO", MerchantCategorizer.normalizeKey("zepto"))
        assertEquals("SONAMHOTEL", MerchantCategorizer.normalizeKey("SONAM HOTEL"))
    }

    @Test fun `static map exact, prefix and longest match`() {
        assertEquals("Travel", MerchantCategorizer.staticCategoryFor("IRCTC"))
        assertEquals("Food", MerchantCategorizer.staticCategoryFor("ZOMATO"))
        assertEquals("Groceries", MerchantCategorizer.staticCategoryFor("JIOMART"))   // beats JIO
        assertEquals("Bills", MerchantCategorizer.staticCategoryFor("JIOPREPAID"))
        assertEquals("Groceries", MerchantCategorizer.staticCategoryFor("SWIGGYINSTAMART"))
        assertEquals("Entertainment", MerchantCategorizer.staticCategoryFor("BOOKMYSHOW"))
        assertNull(MerchantCategorizer.staticCategoryFor("SONAMHOTEL"))
    }

    @Test fun `new built-in categories map common merchants`() {
        assertEquals("Transport", MerchantCategorizer.staticCategoryFor("UBER"))
        assertEquals("Transport", MerchantCategorizer.staticCategoryFor("RAPIDO"))
        assertEquals("Fuel", MerchantCategorizer.staticCategoryFor("HPCL"))
        assertEquals("Health", MerchantCategorizer.staticCategoryFor("APOLLOPHARMACY"))
        assertEquals("Subscriptions", MerchantCategorizer.staticCategoryFor("NETFLIX"))
        assertEquals("Transport", categorize("Uber India").name)
        assertEquals("Travel", categorize("IRCTC").name)
        assertEquals("Fuel", categorize("HP Petrol Pump").name)
    }

    @Test fun `indian shop words are categorised`() {
        assertEquals("Food", categorize("SHREE GANESH HOTEL").name)
        assertEquals("Food", categorize("Punjabi Dhaba").name)
        assertEquals("Food", categorize("UDUPI BHAVAN").name)
        assertEquals("Food", categorize("Agarwal Sweets").name)
        assertEquals("Health", categorize("Gupta Medical Store").name)
        assertEquals("Health", categorize("NEW LIFE CHEMIST").name)
        assertEquals("Fuel", categorize("SAI PETROLEUM").name)
        assertEquals("Fuel", categorize("Highway Filling Station").name)
        assertEquals("Groceries", categorize("Ramesh Kirana").name)
        assertEquals("Groceries", categorize("Laxmi General Store").name)
        assertEquals("Travel", categorize("Sea View Resort").name)
        assertEquals("Shopping", categorize("Vishal Garments").name)
    }

    @Test fun `bank cut-off payee names still categorised`() {
        assertEquals("Food", categorize("SHREE GANESH HO").name)
        assertEquals("Food", categorize("ANNAPURNA RESTAU").name)
        assertEquals("Health", categorize("SAI MEDIC").name)
        assertEquals("Health", categorize("CITY HOSP").name)
        assertEquals("Fuel", categorize("KRISHNA PETROL").name)
        assertEquals("Groceries", categorize("SHIV KIR").name)
        // A lone short word is not treated as a cut-off name.
        assertEquals("Other", categorize("HO").name)
        assertEquals("Other", categorize("RAHUL SHARMA").name)
    }

    @Test fun `short keywords need whole words`() {
        assertEquals("Other", categorize("Coca Cola Distributor").name)   // not "ola"
        assertEquals("Other", categorize("Dinesh Kumar").name)            // not "dine"
        assertEquals("Transport", categorize("City Cabs").name)
        assertEquals("Transport", categorize("OLA").name)
    }

    @Test fun `unknown category name falls back to Other`() {
        assertEquals("Other", categoryByName("Not a category").name)
        assertEquals("Transport", categoryByName("transport").name)
        assertNull(CustomCategories.validate("Pets"))
        assertEquals("\"Food\" already exists", CustomCategories.validate("food"))
        assertEquals("Enter a name", CustomCategories.validate("   "))
    }

    @Test fun `every seed category is a real app category`() {
        MerchantCategorizer.STATIC_MAP.values.toSet().forEach { name ->
            assertEquals(name, categoryByName(name).name)
        }
    }

    // ---- Widget: today vs yesterday ----

    @Test fun `widget midnight alarm targets the start of tomorrow`() {
        val now = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.OCTOBER, 31, 23, 50, 0) }
        val next = java.util.Calendar.getInstance().apply { timeInMillis = SplitleeWidget.nextMidnightMillis(now) }
        assertEquals(2026, next.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.NOVEMBER, next.get(java.util.Calendar.MONTH))
        assertEquals(1, next.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(0, next.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, next.get(java.util.Calendar.MINUTE))
    }

    @Test fun `widget compares today with yesterday`() {
        assertEquals("▲ ₹120 vs yesterday", SplitleeWidget.compareWithYesterday(420.0, 300.0).text)
        assertEquals("▼ ₹80 vs yesterday", SplitleeWidget.compareWithYesterday(220.0, 300.0).text)
        assertEquals("Same as yesterday", SplitleeWidget.compareWithYesterday(300.4, 300.0).text)
        assertEquals("Nothing spent yesterday", SplitleeWidget.compareWithYesterday(0.0, 0.0).text)
    }
}

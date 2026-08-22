// ARCHITECT-OWNED — these tests define correct money behaviour; they must pass, never be edited to pass.
package com.rajnikant.moneybrain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    // --- formatting ---

    @Test fun `zero is bare rupee symbol`() = assertEquals("₹0", Money.formatPaise(0))
    @Test fun `whole rupees drop the decimals`() = assertEquals("₹1", Money.formatPaise(100))
    @Test fun `paise are two digits`() = assertEquals("₹123.45", Money.formatPaise(12_345))
    @Test fun `single paise pads to two digits`() = assertEquals("₹0.05", Money.formatPaise(5))
    @Test fun `thousand groups western style below a lakh`() =
        assertEquals("₹1,000", Money.formatPaise(100_000))
    @Test fun `lakhs group in pairs`() = assertEquals("₹1,23,456.78", Money.formatPaise(12_345_678))
    @Test fun `ten lakh`() = assertEquals("₹10,00,000", Money.formatPaise(100_000_000))
    @Test fun `crores group in pairs`() =
        assertEquals("₹12,34,56,789.01", Money.formatPaise(123_456_789_01))
    @Test fun `negative amounts carry a leading minus`() =
        assertEquals("-₹123.45", Money.formatPaise(-12_345))

    // --- parsing ---

    @Test fun `plain integer is rupees`() = assertEquals(30_000L, Money.parseToPaise("300"))
    @Test fun `two decimals are paise`() = assertEquals(29_950L, Money.parseToPaise("299.50"))
    @Test fun `one decimal means tens of paise`() = assertEquals(29_950L, Money.parseToPaise("299.5"))
    @Test fun `bare decimal parses`() = assertEquals(50L, Money.parseToPaise(".5"))
    @Test fun `trailing dot parses as whole rupees`() = assertEquals(1_200L, Money.parseToPaise("12."))
    @Test fun `symbol commas and spaces are ignored`() =
        assertEquals(12_345_678L, Money.parseToPaise(" ₹1,23,456.78 "))
    @Test fun `negative parses`() = assertEquals(-30_000L, Money.parseToPaise("-300"))
    @Test fun `format then parse round trips`() {
        for (amount in longArrayOf(0, 1, 99, 100, 12_345, 100_000, 12_345_678, 100_000_000)) {
            assertEquals(amount, Money.parseToPaise(Money.formatPaise(amount)))
        }
    }

    // --- rejects ---

    @Test fun `empty is null`() = assertNull(Money.parseToPaise(""))
    @Test fun `blank is null`() = assertNull(Money.parseToPaise("   "))
    @Test fun `letters are null`() = assertNull(Money.parseToPaise("12a"))
    @Test fun `three decimals are null`() = assertNull(Money.parseToPaise("12.345"))
    @Test fun `two dots are null`() = assertNull(Money.parseToPaise("1.2.3"))
    @Test fun `lone minus is null`() = assertNull(Money.parseToPaise("-"))
    @Test fun `lone dot is null`() = assertNull(Money.parseToPaise("."))
    @Test fun `absurd length is null`() = assertNull(Money.parseToPaise("99999999999999"))
}

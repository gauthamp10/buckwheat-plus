package com.danilkinkin.buckwheat.autoImport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class SpendMessageParserTest {
    private fun amount(value: String) = BigDecimal(value).setScale(2)

    @Test
    fun parsesUaeCardPurchase() {
        val spend = SpendMessageParser.parse(
            "Purchase of AED 45.50 with Card ending 1234 at CARREFOUR DUBAI on 31/10/2026 09:10. " +
                    "Avl Cr. Limit is AED 10,000.00"
        )

        assertNotNull(spend)
        assertEquals(amount("45.50"), spend!!.amount)
        assertEquals("AED", spend.currency)
        assertEquals("1234", spend.cardLast4)
        assertEquals("Carrefour Dubai", spend.merchant)
    }

    @Test
    fun parsesRealUaeBankMessages() {
        val purchase = SpendMessageParser.parse(
            "Purchase of AED 13.00 with Credit Card ending 0956 at BUTTER BREAD BAKERY LL, DUBAI. " +
                    "Avl Cr. Limit is AED 19,828.00"
        )
        assertEquals(amount("13.00"), purchase!!.amount)
        assertEquals("0956", purchase.cardLast4)

        val payment = SpendMessageParser.parse(
            "Payment of AED 24.45 to SMILES FOOD with Credit Card ending 0956. Avl Cr. Limit is AED 19,803.55."
        )
        assertEquals(amount("24.45"), payment!!.amount)

        val nol = SpendMessageParser.parse(
            "Dear Customer, your payment of 200.00 AED on your nol card with Tag ID 333883007 has been " +
                    "completed successfully. To activate your pending balance on your nol card, please tap your card"
        )
        assertEquals(amount("200.00"), nol!!.amount)

        val mashreq = SpendMessageParser.parse(
            "Thank you for using Mashreq Credit Card ending 2839 for AED 95.00 at VOX CINEMAS MOE on 31-Oct-2026. " +
                    "Available limit is AED 14,905.00"
        )
        assertEquals(amount("95.00"), mashreq!!.amount)
        assertEquals("2839", mashreq.cardLast4)
        assertEquals("Vox Cinemas Moe", mashreq.merchant)

        val adcb = SpendMessageParser.parse(
            "Your Cr.Card XXX4321 was used for AED64.50 on 31/10/2026 11:20:01 at NOON FOOD,DUBAI. " +
                    "Avl Cr.Limit is AED 9,435.50"
        )
        assertEquals(amount("64.50"), adcb!!.amount)
        assertEquals("Noon Food, Dubai", adcb.merchant)

        assertNull(SpendMessageParser.parse(
            "Your transaction of AED 300.00 at VOX CINEMAS with Credit Card ending 2839 was declined"
        ))
        assertNull(SpendMessageParser.parse(
            "A payment on your card needs your confirmation. Please log in to your ENBD X app and navigate " +
                    "to the Activities section to authorize."
        ))
        assertNull(SpendMessageParser.parse(
            "You have successfully changed the daily limit on your card ending with ****6520. " +
                    "Kindly contact +971 4 424 4411 if you haven't initiated this request."
        ))
    }

    @Test
    fun parsesShortBankAppAlertsOnlyFromBankApps() {
        val alerts = listOf(
            "AED 45.00 spent at CARREFOUR MOE" to "45.00",
            "Purchase of AED 95.00 at VOX CINEMAS" to "95.00",
            "You paid AED 12.50 to UNION COOP" to "12.50",
            "Transaction of AED 18.00 at TALABAT approved" to "18.00",
        )

        alerts.forEach { (text, expected) ->
            assertNull(text, SpendMessageParser.parse(text, title = "Transaction alert"))
            assertEquals(text, amount(expected), SpendMessageParser.parse(text, isBankSource = true)!!.amount)
        }

        listOf(
            "AED 5,000.00 has been credited to your account",
            "Make a purchase of AED 200 with your card and win a trip to Paris",
            "Get 10% cashback on purchases above AED 100",
            "Your salary of AED 12,000.00 is here",
            "Your statement balance of AED 1,240.00 is due on 15 Nov",
        ).forEach { text ->
            assertNull(text, SpendMessageParser.parse(text, isBankSource = true))
        }
    }

    @Test
    fun parsesIndianDebitWithBalanceAfterIt() {
        val spend = SpendMessageParser.parse(
            "Rs.1,250.00 debited from A/c XX5678 on 31-10-26 to VPA swiggy@icici. " +
                    "UPI Ref 430512345678. Avl Bal Rs 20,431.10"
        )

        assertNotNull(spend)
        assertEquals(amount("1250.00"), spend!!.amount)
        assertNull(spend.currency)
        assertEquals("5678", spend.cardLast4)
        assertEquals("430512345678", spend.referenceId)
        assertEquals("swiggy", spend.merchant)
    }

    @Test
    fun skipsBalanceThatComesBeforeTheSpend() {
        val spend = SpendMessageParser.parse(
            "Avl Bal: INR 9,000.00. INR 250.00 spent on card xx4321 at STARBUCKS"
        )

        assertEquals(amount("250.00"), spend!!.amount)
        assertEquals("INR", spend.currency)
    }

    @Test
    fun parsesEuropeanFormat() {
        val spend = SpendMessageParser.parse(
            "Kartenzahlung: 1.234,56 EUR paid with card ****9876 at MediaMarkt"
        )

        assertEquals(amount("1234.56"), spend!!.amount)
        assertEquals("EUR", spend.currency)
        assertEquals("9876", spend.cardLast4)
    }

    @Test
    fun parsesGoogleWalletNotification() {
        val spend = SpendMessageParser.parse(
            text = "$4.75 with Visa •••• 1234",
            title = "Blue Bottle Coffee",
            isWalletSource = true,
        )

        assertEquals(amount("4.75"), spend!!.amount)
        assertNull(spend.currency)
        assertEquals("1234", spend.cardLast4)
        assertEquals("Blue Bottle Coffee", spend.merchant)
    }

    @Test
    fun ignoresWalletNotificationWithoutPayment() {
        assertNull(
            SpendMessageParser.parse(
                text = "Your boarding pass is ready",
                title = "Emirates",
                isWalletSource = true,
            )
        )
    }

    @Test
    fun ignoresCredits() {
        assertNull(SpendMessageParser.parse("INR 5,000.00 credited to your A/c XX1234 by NEFT"))
        assertNull(SpendMessageParser.parse("Payment of AED 1,200 received for your card ending 1234. Thank you"))
    }

    @Test
    fun ignoresRefundsAndReversals() {
        assertNull(SpendMessageParser.parse("Refund of Rs 499 for your purchase at AMAZON processed to card XX1234"))
        assertNull(SpendMessageParser.parse("Txn of USD 20.00 on card ending 1234 has been reversed"))
    }

    @Test
    fun ignoresOtpAndDeclined() {
        assertNull(SpendMessageParser.parse("OTP 123456 for txn of INR 2,000.00 at AMAZON on card XX1234"))
        assertNull(SpendMessageParser.parse("Your card ending 1234 was declined for AED 99.00 at NOON"))
    }

    @Test
    fun ignoresReminders() {
        assertNull(SpendMessageParser.parse("Rs 999 will be debited from your a/c XX1234 for Netflix on 05-11"))
        assertNull(SpendMessageParser.parse("Card XX1234 statement: total amount due AED 3,450.00, min due AED 172.50"))
    }

    @Test
    fun ignoresPromos() {
        assertNull(
            SpendMessageParser.parse("Get Rs 500 off on purchase above Rs 2000 with your bank card. Use code SAVE500. T&C apply")
        )
    }

    @Test
    fun ignoresAtmWithdrawal() {
        assertNull(SpendMessageParser.parse("INR 2,000.00 withdrawn at ATM from A/c XX1234 on 31-10"))
    }

    @Test
    fun ignoresChatWithoutBankContext() {
        assertNull(SpendMessageParser.parse("I paid $20 for lunch, send me your half"))
    }

    @Test
    fun quickCheckLetsOnlyAmountsThrough() {
        assertEquals(true, SpendMessageParser.mightBeSpend("$4.75 with Visa •••• 1234"))
        assertEquals(true, SpendMessageParser.mightBeSpend("Purchase of AED 45.50 at CARREFOUR"))
        assertEquals(false, SpendMessageParser.mightBeSpend("3 new messages"))
        assertEquals(false, SpendMessageParser.mightBeSpend("Your ride arrives in 5 min"))
        assertEquals(false, SpendMessageParser.mightBeSpend("Downloading update"))
    }

    @Test
    fun parsesNumbers() {
        assertEquals(amount("1234.56"), SpendMessageParser.parseNumber("1,234.56"))
        assertEquals(amount("123456.00"), SpendMessageParser.parseNumber("1,23,456.00"))
        assertEquals(amount("1234.56"), SpendMessageParser.parseNumber("1.234,56"))
        assertEquals(amount("12.50"), SpendMessageParser.parseNumber("12,50"))
        assertEquals(amount("1234.00"), SpendMessageParser.parseNumber("1,234"))
        assertEquals(amount("1.50"), SpendMessageParser.parseNumber("1.500"))
    }
}

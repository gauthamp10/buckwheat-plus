package com.danilkinkin.buckwheat.autoImport

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

data class ParsedSpend(
    val amount: BigDecimal,
    // ISO 4217 code, null when the message only has an ambiguous symbol like "$"
    val currency: String?,
    val merchant: String?,
    val cardLast4: String?,
    val referenceId: String?,
)

object SpendMessageParser {
    // X-codes are funds and metals ("XXX", "XAU"), they show up in masked card numbers
    private val ISO_CURRENCIES: Set<String> = Currency.getAvailableCurrencies()
        .map { it.currencyCode }
        .filterNot { it.startsWith("X") }
        .toSet()

    // Symbol or local abbreviation -> ISO code (null when the symbol is shared by several currencies)
    private val CURRENCY_ALIASES: Map<String, String?> = mapOf(
        "₹" to "INR",
        "RS" to null,
        "€" to "EUR",
        "£" to "GBP",
        "₽" to "RUB",
        "РУБ" to "RUB",
        "₴" to "UAH",
        "ГРН" to "UAH",
        "₺" to "TRY",
        "₩" to "KRW",
        "₦" to "NGN",
        "₱" to "PHP",
        "฿" to "THB",
        "₫" to "VND",
        "₪" to "ILS",
        "DHS" to "AED",
        "DH" to "AED",
        "US$" to "USD",
        "S$" to "SGD",
        "A$" to "AUD",
        "C$" to "CAD",
        "HK$" to "HKD",
        "NZ$" to "NZD",
        "R$" to "BRL",
        "RM" to "MYR",
        "ZŁ" to "PLN",
        "KČ" to "CZK",
        "$" to null,
        "¥" to null,
    )

    private const val NUMBER = """\d+(?:[,.'\u00A0\u202F]\d+)*"""

    // Android's ICU regex has no inline unicode-case flag, so non-latin cases are listed explicitly
    private val ALIAS_PATTERN = CURRENCY_ALIASES.keys
        .flatMap { listOf(it, it.lowercase(Locale.ROOT)) }
        .distinct()
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }

    private val CURRENCY_PATTERN = """(?:(?i:$ALIAS_PATTERN)\.?|[A-Z]{3})"""

    private val AMOUNT_PREFIX_REGEX = Regex(
        """(?<![\p{L}\d])($CURRENCY_PATTERN)\s?[:.]?\s?($NUMBER)(?!\d)"""
    )
    private val AMOUNT_SUFFIX_REGEX = Regex(
        """(?<![\d.,])($NUMBER)\s?($CURRENCY_PATTERN)(?![\p{L}])"""
    )

    private val NOT_A_SPEND_AMOUNT_CONTEXT = Regex(
        """\b(bal|balance|limit|lmt|avl|available|outstanding|due|o/s|cashback|reward)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val HARD_NEGATIVE = Regex(
        listOf(
            """\botp\b""",
            """one[\s-]?time\s+pass(word|code)""",
            """verification\s+code""",
            """\bdeclined\b""",
            """\bfailed\b""",
            """\bunsuccessful\b""",
            """\bnot\s+successful\b""",
            """\brejected\b""",
            """\binsufficient\b""",
            """\b(will|to)\s+be\s+(debited|charged|deducted)\b""",
            """\bis\s+due\b""",
            """\bdue\s+(date|on|by)\b""",
            """\bmin(imum)?\.?\s+(amount\s+)?due\b""",
            """\brefund(ed)?\b""",
            """\breversed\b""",
            """\breversal\b""",
            """\brequested\b""",
            """\bpayment\b[^\n]{0,60}?\breceived\b""",
            """\bthank\s+you\s+for\s+(your\s+)?payment\b""",
            """\bcollect\s+request\b""",
            """\bcoupon\b""",
            """\bpromo\s*code\b""",
            """\buse\s+code\b""",
            """\bt\s*&\s*c\b""",
            """\bvalid\s+(till|until|upto)\b""",
            """\bup\s?to\b""",
            """\bpre-?approved\b""",
            // Bank apps push offers too ("Make a purchase of AED 200 and win")
            """\b(offer|win|chance|minimum\s+spend)\b""",
            """\d+\s?%\s?(off|cashback|discount)\b""",
            """\bauthori[sz]ation\s+hold\b""",
        ).joinToString("|"),
        RegexOption.IGNORE_CASE,
    )

    private val STRONG_DEBIT = Regex(
        """\b(debited|spent|purchased?|paid|charged|deducted|withdrawn|sent|used\s+(at|for|on)|thank\s+you\s+for\s+using|payment\s+(of|made|to)|debit\s+of)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val WEAK_DEBIT = Regex(
        """\b(debit|txn|transaction|pos|purchase)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val INCOME = Regex(
        """\b(credited|received|deposited|cashback)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val BANK_CONTEXT = Regex(
        """\b(card|a/c|acct|account|upi|vpa|ending|bank|ref|txn|pos|merchant|avl|bal|imps|neft)\b|[x*•·]{2,}\s*-?\s*\d{3,4}""",
        RegexOption.IGNORE_CASE,
    )

    private val CARD_MARKER = Regex(
        """\b(card|visa|mastercard|master\s+card|amex|american\s+express|rupay|maestro|discover|mir)\b|[x*•·]{2,}\s*-?\s*\d{3,4}""",
        RegexOption.IGNORE_CASE,
    )

    private val ATM_WITHDRAWAL = Regex(
        """\batm\b.*\bwithdraw|\bwithdraw\w*\b.*\batm\b|\bcash\s+withdrawal\b""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val CARD_LAST_DIGITS = Regex(
        """(?:ending(?:\s+(?:with|in))?|end\s+no\.?|[x*•·]{2,}[\s-]*)\s*[:#]?\s*(\d{4})(?!\d)""",
        RegexOption.IGNORE_CASE,
    )

    private val REFERENCE = Regex(
        """\b(?:upi\s*ref(?:\s*no)?|ref(?:erence)?(?:\s*(?:no|number|id))?|txn\s*(?:id|no)|transaction\s*(?:id|no)|utr|rrn|approval\s*code|auth(?:ori[sz]ation)?\s*code)\b\.?\s*[:#-]?\s*([A-Z0-9]{6,})""",
        RegexOption.IGNORE_CASE,
    )

    private const val MERCHANT_END =
        """(?=\s+(?:on|using|with|via|for|dated|ref|txn|avl|bal|from|thru|through|is|has|card|a/c|at)\b|\s+\d{1,2}[/.-]\d{1,2}|\s*[.;,!]\s|\s*[.;,!]?$|\s*\(|\n)"""

    private val MERCHANT_PATTERNS = listOf(
        Regex("""\b(?:merchant|info|payee)\s*[:-]\s*(.{2,40}?)$MERCHANT_END""", RegexOption.IGNORE_CASE),
        Regex("""\bat\s+(.{2,40}?)$MERCHANT_END""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:to|towards)\s+(?:vpa\s+)?(.{2,40}?)$MERCHANT_END""", RegexOption.IGNORE_CASE),
    )

    private val NOT_A_MERCHANT = Regex(
        """^(your|you|the|a/c|acct|account|card|rs\.?|inr|bank|be|your\s+.*|\d+[\d\s:/.-]*)$""",
        RegexOption.IGNORE_CASE,
    )

    // Runs for every notification on the phone, so it must stay far cheaper than parse()
    fun mightBeSpend(text: String): Boolean {
        if (text.length < 4 || text.none { it.isDigit() }) return false

        return AMOUNT_PREFIX_REGEX.containsMatchIn(text) || AMOUNT_SUFFIX_REGEX.containsMatchIn(text)
    }

    /**
     * @param isWalletSource messages from Google Wallet and similar apps carry only the amount
     * and card, without any "debited"/"spent" wording.
     * @param isBankSource the message comes from an app known to be a bank, whose short alerts
     * often skip any card or account wording ("AED 45.00 spent at Carrefour").
     */
    fun parse(
        text: String,
        title: String? = null,
        isWalletSource: Boolean = false,
        isBankSource: Boolean = false,
    ): ParsedSpend? {
        val fullText = listOfNotNull(title?.takeIf { it.isNotBlank() }, text)
            .joinToString("\n")
            .trim()

        if (fullText.isEmpty()) return null
        if (HARD_NEGATIVE.containsMatchIn(fullText)) return null
        if (ATM_WITHDRAWAL.containsMatchIn(fullText)) return null

        val hasStrongDebit = STRONG_DEBIT.containsMatchIn(fullText)
        val hasWeakDebit = WEAK_DEBIT.containsMatchIn(fullText)
        val hasIncome = INCOME.containsMatchIn(fullText)

        val hasDebitWording = hasStrongDebit || (hasWeakDebit && !hasIncome)

        val looksLikeSpend = when {
            isWalletSource -> {
                (hasStrongDebit || CARD_MARKER.containsMatchIn(fullText)) && !(hasIncome && !hasStrongDebit)
            }
            isBankSource -> hasDebitWording
            else -> BANK_CONTEXT.containsMatchIn(fullText) && hasDebitWording
        }

        if (!looksLikeSpend) return null

        val (amount, currency) = findSpendAmount(fullText) ?: return null

        return ParsedSpend(
            amount = amount,
            currency = currency,
            merchant = findMerchant(text, title, isWalletSource),
            cardLast4 = CARD_LAST_DIGITS.find(fullText)?.groupValues?.get(1),
            referenceId = REFERENCE.findAll(fullText)
                .map { it.groupValues[1] }
                .firstOrNull { ref -> ref.any { it.isDigit() } }
                ?.uppercase(Locale.ROOT),
        )
    }

    private data class AmountMatch(val start: Int, val end: Int, val amount: BigDecimal, val currency: String?)

    private fun findSpendAmount(text: String): Pair<BigDecimal, String?>? {
        val matches = mutableListOf<AmountMatch>()

        AMOUNT_PREFIX_REGEX.findAll(text).forEach { match ->
            val currency = resolveCurrency(match.groupValues[1]) ?: return@forEach
            val amount = parseNumber(match.groupValues[2]) ?: return@forEach
            matches += AmountMatch(match.range.first, match.range.last + 1, amount, currency.code)
        }

        AMOUNT_SUFFIX_REGEX.findAll(text).forEach { match ->
            val currency = resolveCurrency(match.groupValues[2]) ?: return@forEach
            val amount = parseNumber(match.groupValues[1]) ?: return@forEach
            if (matches.any { match.range.first < it.end && it.start < match.range.last + 1 }) return@forEach
            matches += AmountMatch(match.range.first, match.range.last + 1, amount, currency.code)
        }

        matches.sortBy { it.start }

        var previousEnd = 0
        for (match in matches) {
            val context = text.substring(maxOf(previousEnd, match.start - 30), match.start)
            previousEnd = match.end

            if (NOT_A_SPEND_AMOUNT_CONTEXT.containsMatchIn(context)) continue
            if (match.amount <= BigDecimal.ZERO) continue

            return match.amount to match.currency
        }

        return null
    }

    private class ResolvedCurrency(val code: String?)

    private fun resolveCurrency(raw: String): ResolvedCurrency? {
        val token = raw.trimEnd('.').uppercase(Locale.ROOT)

        if (CURRENCY_ALIASES.containsKey(token)) return ResolvedCurrency(CURRENCY_ALIASES[token])
        if (raw.length == 3 && raw.all { it in 'A'..'Z' } && raw in ISO_CURRENCIES) {
            return ResolvedCurrency(raw)
        }

        return null
    }

    fun parseNumber(raw: String): BigDecimal? {
        val value = raw.replace(Regex("""[\u00A0\u202F' ]"""), "")
        val dots = value.count { it == '.' }
        val commas = value.count { it == ',' }
        val lastDot = value.lastIndexOf('.')
        val lastComma = value.lastIndexOf(',')

        val decimalSeparator: Char? = when {
            dots > 0 && commas > 0 -> if (lastDot > lastComma) '.' else ','
            dots == 1 -> '.'
            dots > 1 -> null
            commas == 1 && value.length - lastComma - 1 in 1..2 -> ','
            else -> null
        }

        val normalized = buildString {
            value.forEachIndexed { index, char ->
                when {
                    char.isDigit() -> append(char)
                    decimalSeparator != null
                            && char == decimalSeparator
                            && index == value.lastIndexOf(decimalSeparator) -> append('.')
                }
            }
        }

        return normalized.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
    }

    private fun findMerchant(text: String, title: String?, isWalletSource: Boolean): String? {
        if (isWalletSource && !title.isNullOrBlank()) {
            val cleanTitle = cleanMerchant(title)
            if (cleanTitle != null) return cleanTitle
        }

        for (pattern in MERCHANT_PATTERNS) {
            pattern.findAll(text).forEach { match ->
                cleanMerchant(match.groupValues[1])?.let { return it }
            }
        }

        return null
    }

    private fun cleanMerchant(raw: String): String? {
        val value = raw
            .replace(Regex(""",(?=\S)"""), ", ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '.', ',', ';', ':', '-', '*', '\'', '"')

        if (value.length < 2) return null
        if (NOT_A_MERCHANT.matches(value)) return null
        if (AMOUNT_PREFIX_REGEX.containsMatchIn(value)) return null
        if (value.contains('@')) return value.substringBefore('@').takeIf { it.length >= 2 }

        val isAllCaps = value.any { it.isLetter() } && value.none { it.isLowerCase() }

        return if (isAllCaps) {
            value.lowercase(Locale.ROOT).split(' ').joinToString(" ") { word ->
                word.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
        } else {
            value
        }
    }
}

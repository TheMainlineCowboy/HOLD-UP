package com.holdup.app

enum class DecisionRisk(val label: String) {
    HIGH("High caution"),
    MEDIUM("Review carefully"),
    LOW("No urgent warning found")
}

data class DecisionResult(
    val category: String,
    val risk: DecisionRisk,
    val headline: String,
    val explanation: String,
    val evidence: List<String>,
    val primaryAction: String,
    val secondaryAction: String
)

object DecisionAnalyzer {
    private val linkPattern = Regex("https?://|www\\.", RegexOption.IGNORE_CASE)

    fun analyze(rawText: String): DecisionResult {
        val text = rawText.lowercase()
        val evidence = mutableListOf<String>()
        fun matches(signals: List<String>) = signals.filter(text::contains)

        val credentials = matches(listOf("password", "passcode", "verification code", "security code", "one-time code", "otp"))
        val payments = matches(listOf("gift card", "wire transfer", "crypto", "bitcoin", "zelle", "cash app", "venmo", "send money"))
        val pressure = matches(listOf("act now", "urgent", "immediately", "final notice", "account suspended", "within 24 hours", "do not tell"))
        val subscriptions = matches(listOf("subscription", "free trial", "renewal", "renews", "auto-renew", "auto renew", "automatically renew", "recurring charge", "membership", "cancel anytime"))
        val priceChanges = matches(listOf("price change", "price increase", "new price", "rate increase", "will increase", "changing from", "updated pricing"))
        val cancellationDeadlines = matches(listOf("cancel by", "cancel before", "cancellation deadline", "last day to cancel", "avoid being charged", "before your trial ends"))
        val bills = matches(listOf("amount due", "payment due", "invoice", "statement balance", "past due", "due date"))
        val appointments = matches(listOf("appointment", "reservation", "scheduled for", "check-in", "meeting"))
        val hasLink = linkPattern.containsMatchIn(rawText)

        if (credentials.isNotEmpty()) evidence += "Requests a password or verification code"
        if (payments.isNotEmpty()) evidence += "Requests a hard-to-reverse payment method"
        if (pressure.isNotEmpty()) evidence += "Uses urgency or pressure language"
        if (hasLink && (credentials.isNotEmpty() || pressure.isNotEmpty())) evidence += "Includes a link alongside a sensitive request"
        if (priceChanges.isNotEmpty()) evidence += "Mentions a subscription price or rate change"
        if (cancellationDeadlines.isNotEmpty()) evidence += "Mentions a deadline to cancel or avoid a charge"

        return when {
            credentials.isNotEmpty() || payments.isNotEmpty() -> DecisionResult("Possible scam or account takeover", DecisionRisk.HIGH, "Pause before responding", "Do not use the supplied link or share a code. Contact the organization through an independently verified channel.", evidence, "Verify safely", "Save evidence")
            pressure.isNotEmpty() && hasLink -> DecisionResult("Suspicious request", DecisionRisk.MEDIUM, "Verify the sender independently", "Urgency plus a link can push a rushed decision. Open the official service directly instead.", evidence, "Verify safely", "Ask someone trusted")
            subscriptions.isNotEmpty() || priceChanges.isNotEmpty() || cancellationDeadlines.isNotEmpty() -> {
                val draft = SubscriptionDraftParser.parse(rawText)
                draft.previousAmountDisplay()?.let { previous ->
                    draft.amountDisplay()?.let { current -> evidence += "Price appears to change from $previous to $current" }
                }
                draft.amountDisplay()?.let { amount ->
                    val cadence = draft.cadence?.displayName?.lowercase()?.let { " $it" }.orEmpty()
                    evidence += "Detected charge: $amount$cadence"
                }
                draft.annualizedDisplay()?.let { evidence += "Estimated recurring total: $it" }
                draft.dateEvidence()?.let(evidence::add)

                val headline = when {
                    draft.dateIntent == SubscriptionDateIntent.CANCELLATION_DEADLINE -> "Review the cancellation deadline"
                    priceChanges.isNotEmpty() -> "Review the new subscription price"
                    else -> "Review the renewal terms"
                }
                DecisionResult(
                    "Subscription or renewal",
                    DecisionRisk.LOW,
                    headline,
                    "Confirm the next charge, billing cadence, price, and cancellation deadline before deciding what to do.",
                    evidence.distinct().ifEmpty { listOf("Mentions recurring service or renewal language") },
                    "Track renewal",
                    "Find cancellation path"
                )
            }
            bills.isNotEmpty() -> DecisionResult("Bill or payment deadline", DecisionRisk.LOW, "Confirm the amount and due date", "Compare this notice with the official account before paying, then create a reminder using the confirmed date.", listOf("Contains bill, balance, invoice, or due-date language"), "Set reminder", "Mark as reviewed")
            appointments.isNotEmpty() -> DecisionResult("Appointment or scheduled event", DecisionRisk.LOW, "Check the date, time, and location", "Confirm the details before adding the event to your calendar.", listOf("Contains appointment or scheduling language"), "Add to calendar", "Set reminder")
            else -> DecisionResult("General information", DecisionRisk.LOW, "No clear urgent action detected", "Read the original carefully and verify any sender, payment, link, or deadline before acting.", emptyList(), "Mark as reviewed", "Save for later")
        }
    }
}

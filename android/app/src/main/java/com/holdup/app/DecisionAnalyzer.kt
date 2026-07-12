package com.holdup.app

enum class RiskLevel(val label: String) {
    HIGH("High caution"),
    MEDIUM("Review carefully"),
    LOW("No urgent warning found")
}

data class DecisionAnalysis(
    val category: String,
    val risk: RiskLevel,
    val headline: String,
    val explanation: String,
    val evidence: List<String>,
    val primaryAction: String,
    val secondaryAction: String
)

object DecisionAnalyzer {
    private val linkPattern = Regex("https?://|www\\.", RegexOption.IGNORE_CASE)

    fun analyze(rawText: String): DecisionAnalysis {
        val text = rawText.lowercase()
        val evidence = mutableListOf<String>()
        fun matches(signals: List<String>) = signals.filter(text::contains)

        val credentials = matches(
            listOf("password", "passcode", "verification code", "security code", "one-time code", "otp")
        )
        val payments = matches(
            listOf("gift card", "wire transfer", "crypto", "bitcoin", "zelle", "cash app", "venmo", "send money")
        )
        val pressure = matches(
            listOf("act now", "urgent", "immediately", "final notice", "account suspended", "within 24 hours", "do not tell")
        )
        val subscriptions = matches(
            listOf("subscription", "free trial", "renewal", "renews", "auto-renew", "cancel anytime")
        )
        val bills = matches(
            listOf("amount due", "payment due", "invoice", "statement balance", "past due", "due date")
        )
        val appointments = matches(
            listOf("appointment", "reservation", "scheduled for", "check-in", "meeting")
        )
        val hasLink = linkPattern.containsMatchIn(rawText)

        if (credentials.isNotEmpty()) evidence += "Requests a password or verification code"
        if (payments.isNotEmpty()) evidence += "Requests a hard-to-reverse payment method"
        if (pressure.isNotEmpty()) evidence += "Uses urgency or pressure language"
        if (hasLink && (credentials.isNotEmpty() || pressure.isNotEmpty())) {
            evidence += "Includes a link alongside a sensitive request"
        }

        return when {
            credentials.isNotEmpty() || payments.isNotEmpty() -> DecisionAnalysis(
                category = "Possible scam or account takeover",
                risk = RiskLevel.HIGH,
                headline = "Pause before responding",
                explanation = "Do not use the supplied link or share a code. Contact the organization through an independently verified channel.",
                evidence = evidence,
                primaryAction = "Verify safely",
                secondaryAction = "Save evidence"
            )

            pressure.isNotEmpty() && hasLink -> DecisionAnalysis(
                category = "Suspicious request",
                risk = RiskLevel.MEDIUM,
                headline = "Verify the sender independently",
                explanation = "Urgency plus a link can push a rushed decision. Open the official service directly instead.",
                evidence = evidence,
                primaryAction = "Verify safely",
                secondaryAction = "Ask someone trusted"
            )

            subscriptions.isNotEmpty() -> DecisionAnalysis(
                category = "Subscription or renewal",
                risk = RiskLevel.LOW,
                headline = "Review the renewal terms",
                explanation = "Confirm the next charge, billing cadence, and cancellation deadline before deciding what to do.",
                evidence = listOf("Mentions recurring service or renewal language"),
                primaryAction = "Track renewal",
                secondaryAction = "Find cancellation path"
            )

            bills.isNotEmpty() -> DecisionAnalysis(
                category = "Bill or payment deadline",
                risk = RiskLevel.LOW,
                headline = "Confirm the amount and due date",
                explanation = "Compare this notice with the official account before paying, then create a reminder using the confirmed date.",
                evidence = listOf("Contains bill, balance, invoice, or due-date language"),
                primaryAction = "Set reminder",
                secondaryAction = "Mark as reviewed"
            )

            appointments.isNotEmpty() -> DecisionAnalysis(
                category = "Appointment or scheduled event",
                risk = RiskLevel.LOW,
                headline = "Check the date, time, and location",
                explanation = "Confirm the details before adding the event to your calendar.",
                evidence = listOf("Contains appointment or scheduling language"),
                primaryAction = "Add to calendar",
                secondaryAction = "Set reminder"
            )

            else -> DecisionAnalysis(
                category = "General information",
                risk = RiskLevel.LOW,
                headline = "No clear urgent action detected",
                explanation = "Read the original carefully and verify any sender, payment, link, or deadline before acting.",
                evidence = emptyList(),
                primaryAction = "Mark as reviewed",
                secondaryAction = "Save for later"
            )
        }
    }
}

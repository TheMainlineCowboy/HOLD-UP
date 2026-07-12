# HOLD UP Product Requirements

## Product definition

HOLD UP is a privacy-first decision and life-admin firewall. It converts screenshots, shared content, and optional email data into structured action cards.

## Primary user problems

- Important dates disappear inside screenshots, texts, PDFs, and email.
- Bills and subscriptions repeat, but users must manually remember them.
- Scams and manipulative messages create urgency before users can think.
- Cancellation and renewal details are difficult to find.
- Existing tools are fragmented across calendars, email, security apps, and subscription trackers.

## Core action-card model

Every analysis should answer:

1. What is this?
2. What does it want the user to do?
3. Is money, risk, or a deadline involved?
4. What facts were extracted?
5. What is the safest useful next action?

Supported card types:

- Scam or manipulation warning
- Bill due
- Recurring bill
- Appointment or reservation
- Subscription or renewal
- Free-trial ending
- Price increase
- Cancellation confirmation
- Return deadline
- Warranty expiration
- Payment confirmation
- Recovery checklist
- Information only

## Required product behaviors

### Screenshot and share intake

- Receive shared images, screenshots, links, text, and PDFs through Android.
- Run OCR and classify the content.
- Show extracted facts before any calendar, reminder, or recurring-series change is saved.
- Never silently monitor the screen or intercept screenshots from other apps.

### Recurring bill memory

After the user confirms a recurring bill, store:

- Merchant
- Account label, never full account credentials
- Amount and amount history when available
- Due day
- Preferred payment day
- Cadence
- Reminder offsets
- Autopay state
- Source and confidence

Generate future monthly occurrences and support:

- Short-month rules
- Weekend rules
- Amount or due-date changes
- Mark paid
- Skip one occurrence
- Edit future occurrences
- End the series

### Subscription dashboard

With explicit consent, identify recurring charges, trials, renewals, receipts, and price changes. Show:

- Merchant
- Last confirmed amount
- Estimated monthly and annual cost
- Cadence
- Next expected charge
- Confidence
- Source email
- Official account-management or cancellation route
- Cancellation status and follow-up confirmation

### Trust and privacy

- Prefer local processing.
- Explain every permission before requesting it.
- Store only what is needed for the user-facing feature.
- Never train on private content.
- Provide export, deletion, disconnect, and revoke controls.
- Never label content definitively safe based only on missing warning signs.

## Quality bar

- Premium, restrained visual identity
- Original iconography and illustrations
- Android-first responsive layouts
- Excellent accessibility and touch targets
- Clear empty, loading, success, warning, and error states
- Fast interaction with minimal cognitive load
- Action cards instead of long AI prose
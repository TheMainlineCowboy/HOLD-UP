# HOLD UP Architecture

## Product surfaces

### Web prototype

The mobile-first web app validates the information architecture, action cards, recurring-bill model, subscription dashboard, accessibility, and visual system. It can support:

- Manual image upload
- Text and link input
- Local storage
- Calendar file generation
- Reminder planning inside the app
- Offline use
- Installable PWA behavior

### Native Android app

Native Android is required for the intended production experience:

- Android Sharesheet intake for images, text, links, and PDFs
- On-device OCR through ML Kit
- Reliable local notifications
- Calendar insert intents
- Quick Settings shortcut or share shortcut
- Secure local database and encrypted preferences
- Native file and URI handling
- Future Gmail OAuth and local message processing

The app must not use invasive Accessibility Service or screen-capture monitoring to observe other applications.

## Proposed modules

- `intake` — shared content, file import, pasted text, and links
- `ocr` — local text recognition and document cleanup
- `classification` — bill, appointment, subscription, risk, renewal, cancellation, or information-only
- `extraction` — merchant, amount, dates, cadence, contact details, URLs, and requested action
- `decision` — risk signals, reversibility, verification steps, and recommended actions
- `obligations` — bills, reminders, recurring rules, payment state, and exceptions
- `subscriptions` — merchants, charges, cadence, annualized totals, renewal state, and cancellation routes
- `calendar` — user-confirmed event creation and recurring schedules
- `notifications` — due-date and renewal reminders
- `privacy` — redaction, deletion, export, permissions, retention, and consent
- `trusted-circle` — optional structured sharing with selected contacts

## Initial data models

### AnalysisItem

- id
- sourceType
- sourceReference
- createdAt
- extractedText
- category
- confidence
- requestedAction
- riskSignals
- reversibleState
- recommendedActions
- retentionState

### RecurringObligation

- id
- merchant
- label
- cadence
- dueDay
- preferredPayDay
- typicalAmount
- amountHistory
- reminderOffsets
- autopayState
- weekendRule
- shortMonthRule
- nextOccurrence
- active
- sourceConfidence

### Subscription

- id
- merchant
- lastAmount
- cadence
- nextExpectedCharge
- annualizedCost
- confidence
- sourceMessageReference
- managementUrl
- cancellationInstructions
- cancellationState

## Privacy defaults

- Analyze locally whenever technically practical.
- Store derived structured facts rather than full source content by default.
- Require explicit confirmation before creating calendar events or recurring series.
- Require explicit opt-in before Gmail access.
- Provide one-tap deletion for source content and extracted records.
- Keep analytics disabled until a privacy-preserving product policy is approved.
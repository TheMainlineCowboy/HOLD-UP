# HOLD UP Android

This directory is reserved for the native Android application.

## Production requirements

- Kotlin
- Jetpack Compose
- Android Sharesheet intake for `image/*`, `text/plain`, links, and PDFs
- ML Kit on-device text recognition
- Room for recurring obligations, subscription records, analysis history, and user preferences
- WorkManager and notifications for reminders
- Calendar insert intents with user confirmation
- Encrypted local secrets and OAuth tokens
- Explicit Gmail opt-in with restricted-scope compliance
- No Accessibility Service, background screen capture, or hidden screenshot monitoring

## Initial implementation sequence

1. Create the Compose application shell and design system.
2. Add share-intent intake and secure URI handling.
3. Add local OCR and structured extraction.
4. Build action cards and confirmation flows.
5. Add recurring bill storage and notification scheduling.
6. Add calendar insertion.
7. Add optional Gmail connection after OAuth and privacy requirements are prepared.

The web prototype in `../web` is the current design and interaction test surface. Native implementation should preserve its low-cognitive-load action-card model while improving Android integration.
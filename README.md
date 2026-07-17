# Pocket Ledger

Private, offline Android budgeting and bill-reminder app.

## Included in version 1

- Multiple cash, checking, savings, and credit accounts
- Income and expense transactions with categories
- Recurrence labels for weekly and monthly transactions
- Monthly category limits and spending progress
- One-time or monthly bills
- Custom reminder lead time
- Mark-paid action and payment history
- Upcoming and overdue bill status
- Notifications restored after the phone restarts
- Local PDF bank-statement import with transaction review
- Duplicate transaction protection during repeated imports
- Automatic category suggestions and likely recurring-bill detection
- Delete controls for accounts, transactions, budgets, bills, and payment records
- Material 3 light and dark themes
- Local-only SQLite storage; no internet permission, login, analytics, or bank connection

## Build and install

1. Install the current stable Android Studio.
2. Open the `PocketLedger` folder.
3. Let Android Studio download and sync the Gradle dependencies.
4. Connect the Galaxy S23 Ultra by USB and enable **Developer options > USB debugging**.
5. Select the phone in Android Studio and click **Run**.
6. Allow notifications when prompted. If Android asks for **Alarms & reminders** access, allow it for precise bill timing.

To create an installable file, use **Build > Build App Bundles or APKs > Build APKs**. The debug APK appears under `app/build/outputs/apk/debug/`.

### Build without Android Studio

Push this folder to GitHub. The included workflow builds automatically. Open the repository's **Actions** tab, select the completed **Build Android APK** run, and download the `PocketLedger-debug-apk` artifact.

### PDF import limitations

The importer handles text-based statements with common date/description/amount rows. Scanned image-only PDFs require OCR and are intentionally rejected. Always review detected credits, debits, categories, and recurring bills before importing.

## Privacy note

Android app data is removed when the app is uninstalled. Version 1 intentionally has no backup or export because local-only storage was selected.

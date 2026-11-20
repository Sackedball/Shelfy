# Shelfy

An offline Android app that helps kirana (neighbourhood grocery) shop owners sell stock before it expires.

Shelfy tracks every batch of stock with its expiry date and sells it first-expiry-first-out (FEFO). It flags lots at risk of expiring and records sales, returns and stock adjustments in a local ledger.

## Features

- Inventory by batch, with expiry tiers and predicted loss
- Barcode scanning and quick-add items
- Cart and checkout with cash or UPI QR payments
- Sales history, returns and shop reports
- Expiry reminders and encrypted local backups
- English, Hindi and Marathi

## Tech stack

Kotlin · Jetpack Compose · Room · Hilt · Gradle (Kotlin DSL)

Everything runs on the device. There is no backend and no account.

## Build

```
./gradlew assembleDebug
./gradlew test
```

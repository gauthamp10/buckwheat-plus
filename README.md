# Buckwheat+

[<img width="100%" height="auto" src="/screenshots/en/github-screenshot.png" alt="Buckwheat screenshots" />](https://buckwheat.app/)

**Buckwheat+ is an unofficial fork of [Buckwheat](https://github.com/danilkinkin/buckwheat) by [Danil Zakhvatkin](https://github.com/danilkinkin).**
All the credit for the app itself, its idea, design and budgeting logic goes to him and the
[Buckwheat contributors](https://github.com/danilkinkin/buckwheat/graphs/contributors).
This fork is a convenience update on top of it: it stops you from typing in every card spend by reading
them from your bank, Google Wallet and SMS notifications, and adds a few things around that.

> Buckwheat+ is not affiliated with or endorsed by the original author.
> If you just want a great budgeting app, use the original:
> [Google Play](https://play.google.com/store/apps/details?id=com.danilkinkin.buckwheat) ·
> [F-Droid](https://f-droid.org/packages/com.danilkinkin.buckwheat) ·
> [GitHub releases](https://github.com/danilkinkin/buckwheat/releases)

## What Buckwheat+ adds

### Automatic spend deduction
Card spends are deducted from today's budget by themselves, a second or two after the bank or Wallet notification arrives,
with the merchant name saved as the spend's comment.

- **Reads notifications** from Google Wallet, Google Pay, Samsung Wallet, bank apps and SMS apps, in the background, even while Buckwheat+ is closed
- **Works with any bank**: it understands spend wording ("purchase of", "spent", "paid", "was used for", "thank you for using…"),
  the amount and currency, the card's last 4 digits and the merchant, instead of matching one bank's exact template
- **Learns bank apps**: after one full card alert from an app, its shorter alerts ("AED 45.00 spent at Carrefour") are trusted too.
  Emirates NBD and Mashreq are trusted from the start
- **Catches up on the first run**: when switched on, it finds the day's earlier spends from bank SMS and from notifications still in the shade
- **Handles duplicates**: one purchase reported by Wallet, the bank app and an SMS is deducted once, a spend you already typed in by hand is not
  deducted again, and a notification re-posted by its app is ignored
- **Ignores what is not a spend**: OTPs, declined and failed payments, refunds, reversals, credited money, amounts "to be debited",
  bills due, offers and cashback promos, ATM withdrawals, and the "available limit"/"balance" amounts inside real alerts
- **Respects the budget**: spends in another currency or outside the budget period are skipped, and spends that arrive before the app
  starts a new day wait and are deducted as soon as it does
- **Private**: everything is read and processed on the phone, nothing leaves it

Turn it on in *Settings → Auto-import card spends*, then allow notification access (and optionally SMS access and background work).

### Spends by card
A new card button next to Settings opens a breakdown of the current budget period by card: total and count per card,
which app reported it, and the list of merchants. Spends without card info, like the ones typed in by hand, have their own group.

### Live budget notification
An optional ongoing notification (*Settings → Budget in notifications*) shows how much is left for today, how much was spent and
how much is left in total, with a progress bar, in a compact and an expanded layout. It turns red when today's budget is overspent,
updates the moment a spend is added or deducted, refreshes at midnight and comes back after a reboot.

### Battery and memory, ready for Android 17
- Prepared for the [Android 17 memory changes](https://developer.android.com/blog/posts/prioritizing-memory-efficiency-essential-steps-for-android-17):
  releases database caches when Android asks the app to trim memory, and the notification reader drops its caches when in the background
- R8 full mode with resource shrinking, and unused libraries removed: the release APK is about 6 MB
- The notification reader does a cheap digit and amount check before parsing anything, only looks at alerting, silent and conversation
  notifications, skips ongoing and group-summary notifications and personal chat apps, and unbinds itself entirely while the feature is off
- The app's day-change check runs about every minute instead of every 5 seconds

### Tests
Unit and Robolectric tests cover the message parser (with real UAE bank formats), duplicate detection, card grouping,
and the full path from a posted notification to the budget being reduced.

## Building

Requirements: JDK 17, Android SDK 36, Gradle 8.14.

```bash
gradle :app:assembleRelease      # app/build/outputs/apk/release/app-release.apk
gradle :app:testDebugUnitTest    # run the tests
```

Buckwheat+ uses its own application id, `com.gauthamp10.buckwheatplus`, so it installs next to the original Buckwheat.
Data is not shared between the two.

## About the original Buckwheat

[**Buckwheat**](https://buckwheat.app) is an app that helps you spend money wisely.
Writing down each spending sobers the mind and gives you an idea of how much and how you can spend.
Are you going on a business trip or vacation? Buckwheat helps you save money during your trip.

If you like it, please support the original author: share Buckwheat with friends or check the options on its
[contribute page](https://buckwheat.app/contribute). Translations are done on [Crowdin](https://translate.buckwheat.app).

## License

[GNU GPL v3](LICENSE), same as the original.

- Buckwheat: Copyright 2022, [Danil Zakhvatkin](https://github.com/danilkinkin) and [contributors](https://github.com/danilkinkin/buckwheat/graphs/contributors)
- Buckwheat+: modified in 2026 by [Gautham (gauthamp10)](https://github.com/gauthamp10). The changes are listed above and in the git history

[Privacy policy](./POLICY.md) (of the original app; Buckwheat+ adds no data collection, notifications and SMS are only read on the device)

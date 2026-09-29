# Splitlee — Production Readiness Audit

_App: Splitlee (package `com.example.splitwiseauto`) · Android/Kotlin/Jetpack Compose · Room · offline-only_
_Audit date: 28 Sep 2026 · Scope: the 120 checkpoints requested_

## Verdict at a glance

Splitlee is a **solid, well-built hobby / personal app**. Its offline-first, privacy-first design is genuinely strong. It is **not ready for a public Google Play release** — mainly because of SMS-permission policy, no release signing/minification, no privacy policy, and thin automated testing. For **personal use and sharing an APK with friends**, it's close, with a couple of real bugs to close.

**Severity summary**

| Severity | Count | Meaning |
|---|---|---|
| 🔴 Blocker (for public Play release) | 8 | Cannot ship publicly until fixed |
| 🟠 High | 12 | Real risk to users / stability |
| 🟡 Medium | ~20 | Should fix before wider distribution |
| 🟢 Good / not applicable | rest | Already handled, or N/A for an offline hobby app |

**Top 5 things to do next**
1. Reproduce and fix the **fresh-install crash on Map/Insights** (reported; still no logcat — this is the one open correctness bug).
2. Ship a **release build**: `isMinifyEnabled = true` + a real signing keystore. Fixes lag and is required to publish.
3. Add a **data export/backup** (CSV) — today, uninstall = total data loss.
4. Bump **versionCode/versionName** and start committing (60 files are uncommitted right now).
5. Decide the distribution path — the **SMS permissions** make a public Play listing hard; direct APK sharing avoids that.

---

## 1–13 · Code structure, correctness, edge cases

1. **Architecture** — 🟢 Good. Compose UI + Room + per-screen ViewModels, now with a partial repository layer (`ExpenseRepository`). Clean for this size.
2. **Code quality** — 🟢 Good. Readable, heavily commented, consistent style.
3. **Code duplication** — 🟡 Medium. Rupee formatting, "expense → pending row", and category lookups are repeated across files. Consolidate the money/format helpers.
4. **Reusable components** — 🟡 Medium. Repository pattern started for Expense only; other screens still call DAOs directly. Finish or drop it for consistency.
5. **Separation of concerns** — 🟡 Medium. ViewModels do filtering/aggregation in-memory instead of via SQL; UI files are large (`HomeTabContent.kt` ~2k lines).
6. **State management** — 🟢 Good. `StateFlow` + `collectAsState`, `rememberSaveable` for scan/payment state.
7. **Lifecycle handling** — 🟢 Good. `DisposableEffect`/observers used; camera executor shut down in `onDestroy`.
8. **Memory leaks** — 🟢 Mostly good. Camera + receivers disposed. Watch the 2s status-poll loops — they stop on terminal state, verify they cancel on screen exit.
9. **Error handling** — 🟡 Medium. SMS path now wrapped in try/catch; some flows still assume success.
10. **Exception handling** — 🟢 Good. No `printStackTrace`; failures logged by type only, never crash the receiver.
11. **Input validation** — 🟢 Good. Amount parsing guards nulls/≤0; QR parsing rejects non-UPI.
12. **Null/empty-state handling** — 🟠 High. **Suspected source of the fresh-install crash** on Map/Insights with zero data — needs the logcat to confirm. Charts/bounds/marker paths reviewed and look safe, so it is likely a permission-dialog timing or Maps-init issue.
13. **Edge cases** — 🟡 Medium. Two same-amount payments in 15 min → SMS matches the earlier; merchant-QR `tr` override may be rejected by some apps. Both known and documented.

## 14–20 · Data & persistence

14. **Business-logic correctness** — 🟢 Good. Split math, "you owe / owed to you", month rollover all sound.
15. **Data model correctness** — 🟢 Good. Amounts in paise (`amountMinor: Long`) for payments — avoids float drift.
16. **Data persistence** — 🟢 Good. Room, single DB instance.
17. **Database integrity** — 🟢 Good. Foreign-key-style links by id; conditional status updates.
18. **Database migrations** — 🟠 High. Additive-only discipline is excellent (now at **v13**). BUT a v12 schema was edited mid-development after a build — anyone who installed that exact interim build must reinstall. Going forward: never edit a shipped version number.
19. **Data-loss scenarios** — 🟠 High. All data is local with **no backup/export/restore**. Uninstall, "clear data", or device loss = everything gone.
20. **Concurrency / race conditions** — 🟢 Good. `compareAndSetStatus` + a process-wide `Mutex` in `ScanPayLedger` stop the SMS matcher, UPI-app result, and timeout worker from clashing.

## 21–25 · Offline & network

21. **Offline behavior** — 🟢 Excellent. Fully offline by design; the new *99# path is the explicit no-internet payment mode.
22. **Network failure handling** — 🟢 N/A. No app network calls (only Google Maps/Location SDKs).
23. **Timeouts** — 🟢 Good. Location calls use `withTimeoutOrNull`; payment verification has a 30-min timeout worker.
24. **Retry logic** — 🟢 Good. "Check SMS again" rescan; location retries twice.
25. **API response validation** — 🟢 N/A / Good. The UPI-app response is treated as unreliable and never as proof — correct.

## 26–41 · Security & privacy

26–28. **Auth / authorization / sessions** — 🟢 N/A. Single-user local app; no accounts or tokens.
29. **Secrets/API-key protection** — 🟠 High. The **Google Maps API key** is injected via manifest placeholder — make sure it's restricted (package + SHA-1) in Google Cloud and not committed in plaintext.
30–32. **Encryption / secure storage / secure comms** — 🟡 Medium. Room DB is unencrypted (standard, but it holds spending history). Consider SQLCipher only if you distribute widely.
33. **Input/attack surface** — 🟢 Good. SMS body parsed in memory, never persisted; regex hardened this session.
34. **Permissions** — 🔴 Blocker (for Play). RECEIVE_SMS + READ_SMS + CALL_PHONE + background location is a heavy, sensitive set. Play restricts SMS/Call-Log access; a public listing likely gets rejected without a strong, approved use-case. **Fine for personal / APK sharing.**
35. **Privacy** — 🟢 Excellent. No INTERNET permission, no backend, no analytics. Data never leaves the phone.
36. **Sensitive-data handling** — 🟢 Excellent. Raw SMS never stored; UPI PIN never seen/handled; only structured fields kept.
37. **Logging** — 🟢 Good. Only 4 `Log.d`/`Log.e` calls, none log PII or SMS bodies.
38. **Analytics/telemetry** — 🟢 None (by design).
39. **Crash reporting** — 🟡 Medium. None. Acceptable offline, but it's why the Map crash is hard to chase — you rely on manual logcat.
40. **Dependency security** — 🟡 Medium. All first-party AndroidX/Google + ZXing; no risky third parties. Versions are a bit behind (see 100).
41. **Dependency/license review** — 🟢 Good. Apache-2.0 / Google libs; no license concerns.

## 42–50 · Performance

42–45. **Performance / CPU / memory / battery** — 🟡 Medium. Biggest lever: you're testing **debug builds**. A release build (R8 on) is dramatically smoother. Background work is a single 15-min WorkManager job — battery-light.
46. **Startup time** — 🟡 Medium. Cold-start jank was largely debug-build JIT; re-measure on release.
47. **Rendering/frame performance** — 🟡 Medium. Entrance animation was removed after profiling; verify scroll smoothness on release build.
48. **Large-data performance** — 🟠 High. Every screen loads **all** expenses and filters in memory. Fine at hundreds; will slow at thousands. Move filtering/aggregation into SQL queries.
49. **Network/data usage** — 🟢 Minimal (map tiles only).
50. **UI responsiveness** — 🟡 Medium. DB calls are `suspend` (off main thread); the in-memory aggregation is the thing to watch as data grows.

## 51–60 · Compatibility & formatting

51–52. **Screen sizes / resolutions** — 🟡 Medium. Compose adapts; not yet tested on tablets/foldables.
53. **OS versions** — 🟠 High. minSdk 24 → Android 14. Notification-channel API-26 guard fixed earlier; test on an Android 7/8 device or emulator image since your daily device is new.
54. **Orientation / config changes** — 🟢 Good. State uses `rememberSaveable`; no crashes expected on rotation (verify).
55. **Dark/light mode** — 🟢 Good. Full teal-on-dark + light themes.
56. **Font scaling** — 🟡 Medium. Not verified at large accessibility font sizes — check for clipping.
57. **Accessibility** — 🟡 Medium. Most icons have `contentDescription`; no explicit TalkBack pass or touch-target audit.
58. **Localization** — 🟡 Medium. Strings are hardcoded English; currency is ₹/en-IN only. Fine for your audience, not internationalized.
59. **Date/time handling** — 🟢 Good. Calendar-based month keys; legacy date parsing in migration.
60. **Currency/number formatting** — 🟢 Good. Indian grouping (`formatRupeeIndian`), paise-based math.

## 61–73 · UX & states

61–63. **Navigation / back button / deep links / intents** — 🟢 Good. Pager tabs, `BackHandler` in the pay flow, UPI/USSD intents handled.
64–68. **Loading / empty / error / success / permission-denied states** — 🟢 Good. All present, including a dedicated camera-permission empty state and the 6-state payment status screen.
69. **Destructive-action confirmation** — 🟡 Medium. Expense delete uses a dialog; the friend "forget"/data deletes — confirm all destructive actions prompt.
70. **User feedback** — 🟢 Good. Haptics on scan, chips, status colors + icons.
71. **Animations/transitions** — 🟢 Good (after trimming the costly entrance animation).
72. **UI consistency** — 🟢 Good. Shared tokens, one top-gap constant across tabs.
73. **Accessibility (dup)** — see 57.

## 74–89 · Testing

74. **Unit testing** — 🟠 High. Only 3 real test files (SMS parser, UPI-intent helpers, QR parser). Good that they exist; coverage is thin.
75–78. **Integration / UI / e2e / regression** — 🔴 Blocker-ish. None. No Compose UI tests, no end-to-end.
79. **Migration testing** — 🟢 Good. `AppDatabaseMigrationTest` covers 9→10 and 11→12 (add 12→13).
80. **Real-device testing** — 🟢 Partial. Runs on your Pixel 10 Pro; friend's device surfaced the crash.
81. **Low-end-device testing** — 🟠 High. Not done. The "laggy" report may be worse on low-end hardware.
82. **Production-like data testing** — 🟠 High. Not done — test with hundreds of expenses (ties to point 48).
83. **Process-death testing** — 🟡 Medium. Payment flow saves `inFlightPaymentId`; verify by force-killing mid-payment.
84–88. **Restart / reboot / upgrade / fresh-install / reinstall** — 🟠 High. Fresh-install is exactly where the crash appeared. Add this to a manual test checklist.
89. **Release-build testing** — 🔴 Blocker. Never done — you've only run debug builds.

## 90–100 · Build & release

90–91. **Debug code / debug logging removal** — 🟢 Good. Minimal logs, no debug UI, no `printStackTrace`.
92. **Release configuration** — 🔴 Blocker. `isMinifyEnabled = false` in release. Turn on R8 + resource shrinking (with ProGuard keep-rules for Room/ZXing/Maps).
93. **Build configuration** — 🟡 Medium. AGP 8.1.4 / compileSdk 34 — a bit dated but internally consistent.
94. **Signing configuration** — 🔴 Blocker. **Debug-signed only, no release keystore.** Can't publish or ship a stable-identity APK without it. Create and back up a keystore.
95. **Versioning** — 🟠 High. Still `versionCode = 1`, `versionName = "1.0"` after many changes. Bump per build.
96. **CI/CD** — 🟡 Medium. None. A simple GitHub Action running `assembleDebug` + unit tests would catch regressions.
97–99. **Static analysis / lint / security scanning** — 🟠 High. No detekt/ktlint; lint not enforced. Add ktlint + `lint` in CI.
100. **Dependency updates** — 🟡 Medium. core-ktx 1.9.0, compose-bom 2023.03, maps-compose 2.11.4 are old. Update deliberately (some are pinned to compileSdk 34).

## 101–120 · Ops, legal, scale

101. **Backup/restore** — 🟠 High. None. `allowBackup` alone won't protect an SMS/finance app well. Build explicit export.
102. **Data export/import** — 🟠 High. Missing. CSV export is the single most valuable addition for user trust.
103–106. **Monitoring / alerts / crash-ANR / incident plan** — 🟢 N/A for offline hobby app (no backend to monitor). Revisit if you ever add sync.
107. **Rollback strategy** — 🟡 Medium. Git is your rollback; **start committing** (60 files uncommitted). Additive-only migrations already protect data on downgrade-by-reinstall.
108. **Documentation** — 🟡 Medium. Excellent inline comments; no README/architecture doc for a new contributor.
109. **Privacy policy** — 🔴 Blocker (for Play). Required for an app requesting SMS + location, even if data stays on-device. Write one stating "all data stays on your phone."
110. **Terms/legal** — 🟠 High. UPI/payment facilitation has regulatory sensitivity in India; keep the "honest handoff, we never touch your PIN/money" framing explicit.
111. **Store/release requirements** — 🔴 Blocker. SMS-permission declaration form, data-safety section, target-API rules, privacy policy — all needed for Play. Direct APK sharing sidesteps these.
112. **Production config verification** — 🟡 Medium. Confirm the release Maps key is the restricted production key, not a dev key.
113. **Environment separation** — 🟢 N/A. Single environment; no staging/prod backends.
114. **Feature flags/config** — 🟢 N/A at this size.
115–116. **DB/API & backward compatibility** — 🟢 Good. Additive migrations preserve old rows; legacy `payment_attempts` table kept read-only.
117. **Rate limiting/resource limits** — 🟢 N/A (no server).
118. **Scalability** — 🟡 Medium. Single-user local scale is bounded by in-memory filtering (point 48). No multi-user/sync ambitions implied.
119. **Observability** — 🟡 Medium. Logcat only. Fine offline; means bugs need a device + logcat to diagnose (as with the current crash).
120. **Final end-to-end user journey test** — 🟠 High. Do one clean manual pass on a **fresh install**: install → grant/deny each permission → Home/Friends/Map/Insights with zero data → add expense → SMS capture → Scan & Pay (UPI app) → Scan & Pay (*99#) → settle up. This will catch the current crash and validate the whole flow.

---

## What's genuinely excellent (don't lose these)
- **Privacy & security model**: no internet, no backend, no analytics, raw SMS never stored, UPI PIN never touched. This is better than most shipping finance apps.
- **Additive-only migrations** with real migration tests.
- **Concurrency safety** in the payment/SMS reconciliation.
- **Honest-handoff payments** — you correctly refused PIN automation.

## The realistic path
- **For you + friends (now):** fix the Map crash, do a signed **release** build, add CSV export, commit + tag. That's a shippable personal app.
- **For a public Play listing (later):** add privacy policy, resolve SMS-permission policy (or drop auto-SMS for the public build), release signing, versioning, and a basic CI + test pass.

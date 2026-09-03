# Browser Brake

Android self-control app that inserts deliberate friction before impulsive app use.

> 使うな、ではなく。衝動では開かない。

Browser Brake is a self-commitment tool, not hostile tamper resistance. It does not use Device Owner or root. The user can ultimately disable Accessibility or uninstall the app.

## v0.4.1-alpha1

v0.4.1 polishes the multi-Rule Compose app after real-device testing.

### v0.4.1 UX polish

- Android Back / edge-back works through Rule sub-screens.
- Home uses semantic colored cards for 解除条件 / 利用可能 / 利用中 / 利用後の休憩.
- Rule cards show target type, status, place, entry condition and daily progress at a glance.
- 「なぜ今は使えない？」explains the reason and next action before technical state.
- Active use has a one-tap 「利用を終了する」 action.
- A Rule is snapshotted when its Brake starts; edits made during an active episode apply from the next Brake.
- Rule pause/disable is moved to a deeper management screen.
- Pause requires confirmation; disable/delete require typing the Rule name.
- Enabling/resuming remains easy because it strengthens the commitment.
- Notifications identify the active Rule by name.

### App structure

```text
ホーム
├─ 今の状態
├─ 利用時間を選ぶ
├─ なぜ今は使えない？
└─ ルール概要

ルール
├─ ルール一覧
├─ 状態表示
├─ ルール作成
└─ 項目別の編集画面

記録
└─ ルールごとの利用時間 / 利用回数 / 繰り返し利用

設定
├─ 動作チェック
├─ 場所
└─ プライバシー
```

The UI is Kotlin + Jetpack Compose + Material 3.

### Multiple Rules

Each Rule owns:

```text
TARGET
  Browsers / SNS / selected apps

CONTEXT
  all places / selected user-named places

ENTRY BRAKE
  Wait / Phone Break / Walk

SESSION
  choose duration / actual-use clock / wall-clock window

DAILY POLICY
  actual-use limit / Session count

RECOVERY
  post-use break

ESCALATION
  OFF / Standard / Strong
```

Target overlap is intentionally rejected in v0.4. The same app, Browser group or SNS group cannot belong to two enabled Rules.

Only one Brake episode is active at a time. If another Rule's target is opened while a Challenge / READY / Session / Recovery is already active, Browser Brake blocks it until the current episode ends.

### Rule weakening is deliberately deeper

The Rule-list status pill is display-only.

Weakening actions live under:

```text
Rule card
 -> Rule editor
 -> ルールを管理
```

Pause requires confirmation. Permanent disable and delete require typing the Rule name. This is still an alpha mechanism; future Settings Protection is expected to support delayed weakening and an optional explicitly opted-in break mechanism.

### READY flow

```text
Challenge complete
    ↓
READY notification / Home READY card
    ↓
利用時間を選ぶ
    ↓
5 / 10 / 15 min
or 今回はやめる
    ↓
SESSION
```

There is no extra “利用する” confirmation between opening the READY screen and choosing the time.

### Existing runtime behavior retained

- actual foreground-use allowance;
- absolute Session validity window;
- Recovery anchored to the last actual target use;
- per-rule daily usage and Session count;
- per-rule Escalation state;
- daily reset at 04:00 local time;
- over-limit x5 policy with short over-limit Sessions;
- screen-off pauses foreground usage accounting;
- Accessibility foreground inference avoids `TYPE_WINDOWS_CHANGED`;
- 100 m place exit hysteresis;
- local runtime diagnostics/timing tests inherited from alpha3.

## Target groups

### Browsers

Browser packages are detected using known packages plus Android `CATEGORY_APP_BROWSER` handlers.

### SNS

v0.4 provides a conservative curated SNS group for apps such as X, Instagram, Reddit, Threads, Bluesky, Facebook and Mastodon. Messaging apps such as LINE and Discord are not automatically classified as SNS.

The user can always add individual launcher apps manually.

## Privacy

Target stance:

- no account;
- no ads;
- no analytics by default;
- no cloud;
- no `INTERNET` permission.

Accessibility window-content retrieval remains disabled (`canRetrieveWindowContent=false`).

## Build

Current stack:

- Android compileSdk / targetSdk 36
- AGP 9.3.1
- AGP built-in Kotlin
- Compose BOM 2026.04.01 (Compose 1.11 generation)
- Java 17

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

CI debug APKs use the public **test-only** Browser Brake key. Never use that key for production.

## Still planned

- time-of-day / weekday Context;
- delayed weakening of settings;
- hard daily-limit option;
- persistent event history and abandonment metrics;
- import / export;
- paid commitment break experiment;
- DataStore / Room migration;
- reboot-safe monotonic timing;
- production signing and Google Play policy work;
- PiP / split-screen / OEM instrumentation testing.

See:

- [Product & runtime design](docs/DESIGN.md)
- [UI architecture](docs/UI_ARCHITECTURE.md)
- [Implementation status](docs/IMPLEMENTATION_STATUS.md)

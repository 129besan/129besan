# AppLockout

Android向けのアプリ利用制限・セルフコントロールアプリです。

AppLockoutは、対象アプリ・場所・解除条件・利用時間・1日の上限・利用後の休憩を組み合わせて制限できます。Device Owner / rootは使わず、最終的にはAndroid設定からAccessibilityを無効化したり、アプリをアンインストールしたりできます。

## v0.4.3-alpha1

v0.4.3は、実機でのv0.4.2評価を受けて、介入画面・アプリ選択・記録・利用中overlayを改善した版です。

### 主な変更

- Frictoから **AppLockout** へ暫定改名。
- ホーム上部の説明文を削除し、機能中心のUIに変更。
- 「アプリを追加」を **アイコン＋アプリ名**中心の一覧へ変更。
- アプリ一覧を SNS / 動画・音楽 / ゲーム / ブラウザ / メッセージ / 仕事・ツール / その他 に分類。
- 検索は全カテゴリ横断。
- ブラウザ/SNSグループで既に対象のアプリは個別選択一覧から除外。
- 対象アプリを開いたとき、HOMEを経由せず **その場で介入ゲートActivityを表示**。
- ゲート自身のタップはPhone Breakの操作検出から除外。
- 呼吸UIを単純な円から **Compose Canvasの数式ベースハーモニック模様**へ変更。
- Recordsから「今日」カードを削除。
- Recordsは **30日間の実使用時間グラフ + 1日の上限ライン + 90日範囲の現在ストリーク**を中心に変更。
- Session中overlayを画面右上寄りへ移動。
- overlayの「離れる」を **「ロック」**へ変更。
- 「ロック」はSession entitlement自体を終了し、RecoveryまたはLOCKEDへ遷移する。

## 画面構成

Home:
- current runtime state
- why unavailable
- restriction list
- add restriction

Records:
- 30-day actual-use chart
- daily-limit reference line
- recorded / within-limit / average metrics
- current streak

Settings:
- service health
- reusable Places
- privacy

## Restriction model

- TARGET: Browsers / SNS / individual apps
- CONTEXT: all places / user-named Places
- MODE: Challenge-based / Full Lock
- CHALLENGE: Wait / Phone Break / Walk / ALL・ANY
- SESSION: choose duration / actual foreground clock / entitlement lifetime
- DAILY: actual-use limit / Session-count limit
- RECOVERY: post-use wait
- ESCALATION: repeated-use friction

## Direct intervention gate

Normal target-open flow:

target app attempt
→ AppLockout gate appears over the attempted app
→ Challenge
→ READY
→ choose duration
→ target app Session

The gate no longer intentionally sends HOME before launching itself. This avoids the HOME-vs-Activity race that could make the gate flash briefly and disappear.

If the user chooses 「今回はやめる」 or backs out, AppLockout navigates to HOME rather than revealing the restricted target underneath.

## Harmonic breathing graphic

The gate uses Compose Canvas instead of a heavy external graphics dependency.

Several parameterized polar curves are drawn with changing phase and radius. A slower rotational phase and a 4-second breathing oscillation produce an animated layered pattern around 「吸って / 吐いて」.

This is presentation-level intervention. Wait / Phone Break / Walk remain the actual Challenge semantics.

## Session overlay

While a target is consuming actual-use time:

残り 7:42   [ロック]

The overlay is an Accessibility overlay near the upper-right edge.

「ロック」:
1. charges foreground use so far;
2. ends the current Session entitlement;
3. sends HOME;
4. enters Recovery if configured, otherwise returns to LOCKED.

Simply going HOME without pressing Lock still pauses actual-use consumption and keeps the Session entitlement alive until its wall-clock deadline.

## Records

The Home screen owns today's current/configured values. Records is for trend.

Per normal restriction, v0.4.3 shows:
- 30-day actual-use bars;
- configured daily-time limit as a horizontal reference line;
- recorded days;
- days within the current limit;
- average use on recorded days;
- current streak, evaluated across up to 90 days.

Historical goal evaluation still uses the current restriction settings in this alpha. Historical setting snapshots are planned.

## App picker categories

Category detection uses:
1. existing Browser/SNS package logic;
2. known messaging packages;
3. Android ApplicationInfo.category for Game / Audio / Video / Social / Productivity / Maps / Image / News;
4. Other fallback.

Package names remain searchable but are no longer the primary visible content.

## Build

- compileSdk / targetSdk 36
- minSdk 29
- Java 17
- versionCode 10
- versionName 0.4.3-alpha1

The applicationId remains dev.besan.browserbrake so this build can update previous test builds signed with the same public test-only key.

See docs/DESIGN.md, docs/UI_ARCHITECTURE.md, docs/IMPLEMENTATION_STATUS.md, docs/COMPETITIVE_POSITIONING.md.

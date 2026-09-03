# AppLockout

Android向けのアプリ利用制限・セルフコントロールアプリです。

AppLockoutは、対象アプリ・場所・解除条件・利用時間・1日の上限・利用後の休憩を組み合わせて制限できます。Device Owner / rootは使わず、最終的にはAndroid設定からAccessibilityを無効化したり、アプリをアンインストールしたりできます。

## v0.5.0-alpha2

v0.5は、v0.4までの最大の構造的制約だった「進行中の制限は1つだけ」を廃止するruntime rewriteです。

### v0.5の主な変更

- Challenge / READY / Session / Recoveryを **制限ごとに独立保存**。
- 複数の制限を同時に進行可能。
- 制限AがChallenge中でも、制限BのChallengeを開始可能。
- AがREADYのままBを使う、A/B両方のSession entitlementを保持する、といった状態を許可。
- foreground実使用時間を消費するのは、その瞬間に前面にいる対象アプリのSessionだけ。
- 別の対象アプリへ切り替えると、前のSessionの実使用時計を停止して新しいSessionの時計を開始。
- 通知は制限ごとに独立したnotification IDを持ち、同じAppLockout notification groupへまとめる。
- READY通知・ロック通知・利用時間選択画面はrestrictionIdを保持し、別制限へ誤作用しない。
- Homeは進行中runtimeを1枚ではなく **制限ごとの複数カード**として表示。
- Place判定のhysteresis状態も制限IDごとに分離。
- v0.4の単一transient runtimeはv0.5初回起動時に安全側で終了。制限定義・場所・日次履歴は維持。

## 並列runtime

各通常制限は独立したstate machineを持ちます。

```text
Restriction A
LOCKED -> CHALLENGING -> READY -> SESSION -> RECOVERY -> LOCKED

Restriction B
LOCKED -> CHALLENGING -> READY -> SESSION -> RECOVERY -> LOCKED

Restriction C
...
```

たとえば:

```text
SNS       CHALLENGING  スマホ休憩 2:15
Browser   READY        利用時間を選べる
YouTube   SESSION      実使用 6:42 残り
```

という状態を同時に保持できます。

### Sessionのforeground accounting

複数Session entitlementが同時に存在しても、実使用時計は実際に前面にいる対象だけ進みます。

```text
Chrome Session: 7分残り
YouTube Session: 4分残り

Chrome foreground 2分
 -> Chrome 5分
 -> YouTube 4分

YouTubeへ切替 1分
 -> Chrome 5分
 -> YouTube 3分
```

wall-clockのSession有効期限はそれぞれ独立して進みます。

## 通知

各制限は別notificationを持ちます。

- Challenge
- READY
- Session
- Recovery
- Full Lock

notification actionにもrestrictionIdを入れているため、SNSの「今回はやめる」がBrowserのREADYを消すことはありません。

通知は同じAppLockout group keyで束ねます。

## Home

Homeは現在進行中の制限をすべて表示します。

進行中がなければ:

> 現在進行中の制限はありません

進行中なら制限ごとに:

- 対象アプリアイコン
- Challenge / READY / Session / Recovery
- 残り時間
- 利用時間を選ぶ
- 利用を終了する
- なぜ今は使えない？

を表示します。

## v0.4.3から引き続き使えるもの

- AppLockout名称 / 青系UI
- アイコン＋カテゴリ付きアプリ選択
- Browser / SNS group
- user-named Places
- Wait / Phone Break / Walk
- Challenge ALL / ANY
- Full Lock
- READY + 利用時間選択
- actual foreground-use clock
- Session wall-clock lifetime
- daily usage / Session count limits
- over-limit stronger Challenge
- Recovery
- Escalation
- 30日利用グラフ（時間上限超過は赤表示） / streak（一時停止・無効化で切断）
- タッチで引き寄せられるインタラクティブ粒子gate
- Session overlay + ロック

## Migration from v0.4

applicationIdと公開テスト署名は維持するため、v0.4.3から上書き更新できます。

Durable data:
- 制限定義: 維持
- Places: 維持
- daily/history metrics: 維持

Transient data:
- v0.4で進行中だったChallenge / READY / Session / Recovery: v0.5初回migrationで終了

単一runtimeを複数runtimeへ曖昧に変換するより、安全で説明可能な挙動を優先しています。

## Known alpha limitations

- transient deadlineはまだSystem.currentTimeMillis()ベース。
- reboot reconciliation未完成。
- Placeはpassive / last-known location依存でfreshness問題が残る。
- PiP / split-screen / OEM別Accessibility挙動は実機検証が必要。
- historical daily recordsは当時の制限設定snapshotを保存していない。
- Full Lockのblock attempt historyは未実装。
- schedule / weekday Contextは未実装。
- Settings Protection / delayed weakeningは未実装。
- Google Play Accessibility/background-location対応は未完。
- production signing未設定。

## Build

- compileSdk / targetSdk 36
- minSdk 29
- Java 17
- versionCode 12
- versionName 0.5.0-alpha2
- applicationId dev.besan.browserbrake

CI debug APKは従来と同じ公開テスト専用署名鍵を使用します。productionには使用しません。

See:
- docs/DESIGN.md
- docs/UI_ARCHITECTURE.md
- docs/IMPLEMENTATION_STATUS.md
- docs/COMPETITIVE_POSITIONING.md

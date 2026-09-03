# Fricto

Android向けの、衝動的なアプリ利用に「ちょうどよい摩擦」を入れるセルフコントロールアプリです。

> 必要なときは使える。でも、衝動では開かない。

Fricto は敵対的なロックダウン製品ではなく、自分で決めた制限を守りやすくする commitment device です。Device Owner / root は使いません。Android設定からAccessibilityを無効化したり、アンインストールしたりする最終的な逃げ道は残ります。

## v0.4.2-alpha1

実機で ScreenZen / one sec を比較した結果を反映したUI・介入体験の更新です。

### 主な変更

- Browser Brake から Fricto へ改名。
- ユーザー向けの「ルール」を「制限」に変更。
- ホームと制限一覧を統合。下部ナビは ホーム / 記録 / 設定 の3つ。
- ホーム右下の＋から新しい制限を作成。
- 制限カードは対象アプリの実アイコンを最大4個表示。5個以上は +N。
- 青〜藍を基調にしたFricto固有のMaterial 3パレットとグラデーション背景。
- 完全ロックを追加。
- 対象アプリを開いたとき、呼吸アニメーション付きのゲートを表示。
- 利用中はAccessibility overlayで残り実使用時間 + 「離れる」を表示。
- 記録画面を今日の数字の重複から「この7日間 + 継続記録」へ変更。
- 「現在の厳しさ Level N」のような内部指標を通常の記録画面から削除。
- 今日の利用は 現在値 / 設定上限 を明示。
- 進行中のBrakeは開始時設定をsnapshotし、編集は次回から反映。

## 画面構成

ホーム: 今の状態 / なぜ今は使えない？ / 制限一覧 / ＋新しい制限  
記録: 今日の利用時間・利用回数 / この7日間 / 連続記録  
設定: 動作チェック / 場所 / プライバシー

## 制限モデル

各制限は以下を持ちます。

- TARGET: ブラウザ / SNS / 個別アプリ
- CONTEXT: すべての場所 / ユーザーが登録した場所
- MODE: 解除条件あり / 完全ロック
- ENTRY: 待つ / スマホ休憩 / 歩く / ALL・ANY
- USE: 利用前の時間選択 / 実使用時計 / 利用権の有効時間
- DAILY: 1日の実使用時間 / 1日の利用回数
- AFTER USE: 利用後の休憩
- ADAPTATION: 繰り返すほど解除条件を厳しくする

### 完全ロック

Contextが有効な間、対象アプリを開けません。解除条件・Session・Recoveryは使いません。

v0.4.2では「制限そのもののモード」です。「1日の上限超過後だけ完全ロック」はまだ別機能として未実装です。

### 呼吸ゲート

通常制限で対象アプリを開くと、HOMEへ戻したあとFrictoのゲート画面を開きます。

- 青いグラデーション
- 拡大縮小する呼吸アニメーション
- 「吸って / 吐いて」
- 現在の解除条件と残り時間
- READY後の「利用時間を選ぶ」
- 「今回はやめる」

ゲートから戻ってもChallengeはruntime側で継続します。

### 利用中オーバーレイ

Session中に対象アプリがforegroundのときだけ、Accessibility overlayを表示します。

表示例: 残り 7:42 / 離れる

「離れる」はHOMEへ移動し、実使用時計を停止します。Session entitlementは残るため、有効時間内なら再び対象アプリへ戻れます。

追加のSYSTEM_ALERT_WINDOW権限は要求せず、TYPE_ACCESSIBILITY_OVERLAYを利用しています。

## 記録

ホームを「今日」、記録画面を「推移」に分けました。

通常制限では以下を表示します。

- 今日の利用時間: 現在 / 設定上限
- 今日の利用回数: 現在 / 設定上限
- 残り時間 / 残り回数
- 過去7日間の上限内 / 超過 / データなし
- 現在の連続記録

4:00の日次reset時に前日の集計をSharedPreferencesへarchiveします。v0.4.2以前の過去データは復元できません。またalphaでは過去日も現在の制限値で成功判定します。

## 複数制限

Durableな制限定義は複数保持できます。対象アプリの複数有効制限への重複は拒否します。

ただし通常の transient runtime はv0.4.2でもアプリ全体で1つです。

LOCKED → CHALLENGING → READY → SESSION → RECOVERY

複数Challenge / READY / Session / Recoveryを同時保持するper-restriction RuntimeStoreは次段階です。

## Privacy

- アカウントなし
- 広告なし
- analyticsなし
- cloudなし
- INTERNET permissionなし
- Accessibilityのwindow content取得なし

## Build

- compileSdk / targetSdk 36
- minSdk 29
- AGP 9.3.1
- Compose BOM 2026.04.01
- Java 17
- versionCode 9
- versionName 0.4.2-alpha1

CI debug APKは従来と同じ公開テスト専用署名鍵を使います。productionには使用しません。

## 次の候補

- 制限ごとの独立runtime / 同時Challenge・READY・Session・Recovery
- 制限ごとのnotification ID + Notification Group
- schedule / weekday Context
- 設定を弱める変更の遅延
- 1日の上限超過後のHard Lock
- ブロック試行・Challenge放棄などのevent history
- streak判定時のhistorical settings snapshot
- reboot-safe monotonic timing
- DataStore / Room
- Google Play Accessibility / background-location policy対応
- production signing

See docs/DESIGN.md, docs/UI_ARCHITECTURE.md, docs/IMPLEMENTATION_STATUS.md, docs/COMPETITIVE_POSITIONING.md.

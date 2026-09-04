# AppLockout

Android向けのアプリ利用制限・セルフコントロールアプリです。

AppLockoutは、対象アプリ・場所・解除条件・利用時間・1日の上限・利用後の休憩を組み合わせて制限できます。Device Owner / rootは使わず、最終的にはAndroid設定からAccessibilityを無効化したり、アプリをアンインストールしたりできます。

## v0.5.0-alpha4

- GPU shader intervention on Android 13+ with a soft-blob fallback
- paused target-app time is charged to daily usage
- pause expiry is enforced while the target app remains foreground
- Phone Break only advances on the launcher/AppLockout safe surface
- Phone Break target re-entry returns to the intervention gate
- new restrictions stay as unsaved drafts until Save
- target summaries show up to three app icons and names, then +N
- Records prioritizes current/best streak and a 30-day achievement calendar
- raw usage history remains available as secondary detail
- one-shot streak entry animation

## Product premise

AppLockoutの中心は「完全に使わせない」ことではなく、無意識・反射的なアプリ起動の直前に摩擦を入れ、必要なときは意図的に利用できるようにすることです。

制限は次の要素から構成されます。

```text
Rule
├── TARGET       what is braked
├── CONTEXT      where / when
├── ENTRY BRAKE  challenge
├── SESSION      allowed use
├── RECOVERY     post-use refractory
└── POLICY       daily limits / escalation / etc
```

Runtimeは概ね次の状態を取ります。

```text
LOCKED -> CHALLENGING -> READY -> SESSION -> RECOVERY -> LOCKED
```

- Challenge: 待つ / スマホ休憩 / 歩く。複数条件はALL/ANY。
- READY: Challenge達成後も自動解放せず、利用するかを明示的に決める。
- Session: 実際に対象アプリがforegroundだった時間を消費する。
- Recovery: 利用直後の再侵入を防ぐ休憩時間。
- 1日の区切りはローカル時刻04:00。

## v0.5 runtime model

v0.5ではrestrictionごとに独立runtimeを持ちます。

- Challenge / READY / Session / Recoveryはrule単位。
- 複数ruleのruntimeを同時に保持可能。
- notification ID / actionもrule単位。
- Session entitlementが複数あってもforeground利用を消費できるのは、その時点で前面にいる1つの対象だけ。
- runtime開始時にBrowserRule snapshotを保存するため、進行中episodeに設定変更を漏らさない。
- Place判定のhysteresisもrule単位。

## Challenge

### Wait

設定時間が過ぎると達成です。他のスマホ操作をしていても時間は進みます。

### Phone Break

スマホを実際に触らないためのChallengeです。

- 他アプリがforegroundにある間は休憩時計が進みません。
- launcher / AppLockout / intervention gateをsafe surfaceとして扱います。
- safe surface上で操作するとquiet periodを最初から数え直します。
- 対象アプリへ再侵入するとintervention gateへ戻します。

### Walk

端末のstep counterで歩数を数えます。Android 10+ではACTIVITY_RECOGNITION permissionが必要です。

## READY / Session

Challenge達成後はREADYになります。

- 通常は5 / 10 / 15分など今回の利用時間を選択。
- 「今回はやめる」でruntimeを終了。
- ready timeoutは任意。
- Sessionは実使用時間とwall-clock validity windowを分けて管理。
- 日次上限超過後はstronger challengeとなり、追加Sessionは短時間に制限。

## Pause / commitment

一時停止・無効化は「制限を弱める操作」として記録します。

- pause / disableを使った日はcommitmentBroken=true。
- その日はstreakに含めません。
- pause中に対象アプリをforeground利用した時間もdaily usageへ加算します。
- pause期限はAccessibilityService側で監視し、対象アプリを開きっぱなしでも期限で再制限します。
- pause確定後は管理画面ではなくAppLockout Homeへ戻します。

## Records

Recordsでは「使った量」より「守れた積み重ね」を主役にします。

- 現在のstreak
- 最長streak
- 直近30日の達成カレンダー
- 直近30日の達成日数

実利用時間は引き続き記録し、「利用時間の記録を見る」から補助情報として確認できます。

## Data

現時点ではSharedPreferencesに保存しています。

Durable data:

- BrowserRule JSON
- Place
- per-rule daily usage
- per-rule daily sessions
- per-rule commitment-break state
- history records
- escalation state

Transient data:

- per-rule runtime state
- runtime BrowserRule snapshot
- pending target
- Challenge deadlines / step baseline
- READY deadline
- Session remaining foreground budget / wall deadline
- Recovery deadline

## Known limitations

- reboot時のruntime reconciliationは未完成。
- runtime deadlineはwall clockベース。
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
- versionCode 14
- versionName 0.5.0-alpha4
- applicationId dev.besan.browserbrake

CI debug APKは従来と同じ公開テスト専用署名鍵を使用します。productionには使用しません。

See:
- docs/DESIGN.md
- docs/UI_ARCHITECTURE.md
- docs/IMPLEMENTATION_STATUS.md
- docs/COMPETITIVE_POSITIONING.md

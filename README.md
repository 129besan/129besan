# Browser Brake

Android self-control app for inserting deliberate friction before opening browsers or other selected apps.

> 使うな、ではなく。衝動では開かない。

Browser Brake is currently an **alpha prototype**. It is designed for self-commitment, not hostile tamper resistance. It does not use Device Owner or root; the user can ultimately disable Accessibility or uninstall the app.

## v0.3-alpha flow

```text
LOCKED
  -> target app opened
CHALLENGING
  -> challenge completed
READY
  -> user deliberately reopens target and chooses to use it
SESSION
  -> actual usage allowance or session window expires
RECOVERY
  -> post-use cooldown expires
LOCKED
```

Challenge completion never automatically unlocks the target. READY means only "you may deliberately choose to start a session".

## Implemented in v0.3-alpha1

- Browser group detection + user-selected launcher apps
- Place condition: `ALL` or multiple user-named places
- Challenges: Wait / Phone Break / Walk, with ALL / ANY
- READY state, default timeout: none
- Explicit "本当に必要なら利用する / 今回はやめる"
- Session duration selection before use (default ON)
- Two session clocks: actual foreground usage + absolute validity window
- Daily actual-use limit and daily session-count limit
- Post-use Recovery
- Escalation: Session start raises level; quiet periods decay it
- Over-limit state: "今日の上限を超えています"
  - challenge x5 by default
  - time challenges minimum 10 min / cap 30 min
  - over-limit Session max 3 min
- "なぜブロックされた？" state explanation
- Local-only runtime state; no INTERNET permission

## Not yet implemented

- Multiple independent Rules
- Schedule/day-of-week condition
- Delayed weakening of settings
- Hard-lock option after daily limit
- Paid rule-break / Google Play Billing
- Detailed history/statistics
- Import/export
- Production signing
- Reboot-safe monotonic transient timers

## Build

The CI debug APK uses a **public test-only signing key**. Never use it for production.

```bash
gradle :app:assembleDebug
```

Android SDK 36 / Java 17.

## Privacy

Intended stance:

- no account
- no ads
- no analytics by default
- no cloud
- no `INTERNET` permission

Accessibility is used for target-app foreground state and the limited interaction signals required by Phone Break. Location is only for user-configured place conditions.

See [docs/DESIGN.md](docs/DESIGN.md) and [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md).

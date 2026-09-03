# v0.4.1-alpha1 Implementation Status

## Implemented

### UI architecture

- Kotlin + Jetpack Compose + Material 3.
- Bottom navigation: Home / Rules / Records / Settings.
- Rule list with rich summary cards.
- Rule editor split into semantic sub-screens rather than one giant settings page.
- READY screen directly asks for Session duration.
- Home READY card.
- “なぜブロックされた？” explanation.
- System Health screen.
- Place management from Settings.
- Searchable custom app picker.

### Multiple Rules

- Persist multiple Rule definitions.
- Migrate the previous singleton Rule into the v0.4 Rule store.
- Per-Rule target definitions.
- Browsers checkbox.
- SNS checkbox.
- Individual launcher apps.
- Per-Rule Place selection.
- Per-Rule Challenge / Session / Daily / Recovery / Escalation settings.
- Target-overlap detection.
- Runtime resolves the target package to its enabled Rule.
- Per-Rule daily metrics and Escalation storage.
- One active Brake episode at a time.

### Rule status

- No direct ON/OFF switch on cards.
- 15-minute pause.
- 1-hour pause.
- resume.
- explicit disable confirmation.
- disabling/pausing the currently active Rule terminates its runtime episode.
- re-enable is blocked if the Rule now conflicts with another enabled Rule.

### Runtime inherited from v0.3-alpha3

- Wait / Phone Break / Walk.
- Challenge ALL / ANY.
- READY with optional timeout.
- actual foreground Session clock.
- absolute Session window.
- daily actual-use limit.
- daily Session count.
- 04:00 reset.
- Escalation + decay.
- over-limit policy.
- Recovery anchored to last actual use.
- screen-off foreground pause.
- foreground accounting audit fixes.
- timing unit tests.

## Not implemented yet

- weekday / time schedule.
- delayed settings weakening.
- hard-lock daily-limit policy.
- editing all over-limit constants.
- paid rule break / Play Billing.
- Video curated group.
- detailed event history / abandonment metrics.
- import / export.
- DataStore / Room.
- monotonic/reboot-safe timers.
- full instrumentation tests.
- production signing.

## Important v0.4-alpha limitations

1. Only one Challenge / READY / Session / Recovery episode may run at once across all Rules.
2. Target overlap is rejected instead of applying priorities.
3. SNS classification is intentionally conservative and curated.
4. Place context still relies on passive / last-known Android location and has freshness risk.
5. Walk still needs explicit unavailable-state UX when no sensor/permission exists.
6. Transient deadlines still use wall-clock timestamps.
7. Accessibility foreground inference needs additional PiP / split-screen / OEM testing.

## Real-device test focus

1. Create a Browser Rule and an SNS Rule.
2. Verify target overlap cannot be enabled.
3. Verify each Rule uses its own Challenge values.
4. Verify daily usage / Session count stay separate by Rule.
5. Pause one Rule for 15 minutes and verify the other Rule still works.
6. Complete a Challenge and enter Session directly from the READY notification.
7. Verify actual-use timing still pauses outside the target app.
8. Verify Recovery timing remains anchored to actual target use.
9. Verify Home and “なぜブロックされた？” match runtime state.
10. Verify the app remains understandable without opening advanced sections.


## v0.4.1 UX polish

Implemented:

- system/edge Back handling for the editor hierarchy;
- semantic colored runtime cards;
- richer Rule cards with status, target badge and daily progress;
- direct “利用を終了する” action during active use;
- human-readable “なぜ今は使えない？” explanation;
- technical runtime details hidden behind disclosure;
- Rule-specific notification titles;
- explicit runtime snapshot metadata;
- active-Rule edit banner: changes apply next Brake;
- Rule-list status chip is display-only;
- pause/disable/delete moved into the bottom of Rule management;
- confirmation before pause;
- Rule-name typing required for disable/delete;
- Japanese cleanup for target groups and runtime concepts.

Still deferred to v0.5:

- independent per-Rule transient state machines;
- simultaneous Challenges;
- multiple READY qualifications;
- simultaneous Session entitlements;
- per-Rule Recovery states;
- notification IDs/groups per Rule.

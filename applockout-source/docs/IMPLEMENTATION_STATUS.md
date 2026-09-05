# AppLockout v0.5.0-alpha7 Implementation Status

## v0.5.0-alpha7

- Supplied adaptive launcher icon adopted.
- Onboarding backdrop uses continuous periodic motion and radial-gradient glows.
- Stronger app background contrast and subtle gradient key cards.
- Settings owns permissions / health checks; Info is text-first.
- Long explanations use compact chevron rows and detail pages.


- UI: battery-saver由来のdark blue/violet paletteを常時採用。global card shapesを丸めた。
- Onboarding: animated backdrop / step transition / selection feedback / animated final testを追加。
- Home: active runtime cardsをcompact化。
- Navigation: SettingsとInfoを分離し、health/privacy/app metadataをInfoへ移動。
- Runtime resilience: foreground heartbeat checkpoint、paused-usage checkpoint、boot-count walk baseline reset、root-window foreground reconciliation、boot/package-replace reconciliationを追加。


## v0.5 core rewrite

Implemented:

- paused target-app foreground usage is still charged to daily usage;
- phone-break challenge pauses/resets while another app remains foreground, including passive reading;
- intervention gate is one-shot and READY routes directly to the usage-duration chooser;
- Android 13+ intervention visual uses an AGSL RuntimeShader fluid field;
- records streak uses a one-shot entry count/pop animation;
- pause confirmation adds deliberate delay and returns to AppLockout home;
- non-verbal GPU fluid intervention replacing the verbal breathing animation;
- touch/drag warps the shader field without rendering discrete particles;
- explicit daily commitment-break history for pause/disable;
- streak reset semantics for manual pause/disable;
- time-over-limit histogram bars rendered in error color;

- per-restriction runtime namespace in SharedPreferences;
- independent Challenge state;
- independent READY state and timeout;
- independent Session allowance;
- independent Session wall-clock deadline;
- independent Recovery deadline;
- independent runtime BrowserRule snapshot;
- independent pending target;
- independent Place hysteresis state;
- active runtime ID set;
- migration from v0.4 single transient runtime;
- per-restriction notification IDs;
- notification actions carrying restrictionId;
- notification group key;
- Home with multiple active runtime cards;
- one foreground Session consumer with switching between multiple Session entitlements.

## Expected supported scenarios

### Two simultaneous Challenges

A Challenge may remain active while a target from B starts its own Challenge.

### Challenge + READY

A can be READY while B continues its Challenge.

### READY + READY

Both qualifications can coexist. Each notification/button opens the correct duration decision.

### Session + Challenge

A permitted target Session can be paused by leaving it while B's Challenge progresses.

### Session + Session

Two entitlements may coexist. Actual-use accounting follows the foreground target only.

### Recovery + other runtime

A can be in Recovery while B is Challenge/READY/Session.

### Full Lock

Full Lock remains stateless per target attempt and can coexist with normal runtimes.

## Migration

On first v0.5 runtime initialization:

- existing durable restrictions remain;
- Places remain;
- per-rule daily/history metrics remain;
- legacy v0.4 transient state is ended;
- legacy notification ID 2001 is canceled;
- v0.5 active runtime set starts clean.

## Real-device test matrix

High priority:

1. Start Challenge A.
2. Leave its gate without declining so A remains active.
3. Start Challenge B.
4. Confirm both cards and both notifications exist.
5. Complete A; B must remain unchanged.
6. Complete B; both may be READY.
7. Start Session A; B READY must remain.
8. Start Session B.
9. Switch A -> B -> A and verify only foreground actual-use time decreases.
10. Lock B from overlay; A Session must survive.
11. Let A wall-clock entitlement expire while B is foreground; B must survive.
12. Put A into Recovery and start/use B.
13. Decline one READY notification; the other READY must survive.
14. Pause/disable one active restriction; unrelated runtimes and notifications must survive.
15. Verify two different Place restrictions do not share hysteresis state.
16. Edit A's target list while A is Challenge/READY/Session; A must continue using its start-time target snapshot, while the edited target list applies only after A returns to LOCKED.

Regression:

- direct gate remains visible on Pixel/Android 17;
- Phone Break gate interaction does not self-reset;
- Walk still progresses;
- Full Lock still blocks;
- overlay is top-right and Lock ends current Session;
- 30-day chart / streak remain intact;
- v0.4.3 restrictions and Places survive update.

## Known limitations

- integration concurrency is not covered by local JVM tests; real-device testing is required.
- deadlines still use wall clock.
- reboot reconciliation remains incomplete.
- passive/last-known location freshness remains a risk.
- current foreground inference still needs PiP/split-screen/OEM testing.
- historical daily limits are not snapshotted.
- Full Lock attempts are not persisted.
- schedule/weekday Context not implemented.
- Settings Protection 30-second confirmation is implemented; long delayed weakening is not implemented.
- production Play policy/signing work remains.


## alpha4 UX/runtime fixes

- Phone Break target re-entry returns to the intervention gate instead of silently kicking home.
- BrakeGate lifecycle explicitly tells the AccessibilityService when the intervention surface is foreground, preventing late target-app events from invalidating the Phone Break safe surface.
- New restrictions remain in-memory drafts until Save; backing out leaves no persisted half-created rule.
- Target summaries show up to three app icons with names, then +N.
- Records now prioritizes current/best streak and a 30-day achievement calendar; raw usage time remains available as secondary detail.


## alpha5 onboarding / Settings Protection

- Fresh installs enter a guided first-run flow before any synthetic migration rule is created.
- The guide selects 1–3 launchable apps and one simple challenge preset: Phone Break 1 min, Wait 15 sec, or Walk 50 steps.
- Accessibility is requested contextually; Activity Recognition is requested only for Walk.
- The final onboarding action persists the first rule and can launch the selected target for a real gate test.
- Existing installs automatically mark onboarding complete; Settings provides a guided-rule entry for replay/testing without clearing app data.
- RuleRepository compares persisted and draft rules to detect weakening changes.
- Protected examples include target removal, place narrowing, challenge removal/shortening, longer session/daily allowances, shorter Recovery, and weaker escalation.
- Protected edits require a 30-second confirmation and mark the current day commitment as broken.
- Strengthening edits save immediately.
- Rule management no longer silently saves an unsaved editor draft, avoiding a Settings Protection bypass.
- JVM tests cover representative strengthening/weakening comparisons.

# Fricto v0.4.2-alpha1 Implementation Status

## Implemented

### Product / UI

- Product-facing name changed to Fricto.
- User-facing “Rule” terminology changed to 「制限」.
- Bottom navigation reduced to Home / Records / Settings.
- Restriction list merged into Home.
- Home FAB creates a restriction.
- Fricto-specific blue / indigo Material 3 palette.
- Subtle blue gradient top-level background.
- Real installed target app icons, maximum 4 + +N.
- Human-readable 「なぜ今は使えない？」.
- System / edge Back handling retained.
- Direct 「利用を終了する」 retained.

### Restriction methods

- Normal Challenge-based restriction.
- Full Lock mode.
- Full Lock hides normal Session / Daily / Recovery / Escalation settings.
- Full Lock checks Context, returns target to Home, shows Fricto gate and notification.
- Full Lock does not occupy the ordinary transient state machine.

### Entry intervention

- Animated breathing gate Activity.
- Blue gradient.
- Inhale / exhale animation and copy.
- Live Wait / Phone Break countdown.
- Walk requirement display.
- READY state and direct duration selection.
- Explicit decline path.
- Challenge remains active after leaving the gate.

### Session

- actual foreground-use clock.
- absolute Session entitlement window.
- duration choice before use.
- compact Accessibility overlay while target is foreground.
- overlay shows remaining actual-use time.
- overlay 「離れる」 sends Home and pauses actual-use consumption while retaining entitlement.
- cleanup on screen off / app leave / state end / Context exit / service destroy.

### Daily / Records

- per-restriction daily actual usage.
- per-restriction Session count.
- daily reset at 04:00.
- previous-day archive during reset.
- last-seven-budget-days record API.
- today current/configured limits visible.
- remaining time/count.
- seven-day goal cells.
- current streak and subtle animated badge.
- raw Escalation Level removed from normal Records UI.

### Existing runtime retained

- Wait.
- Phone Break.
- Walk.
- Challenge ALL / ANY.
- READY with optional timeout.
- daily usage limit and Session-count limit.
- over-limit x5 alpha policy.
- Recovery.
- Escalation + decay.
- location contexts.
- target overlap rejection.
- runtime snapshot behavior.
- notifications.
- foreground accounting audit fixes.

## Important limitations

1. Normal restrictions still allow only one Challenge / READY / Session / Recovery episode globally.
2. Historical goal cells use current restriction limits rather than historical configuration snapshots.
3. v0.4.2 cannot reconstruct history from before its archive keys existed.
4. Full Lock does not yet record blocked attempts.
5. Full Lock is a restriction mode; hard lock only after daily limit is not implemented.
6. Gate Activity launch from AccessibilityService needs OEM / Android-version real-device testing; notification is fallback.
7. Accessibility overlay needs PiP / split-screen / OEM testing.
8. Place context still uses passive / last-known location and has freshness risk.
9. transient deadlines still use wall clock.
10. production Play Accessibility / background-location review remains unresolved.

## Real-device test focus

1. Upgrade v0.4.1 -> Fricto and verify existing restrictions and places survive.
2. Confirm app label is Fricto.
3. Home has only Home / Records / Settings and + creates a restriction.
4. Restriction cards show correct installed icons and +N.
5. Full Lock at an active place immediately prevents target use.
6. Full Lock outside its place does not interfere.
7. Normal target open shows breathing gate.
8. Wait / Phone Break / Walk still complete correctly from the gate.
9. Back out of gate; normal Challenge continues.
10. READY notification and gate both reach duration chooser.
11. During target use, overlay countdown decreases.
12. 「離れる」 returns Home, preserves remaining Session entitlement and pauses actual-use clock.
13. Overlay disappears after Session end, Context exit and screen-off.
14. Recovery still anchors to actual use.
15. Records shows current/configured daily time and count clearly.
16. After a 04:00 reset, prior-day data appears in the seven-day strip.

## Deferred

- per-restriction concurrent RuntimeStore.
- simultaneous Challenges / READY / Sessions / Recoveries.
- per-restriction notification IDs and Android Notification Group.
- schedule / weekday Context.
- delayed settings weakening.
- daily-limit Hard Lock policy.
- detailed event history / abandonment rate.
- blocked-attempt stats for Full Lock.
- historical settings snapshots.
- Video target group.
- DataStore / Room.
- reboot-safe monotonic timing.
- production signing.

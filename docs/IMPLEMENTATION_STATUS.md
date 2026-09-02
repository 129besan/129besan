# v0.3-alpha1 Implementation Status

## Implemented

- One configurable Rule.
- Browser group + custom launcher-app targets.
- User-named Places.
- Place condition ALL or selected places.
- Wait / Phone Break / Walk Challenges.
- Challenge ALL / ANY.
- READY with optional timeout; default none.
- Deliberate second open.
- Explicit use/decline gate.
- “How many minutes?” prompt ON by default.
- Actual foreground-use allowance.
- Absolute Session window.
- Daily actual-use accounting.
- Daily Session budget.
- 04:00 local daily reset.
- Session-start Escalation and quiet-time decay.
- Over-limit x5 policy with min/cap and short Session.
- Recovery.
- “なぜブロックされた？” state explanation.
- Step Counter Challenge when permission/hardware are available.

## Not implemented yet

- Multiple Rules.
- Weekday/time schedule.
- Hard daily-limit mode.
- UI editing of all over-limit constants.
- Delayed weakening of settings.
- Paid rule break / Play Billing.
- Social/Video curated groups.
- Import/export.
- Persistent detailed event history.
- Statistics dashboard.
- Kotlin/Compose migration.
- DataStore/Room migration.
- Reboot-safe monotonic timing.
- Instrumentation tests.

## Alpha test questions

1. Does READY reduce accidental continuation?
2. Is “何分使う？” useful or annoying every Session?
3. Does actual-use time feel fair?
4. Is the second Session window understandable?
5. Is Recovery helpful or redundant?
6. How quickly does Escalation become annoying?
7. Does x5 over-limit friction feel meaningfully different?
8. Is Phone Break reliable across common apps?
9. Does Walk work reliably across devices?
10. Can users explain blocks via “なぜブロックされた？”?

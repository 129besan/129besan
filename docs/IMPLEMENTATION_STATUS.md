# AppLockout v0.4.3-alpha1 Implementation Status

## Implemented in v0.4.3

### Naming / Home
- Fricto -> AppLockout user-facing rename.
- Removed Home marketing/tagline text.
- applicationId remains unchanged for test-build upgrades.

### App picker
- real installed app icons;
- app names as the primary label;
- package-name search;
- category grouping;
- Browser/SNS-covered apps excluded from custom selection;
- categories derived from package rules + ApplicationInfo.category.

### Gate
- direct Activity launch over attempted target;
- removed intentional HOME-before-gate transition;
- gate Back / decline navigates HOME;
- AppLockout's own UI interactions do not reset Phone Break;
- animated mathematical Canvas graphic replaces simple breathing circle.

### Records
- removed duplicate Today card;
- 30-day actual-use bar chart;
- configured daily limit line;
- recorded-day / success-day / average metrics;
- streak computed from up to 90 days.

### Session overlay
- moved near top-right edge;
- action renamed from 離れる to ロック;
- Lock ends Session entitlement rather than merely going HOME;
- configured Recovery still follows Session end.

## Existing behavior retained

- multiple durable restriction definitions;
- target overlap rejection;
- Browser / SNS groups;
- user-named Places;
- Challenge Wait / Phone Break / Walk;
- ALL / ANY;
- READY and optional timeout;
- choose-use-duration flow;
- actual foreground Session clock;
- absolute entitlement lifetime;
- daily actual-use and Session-count limits;
- 04:00 budget reset;
- over-limit policy;
- Recovery;
- Escalation;
- Full Lock;
- notifications;
- runtime snapshot on restriction start.

## Important real-device tests

1. Open a restricted Chrome/SNS/custom app and verify the gate remains visible rather than flashing away.
2. Verify the attempted target remains immediately behind the gate.
3. Press Back / 今回はやめる and verify HOME appears, not the restricted app.
4. Phone Break must continue while the gate animation is visible; interacting with the gate itself must not restart it.
5. App picker rows show icon + name and sensible categories.
6. Search by app name and package name.
7. Session overlay sits near the top-right edge.
8. Press ロック and verify the target cannot immediately reopen under the existing Session.
9. Verify Recovery begins after Lock when configured.
10. Records has no Today card and renders the 30-day chart without clipping.
11. Verify previous v0.4.2 restrictions/Places survive upgrade.

## Remaining limitations

- normal transient runtime is still global, not per restriction;
- no simultaneous Challenge / READY / Session / Recovery;
- historical limits are not snapshotted with daily records;
- Full Lock attempt counts are not persisted;
- location freshness risk remains;
- wall-clock transient deadlines remain;
- PiP / split-screen / OEM overlay behavior needs testing;
- production Play Accessibility/background-location work remains.

# AppLockout v0.5.0-alpha2 Implementation Status

## v0.5 core rewrite

Implemented:

- interactive particle gate replacing the verbal breathing animation;
- touch/drag attraction and continuously drifting particle colors;
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
- Settings Protection/delayed weakening not implemented.
- production Play policy/signing work remains.

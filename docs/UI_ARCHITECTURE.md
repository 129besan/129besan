# AppLockout v0.5.0 UI Architecture

## Product surfaces

Primary navigation:

Home | Records | Settings

Home owns current runtime state and restriction management. Records owns long-term progress. Settings owns Android integration and reusable Places.

## Home — multiple runtime cards

v0.4 displayed one global runtime card.

v0.5 reads the active runtime IDs and renders one card per restriction.

Example:

```text
AppLockout

SNS
解除条件を進めています
スマホ休憩 あと2:15

Browser
利用する準備ができました
[利用時間を選ぶ] [今回はやめる]

YouTube
YouTubeを利用中
実際に使える時間 6:42
[利用を終了する]

制限
...
```

The ordinary restriction list remains below the transient runtime cards.

「なぜ今は使えない？」is tied to a specific restrictionId. Technical details also show that restriction's independent state and deadlines.

## Runtime navigation

READY cards launch UnlockGateActivity with an explicit restrictionId.

Notifications do the same.

The duration-decision screen therefore reads:
- snapshot for that restriction;
- daily usage for that restriction;
- pending target for that restriction.

It cannot accidentally consume another restriction's READY state.

## Entry gate

BrakeGateActivity also receives restrictionId.

Normal flow:

target attempt
→ gate for matching restriction
→ Challenge/READY state for that restriction only

AppLockout's own gate interaction remains excluded from Phone Break reset detection.

A meaningful interaction in another app resets every active Phone Break Challenge, because each of those Challenges semantically requires a phone break.

## Concurrent Sessions

Multiple Session entitlements may coexist.

Only one target can be foreground at a time, so BrowserBlockService maintains one currentForegroundRuleId.

On target switch:

1. charge elapsed foreground time to the old restriction;
2. pause old actual-use clock;
3. enter the new restriction's Session;
4. start its actual-use clock;
5. update overlay to the new restriction.

The old Session keeps its own wall-clock expiry.

## Session overlay

There is still one visible overlay because only one target is foreground.

It displays the currently consuming Session:

```text
残り 7:42   [ロック]
```

「ロック」ends only that restriction's Session entitlement. Other paused Sessions remain valid.

## Notifications

Every restriction gets a stable notification ID derived from restrictionId.

All runtime notifications use the AppLockout runtime group key.

Actions carry restrictionId:
- READY decline;
- READY duration decision;
- Session lock.

This removes cross-rule notification side effects.

## Place context

Hysteresis memory is namespaced by restrictionId.

Two restrictions can therefore use different Places/radii without one rule's previous INSIDE/OUTSIDE state contaminating the other.

Runtime uses the start-time BrowserRule snapshot. Editing a durable restriction while it is active still applies from the next Brake, except explicit pause/disable/delete which terminate that restriction's current runtime.

## App picker / Records

v0.4.3 behavior is retained:
- icon-first categorized app picker;
- search by name/package;
- Browser/SNS-covered apps excluded from custom selection;
- 30-day actual-use chart;
- daily limit reference line;
- 90-day streak horizon.

## Remaining UI work

- optionally collapse several simultaneous runtime cards into a compact summary when many are active;
- notification group summary UI if individual notifications become noisy;
- make walk-sensor unavailable states explicit;
- historical settings snapshots for Records;
- polished onboarding/presets.

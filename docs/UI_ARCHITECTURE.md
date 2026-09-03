# AppLockout v0.4.3 UI Architecture

## Product surfaces

Primary navigation remains:

Home | Records | Settings

Home owns current state and restriction management. Records owns long-term progress. Settings owns system integration and reusable Places.

## Home

The product name stands alone at the top. There is no marketing sentence beneath it.

Restriction cards retain:
- restriction name;
- status;
- up to four real target app icons + +N;
- Place;
- Challenge / Full Lock;
- daily compact progress.

## App picker

The custom-app picker is designed to be scanned visually.

Each row shows:
- installed app icon;
- app name;
- selected checkbox.

Package names are not shown in the ordinary list, although package names remain searchable.

When query is empty, apps are grouped into:
- SNS
- 動画・音楽
- ゲーム
- ブラウザ
- メッセージ
- 仕事・ツール
- その他

If Browser or SNS group selection already covers an app, that app is omitted from the custom list.

## Intervention gate

The target attempt itself is the transition into the gate.

Old flow:
target → HOME → gate

v0.4.3 flow:
target → gate

This avoids an asynchronous HOME transition winning after the Activity launch and causing a brief flash.

Backing out of the gate or choosing 「今回はやめる」 navigates HOME, so the target is never revealed underneath.

Interactions produced by AppLockout's own package are excluded from Phone Break reset detection.

## Harmonic breathing visual

The gate uses Compose Canvas to draw layered parametric curves.

Each curve varies radius with harmonic terms and phase:
- six-fold ripple;
- secondary three-fold ripple;
- slow rotation;
- breathing-dependent base radius.

Additional orbiting points and a radial glow create depth without adding a third-party rendering dependency.

## Session overlay

Overlay placement: top-right, close to the screen edge.

Contents:
- remaining actual-use time;
- ロック action.

Lock is intentionally stronger than “leave”:
- terminate Session entitlement;
- HOME;
- Recovery if configured.

Normal navigation away from the target still preserves Session entitlement and pauses the foreground clock.

## Records

Records no longer duplicates today's summary.

Each normal restriction shows:
- 30-day actual-use graph;
- daily limit reference line;
- number of recorded days;
- number of within-limit days;
- average actual use;
- current streak.

Streak calculation reads up to 90 days of local daily history.

Home remains the place to answer “what are my limits and where am I today?”

## Known boundary

Historical records currently store daily usage and Session count only. The UI evaluates old days using the restriction's current limits. A future history schema should archive the applicable policy for each day.

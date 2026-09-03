# AppLockout — Competitive Positioning Notes

Last updated: 2026-09-03

This is product-design research, not a claim that any feature is unique or patent-free.

## ScreenZen

Observed strengths:

- broad app/group restriction features;
- delays before open;
- open/session/time limits;
- increasing friction;
- strict blocking;
- schedules;
- settings protection;
- mature statistics and rule UX.

AppLockout should not try to win by duplicating ScreenZen's feature count.

Useful patterns deliberately adopted or explored:

- Full Lock.
- visible remaining-use state.
- a quick way to leave a target during permitted use.
- clearer current/configured daily limits.

## one sec

Official product/tutorial material describes intervention experiences before target access, breathing-style interaction, intervention customization, full blocking, schedules and re-interventions.

Useful references:

- https://one-sec.app/faq/
- https://one-sec.app/ja/faq/
- https://tutorials.one-sec.app/en/articles/3310978
- https://tutorials.one-sec.app/en/articles/3035202

Useful pattern deliberately adopted:

- opening a target should feel like a visible intervention, not merely a background timer or notification.

AppLockout's v0.4.2 breathing gate is an interaction pattern inspired by this category. It does not make breathing itself the restriction mechanism; the underlying Wait / Phone Break / Walk engine remains separate.

## Where AppLockout can differentiate

### 1. Context-aware friction

Reusable user-named places are first-class restriction context.

Examples:

- browser friction only at home;
- stronger social restriction at a lab or library;
- Full Lock in a selected place.

Location is not merely a schedule substitute: it allows the same app to feel different in behavioral contexts.

### 2. Several kinds of friction

A restriction can use:

- Wait: time passes while other phone use is allowed.
- Phone Break: meaningful interaction resets the break.
- Walk: physical movement.
- ALL / ANY combinations.

This makes friction something the user designs rather than one universal delay animation.

### 3. Deliberate access after earning it

The intended normal flow is:

target attempt -> intervention -> Challenge -> READY -> choose duration -> actual use -> Recovery

Challenge completion does not silently create an unlimited bypass.

### 4. Two clocks during permitted use

AppLockout separates:

- actual target foreground-use allowance;
- maximum wall-clock lifetime of the Session entitlement.

Leaving the target pauses actual-use consumption while the entitlement continues aging.

The v0.4.2 overlay makes this model visible and gives the user an immediate 「離れる」 action.

### 5. Recovery after use

Post-use Recovery is a separate mechanism from entry friction. It targets repeated app hopping and immediate relapse after a Session.

### 6. Adaptive pressure

Daily usage, Session count and short-term Escalation can change how difficult subsequent access becomes.

This is intended to become an adaptive-friction system rather than only a static blocker.

### 7. Commitment protection

The current alpha already makes weakening a restriction less immediate than strengthening it.

Future direction:

- delayed weakening;
- settings protection;
- optional explicitly opted-in monetary break;
- never surprise charging;
- retain an emergency/non-paid route.

### 8. Local-first Android implementation

Target stance:

- no account;
- no ads;
- no analytics by default;
- no cloud dependency;
- no INTERNET permission;
- explainable runtime state;
- local records.

This alone is not a moat — Nudge is an important OSS/local-first Android comparator — but it strengthens trust around Accessibility.

## Product thesis

AppLockout should not be marketed as “the strongest blocker.”

A better thesis is:

> Design the amount and kind of friction that makes impulsive use not worth it, while keeping deliberate use possible when the user has chosen that policy.

Functional shorthand:

WHO — Browser / SNS / custom targets  
WHERE — reusable places, later schedule  
HOW TO ENTER — Wait / Phone Break / Walk / Full Lock  
DELIBERATE DECISION — READY + duration choice  
HOW LONG — actual-use allowance + entitlement lifetime  
AFTER — Recovery  
HOW OFTEN — daily time / Session limits  
REPEATED ATTEMPTS — Escalation  
COMMITMENT — protected weakening

## Current gaps versus mature competitors

- no time-of-day / weekday schedule yet;
- no in-app subfeature blocking such as Reels/Shorts;
- no mature long-term analytics;
- no import/export;
- no production-grade settings lock;
- no independent concurrent restriction runtime yet;
- no Play-distribution hardening;
- no polished onboarding/presets in the current AppLockout redesign.

These should be treated as explicit gaps rather than hidden by marketing.


## v0.4.3 implications

AppLockout now adopts three mature-category conventions that should be considered baseline rather than differentiation:

- direct blocking/intervention surface over the attempted app;
- visually scannable installed-app picker with icons and categories;
- visible remaining Session time with an explicit Lock action.

The differentiation claim should therefore focus less on “we also block apps” and more on the combined policy model: Place-aware restrictions, multiple Challenge types, READY duration choice, actual-use accounting, Recovery, daily limits, Escalation and commitment protection.

The Records direction also shifts from a settings/status dump toward long-term adherence: a 30-day actual-use trend plus streak. This should eventually be complemented by event-level metrics such as blocked attempts and Challenge abandonment.

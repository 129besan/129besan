# Browser Brake — Product & Runtime Design

Last updated: 2026-09-03

## Product premise

Browser Brake is not primarily a hard app blocker. Its goal is to interrupt reflexive access while preserving deliberate access when the user genuinely needs it.

> Access remains possible, but impulsive access should become disproportionately inconvenient.

The same mechanism should support light-friction users and users who intentionally want a strong behavioral break.

## Rule model

Long-term model:

```text
Rule
├── TARGET      what is braked
├── CONTEXT     where / when the rule applies
├── ENTRY BRAKE what must happen before access
├── SESSION     how much access is granted
├── RECOVERY    what happens after use
└── POLICY      daily limits, escalation, commitment protection
```

v0.3-alpha validates one primary Rule before multiple Rules are introduced.

### TARGET

- Browser group: known browser packages + Android APP_BROWSER handlers.
- Custom apps: user-selected visible launcher apps.
- Future: suggested Social / Video groups, with explicit user confirmation.

### CONTEXT

Place choices:

- `ALL`: active everywhere.
- selected places: active inside any user-selected place.

Place names are completely user-defined. Browser Brake does not hard-code labels such as “research lab”.

Future dimensions: schedule and weekday. Within one dimension conditions are OR; across dimensions they are AND.

## 解除条件 / Challenge

UI name: **解除条件**  
Internal name: **Challenge**

The challenge set is extensible.

### Wait

Time passes even while other phone activity continues.

### Phone Break

Meaningful interaction such as click, scroll and text editing/selection resets the configured timer.

This is not claimed to detect every physical touch. Android does not provide the passive raw-touch model initially assumed in v0.2.

### Walk

Uses the Android cumulative step counter and measures a delta from the Challenge baseline.

### ALL / ANY

When several Challenges are enabled:

- ALL: every enabled condition must complete.
- ANY: any enabled condition is sufficient.

Escalation never adds a Challenge type the user did not select.

## Runtime state machine

```text
LOCKED
  target open
    ↓
CHALLENGING
  condition success
    ↓
READY
  deliberate re-open + explicit confirmation
    ↓
SESSION
  usage allowance or validity window expires
    ↓
RECOVERY
  recovery expires
    ↓
LOCKED
```

When the Rule context is not active, the restriction is effectively inactive.

### READY

Challenge success is **not** automatic unlock.

The user sees:

> 解除条件を達成しました。本当に必要なら対象アプリをもう一度開いてください。

On the second deliberate open:

- 利用する
- 今回はやめる

Default READY timeout is **none**. An optional timeout can be configured for users who want the qualification to expire.

## Session

Before a Session starts, Browser Brake asks **「今回は何分使いますか？」** by default.

This creates an explicit estimate of needed usage without the burden of writing an intent.

Two clocks are used.

### Usage clock

Counts actual foreground time of the target app/group.

```text
10 min allowance
Chrome 3 min -> 7 min
Notes 8 min  -> still 7 min
Chrome 4 min -> 3 min
```

### Window clock

Caps the wall-clock lifetime of the Session.

```text
10 min actual target usage
OR
30 min from Session start
whichever ends first
```

This prevents small unused allowances from remaining valid indefinitely.

Daily time is charged by actual target foreground usage, not by the Session amount selected in advance.

## Daily limits

Two separate limits represent different behaviors.

### Daily usage time

Controls total consumption.

### Daily Session count / Budget

Controls repeated returns.

30 minutes twice and 5 minutes 12 times have equal total duration but very different repetition patterns.

Both can be unlimited. A Session is counted only on READY -> SESSION.

Prototype daily reset: 04:00 local time.

## Escalation

Escalation targets short-term repetition.

- Starting a real Session increments the level.
- Repeated attempts inside the same Challenge do not increase the level.
- Any target attempt resets the quiet-time decay clock.
- Long periods with no target attempts gradually reduce the level.

Prototype Standard:

```text
L0 1.0x
L1 1.5x
L2 2.5x
L3 3.5x
L4 5.0x
decay: 90 min / level
```

Prototype Strong:

```text
L0 1.0x
L1 2.0x
L2 3.5x
L3 5.0x
L4 7.0x
decay: 3 h / level
```

These are product-test values, not scientifically optimal constants.

## 「今日の上限を超えています」

Daily-limit overflow is more serious than normal Escalation: the user has already crossed a self-defined daily promise.

Alpha default:

```text
Challenge multiplier: x5
time-based Challenge minimum: 10 min
time-based Challenge maximum: 30 min
maximum Session after limit: 3 min
```

The goal is to preserve emergency access while making casual continued use unattractive.

Future policies:

- Hard limit until daily reset.
- Configurable over-limit severity.
- Optional paid commitment break.

A paid break should never be the only emergency route and should require explicit opt-in.

## Recovery

Recovery is a post-use refractory period.

- Entry Brake = cost before access.
- Session = permitted use.
- Recovery = time before another attempt can begin.

Opening a target during Recovery does not start a new Challenge; it shows the remaining Recovery time.

## 「なぜブロックされた？」

A sophisticated Rule engine is unusable if behavior cannot be explained.

The app should expose:

- active Rule
- runtime state
- context/place
- daily usage
- daily Session count
- Escalation level
- over-limit status
- relevant remaining time

This is a product feature, not merely debug UI.

## Settings protection — planned

Proposed commitment behavior:

- stronger changes: immediate
- weaker changes: delayed

Examples of weakening include shorter Challenges, larger daily limits, longer Sessions, removing targets, disabling Escalation, or disabling the Rule.

Browser Brake remains a self-commitment tool; uninstalling or disabling Accessibility ultimately remains possible.

## Multiple Rules — planned

v0.3 intentionally validates one Rule first.

Initial v1 conflict policy should forbid assigning the same target package to multiple Rules. Priority resolution should be introduced only if real use requires it.

## Privacy

Desired stance:

- local-only
- no account
- no ads
- no analytics by default
- no INTERNET permission

Accessibility uses only events needed for target state and Phone Break, with `canRetrieveWindowContent=false`.

Location is isolated as a Context feature because background-location policy is a significant Google Play distribution concern.

## Product-validation metrics

Screen time alone is insufficient.

Future local event metrics:

- Brake attempts
- Challenge started / abandoned / completed
- READY declined / expired
- Session started
- actual usage
- Recovery hits
- over-limit attempts
- Rule weakened / disabled

Two key metrics:

1. **Brake abandonment rate** — target attempts that ultimately do not produce a Session.
2. **Rule retention** — whether users keep the Rule active over days/weeks.

Very high abandonment with low retention likely means the Brake is too punishing. Very low abandonment likely means it is too weak.

## Competitive design rationale

Observed patterns that informed the design:

- ScreenZen: pre-open delay, open limits, escalating waits, strict/settings protection.
- one sec: interventions before access and repeated intervention patterns.
- Zentime: choose use duration before access and post-use breaks.
- Nudge: OSS/local-first, delay, budgets, groups and “walked away” metrics.
- LockIn / Digital Detox: commitment-oriented early-unlock models.

Browser Brake should not compete on feature count. Its intended position is:

> configurable, context-aware adaptive friction with an explicit READY decision boundary.

## Known alpha architecture debt

- Transient deadlines currently use wall-clock timestamps; manual clock changes can affect them.
- Robust reboot reconciliation is not implemented.
- UI uses platform Java widgets rather than the intended eventual Kotlin/Compose architecture.
- SharedPreferences is used instead of DataStore/Room.
- Background-location release strategy remains unresolved.

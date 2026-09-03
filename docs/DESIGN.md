# AppLockout — Product & Runtime Design

Last updated: 2026-09-03

## Product premise

AppLockout is not primarily a hard app blocker. Its goal is to interrupt reflexive access while preserving deliberate access when the user genuinely needs it.

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

v0.4-alpha supports multiple durable Rule definitions. The runtime intentionally allows only one transient Brake episode (Challenge / READY / Session / Recovery) at a time.

### TARGET

- Browser group: known browser packages + Android APP_BROWSER handlers.
- SNS group: a conservative curated set (X, Instagram, Reddit, Threads, Bluesky, Facebook, Mastodon).
- Custom apps: user-selected visible launcher apps.
- Future: Video group and richer user-editable group presets.

### CONTEXT

Place choices:

- `ALL`: active everywhere.
- selected places: active inside any user-selected place.

Place names are completely user-defined. AppLockout does not hard-code labels such as “research lab”.

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

> 解除条件を達成しました。今回は何分使うか決めてください。

After completion, the READY notification and Home READY card both open the same decision screen:

- 5 / 10 / 15 minutes (or the Rule's available amount)
- 今回はやめる

There is no extra “利用する” confirmation. The notification is an entry point; the AppLockout READY screen is the decision surface.

Default READY timeout is **none**. An optional timeout can be configured for users who want the qualification to expire.

## Session

Before a Session starts, AppLockout asks **「今回は何分使いますか？」** by default.

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

AppLockout remains a self-commitment tool; uninstalling or disabling Accessibility ultimately remains possible.

## Multiple Rules

v0.4 implements multiple Rule definitions.

The conflict policy is deliberately simple:

```text
one target package/group -> one enabled Rule
```

Browser/SNS group overlap and individual-package overlap are rejected. A disabled Rule may temporarily overlap while being edited, but cannot be re-enabled until the conflict is resolved.

Only one transient Brake episode runs at a time. If a target belonging to another Rule is opened while a Challenge / READY / Session / Recovery is active, it is blocked until the current episode finishes. This avoids hidden priority/merge semantics in the first multi-Rule version.

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

AppLockout should not compete on feature count. Its intended position is:

> configurable, context-aware adaptive friction with an explicit READY decision boundary.

## Known alpha architecture debt

- Transient deadlines currently use wall-clock timestamps; manual clock changes can affect them.
- Robust reboot reconciliation is not implemented.
- The durable UI is now Kotlin + Jetpack Compose + Material 3, while the audited runtime remains largely Java during migration.
- SharedPreferences/JSON is still used instead of DataStore/Room.
- Background-location release strategy remains unresolved.


## Runtime accounting clarification (alpha3)

Foreground usage is inferred conservatively from Accessibility events:

- target-origin events are positive evidence that a target is active;
- only `TYPE_WINDOW_STATE_CHANGED` from a non-target window may end target accounting;
- `TYPE_WINDOWS_CHANGED` is not treated as a foreground transition;
- System UI and the active input method are treated as transient overlays.

Recovery is tied to the end of the last actual target-use interval. If a Session remains open in the background until its wall-clock window expires, time already spent away from the target counts toward Recovery. A late Session-window expiry therefore does not create a fresh, surprising Recovery period.


## Remaining runtime risks after alpha3 audit

The alpha3 audit deliberately separates fixed bugs from unresolved platform questions.

### Wall-clock deadlines

Challenge, READY, Session-window and Recovery deadlines still use `System.currentTimeMillis()`. This is simple across process restarts but is susceptible to manual clock changes. Production should use a monotonic in-boot clock plus an explicit reboot policy.

### Location freshness

A place Rule currently relies on passive/last-known Android locations. Hysteresis protects boundary jitter, but a stale last location can still misclassify context. Production needs an explicit freshness policy and one-shot refresh behavior.

### Walk availability

Walk depends on Activity Recognition permission and a step-counter sensor. A missing permission/sensor currently prevents completion rather than silently weakening the Rule. The UI still needs a clear unavailable-state.

### AccessibilityService restart

If the service reconnects during an active Session, AppLockout pauses foreground accounting instead of assuming the target remained visible. This favors not overcharging the user, but it can undercount usage until a new target event arrives.

### Foreground inference

Foreground inference intentionally avoids `TYPE_WINDOWS_CHANGED`. Target-origin events confirm target use, while non-target `TYPE_WINDOW_STATE_CHANGED` events end it, except for System UI and the active IME. This is more robust than alpha2 but still requires device-level testing across OEMs, PiP and split-screen.


## v0.4 information architecture

v0.4 separates the product into four top-level destinations:

- Home: runtime state and the next action.
- Rules: create/manage Rules and their status.
- Records: per-Rule daily usage.
- Settings: permissions, reusable Places and privacy.

The Rule editor shows semantic categories instead of exposing all controls simultaneously. See [UI_ARCHITECTURE.md](UI_ARCHITECTURE.md).


## v0.4.2 product update

User-facing terminology changed from Rule to 「制限」. Internal Rule naming remains during migration.

A restriction now has two top-level methods:

- normal: intervention + Challenge + READY + Session + Recovery;
- Full Lock: when Context is active, the target cannot be opened.

The normal entry experience is now an animated AppLockout gate. The breathing animation is presentation-level friction; Wait / Phone Break / Walk remain the actual configurable Challenge semantics.

During a Session, a compact Accessibility overlay exposes remaining actual-use allowance and a one-tap 「離れる」 action. Leaving pauses actual-use consumption without destroying the remaining Session entitlement.

Home now owns today's status and restriction editing. Records owns the seven-day view and streak. Raw Escalation levels are no longer presented as a primary user metric.

AppLockout's intended differentiation is not feature count. The product thesis is context-aware adaptive friction: WHO / WHERE / ENTRY / DELIBERATE DECISION / USE / RECOVERY / DAILY LIMIT / ESCALATION / COMMITMENT.


## v0.4.3 interaction clarification

The entry gate should appear directly over the attempted target. HOME is an exit destination, not an intermediate step. This avoids platform timing races and gives the user a clear causal model: “I opened this app, therefore this restriction appeared.”

The intervention graphic is generated from animated harmonic polar curves in Compose Canvas. The graphic is intentionally ornamental; Challenge semantics remain independent.

Session overlay semantics were also tightened. Navigation away from a target is not the same as abandoning the granted Session. Therefore ordinary app-switching pauses actual-use time. The explicit overlay action is now 「ロック」 and terminates the current Session entitlement.

Records is trend-oriented rather than a duplicate status dashboard. Today/current-limit information belongs on Home; Records uses 30-day actual-use history and a longer streak horizon.

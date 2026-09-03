# Fricto v0.4.2 UI Architecture

## Goal

Fricto should expose a sophisticated restriction engine without feeling like a settings utility.

The v0.4.2 information architecture separates three questions:

- Home — what is happening now, and what restrictions exist?
- Records — how has the last week gone?
- Settings — is Android integration healthy, and what reusable places exist?

## Primary navigation

Home | Records | Settings

The former separate Rules tab was removed because it duplicated Home. Creating a restriction is now the Home floating action button.

## Home

Home contains:

1. Fricto identity and short premise.
2. Current runtime card.
3. 「なぜ今は使えない？」when relevant.
4. Restriction cards.
5. Floating + action.

### Restriction card

Cards use actual installed app icons instead of a generic rule glyph. At most four icons are rendered and remaining targets become +N.

A normal card shows restriction name, status, target icons, place, Challenge, per-use maximum and today's compact progress.

A Full Lock card hides irrelevant Session / daily usage details and clearly says 完全ロック.

## Restriction editor

User-facing terminology is 「制限」. Internal code may continue to use BrowserRule / RuleRepository during migration.

Top-level entries:

1. 対象
2. 有効にする場所
3. 制限方法
4. 利用 — normal mode only
5. 1日の上限 — normal mode only
6. 利用後 — normal mode only
7. 繰り返し利用 — normal mode only
8. 制限を管理

### Restriction method

Two modes:

- 解除条件あり
- 完全ロック

Full Lock hides options that have no effect on it.

### Weakening restrictions

Weakening remains intentionally deeper than ordinary editing:

Home → restriction card → editor → 制限を管理

Pause requires confirmation. Disable/delete require typing the restriction name. Enabling/resuming remains easy because it strengthens the commitment.

## Breathing gate

A target-open attempt in normal mode opens a Fricto intervention surface.

The gate deliberately differs from ordinary settings UI:

- strong blue gradient
- breathing circle
- inhale / exhale rhythm
- live Challenge state
- READY decision
- explicit decline

The actual Challenge remains owned by runtime state, so leaving the gate does not destroy Wait / Phone Break / Walk progress.

## Session overlay

When a target is actively consuming Session time, Fricto renders a compact Accessibility overlay:

残り 7:42   [離れる]

「離れる」charges foreground time consumed so far, sends the user Home, hides the overlay and preserves the Session entitlement until its wall-clock deadline.

The overlay must disappear on screen-off, target leave, Session end, Context exit, restriction weakening and service destruction.

## Records

Home owns today's compact state. Records owns historical meaning.

Per normal restriction:

- current / configured daily usage time
- current / configured daily Session count
- remaining amount
- seven-day goal strip
- current streak

The old raw Escalation Level N display is not a normal user-facing metric.

### Seven-day state

- blue check: data exists and current goal criteria are met
- error mark: data exists and criteria were exceeded
- neutral dot: no archived data

The streak badge has a subtle pulse, stronger for a longer streak. This is intentionally light gamification rather than a punitive score.

## Visual system

Fricto uses a branded blue / cobalt / indigo palette rather than delegating identity entirely to Android Dynamic Color.

- background: near-white blue
- primary: cobalt
- READY / Session: lighter blue variants
- Recovery: indigo
- errors / conflicts: red only when necessary

Top-level screens use a subtle vertical gradient; intervention surfaces can use a stronger blue gradient.

## Runtime snapshot

When a normal restriction starts a Brake episode, its durable definition is snapshotted into runtime configuration.

Edits made during Challenge / READY / Session / Recovery apply to the next Brake. Explicit runtime actions such as pause/disable terminate an active episode immediately.

## Known v0.4.2 boundary

Normal restrictions still share one transient state machine globally. Full Lock is stateless per attempt and does not need a long-lived episode.

The next runtime architecture should store state independently for each restriction, including Challenge / READY / Session / Recovery and notifications.

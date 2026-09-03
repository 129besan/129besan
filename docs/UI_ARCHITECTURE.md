# Browser Brake v0.4 UI Architecture

## Goal

v0.3 proved the behavioral components but presented them as one long developer-oriented settings screen.

v0.4 separates **daily use**, **Rule management**, **records**, and **system configuration**.

The user should understand what Browser Brake will do without learning internal terms such as state-machine names or preference keys.

## Primary navigation

```text
Home | Rules | Records | Settings
```

Four destinations fit a compact Android phone navigation bar and keep configuration separate from runtime status.

## Home

Home answers:

1. Is Browser Brake doing something right now?
2. Which Rule is responsible?
3. What do I need to do next?
4. If READY, how do I intentionally start a Session?

It does not expose raw sliders or permission setup.

When READY, the high-priority card offers:

- 利用時間を選ぶ
- 今回はやめる

## Rules

The Rule list is the main configuration surface.

A card summarizes:

- name;
- target groups;
- place context;
- Entry Brake;
- maximum Session;
- daily limits;
- status.

The whole card opens editing.

The status chip opens Rule control. It is intentionally not a one-tap switch.

### Status actions

Enabled:

- pause 15 min;
- pause 1 h;
- disable with confirmation.

Paused:

- resume.

Disabled:

- re-enable if no target conflict exists.

Future Settings Protection should replace immediate permanent weakening with delayed application.

## Rule editor

The top-level editor contains seven semantic entries:

1. 対象
2. 有効にする場所
3. 開く前
4. 利用
5. 1日の上限
6. 利用後
7. 繰り返し利用

Each entry opens a dedicated sub-screen.

This keeps advanced configurability without showing dozens of controls simultaneously.

## Target editor

Target selection is group-first:

- Browsers
- SNS
- その他のアプリ

Groups are convenience layers, not irreversible classifications.

When a group is selected, packages already covered by that group are removed from the individual-app list.

The custom picker is searchable and only contains launcher-visible apps. Browser Brake does not request broad `QUERY_ALL_PACKAGES`.

## Multiple Rule conflict policy

v0.4 deliberately avoids priority systems.

```text
one target package/group -> one enabled Rule
```

Saving an overlapping enabled Rule is blocked.

A disabled Rule may be edited into an overlap, but cannot later be enabled until the conflict is resolved.

This makes runtime behavior explainable.

## READY

READY is a deliberate decision boundary.

The notification is only an entry point.

```text
解除条件を達成
  ↓
利用時間を選ぶ
  ↓
5 / 10 / 15 min
or 今回はやめる
```

No redundant “利用する” button exists before duration selection.

Home exposes the same READY action so the flow does not depend on notification persistence.

## Records

v0.4 records only simple per-Rule daily values:

- actual use;
- Session count;
- stored Escalation level.

Future versions should add event-level local history:

- Brake attempt;
- Challenge abandonment;
- READY decline;
- Session;
- Recovery hit;
- over-limit attempt.

## Settings

Settings contains system-wide concerns:

- Accessibility health;
- notification permission;
- location permission;
- Activity Recognition permission;
- reusable Places;
- privacy statement;
- Android app settings.

Rule-specific policy does not belong here.

## Visual direction

Browser Brake should feel like a calm utility, not a punishment or “digital detox” game.

- Material 3;
- spacious layout;
- cards for semantic groups;
- short Japanese labels;
- dynamic system colors;
- strong hierarchy;
- minimal warning colors except for actual conflicts/limits;
- no decorative analytics or gamification by default.

## Runtime/UI boundary

The Compose UI stores durable Rule definitions.

The existing audited runtime still owns transient state:

```text
LOCKED -> CHALLENGING -> READY -> SESSION -> RECOVERY
```

When a target is opened while LOCKED:

1. find enabled Rule matching the package;
2. snapshot that Rule into runtime configuration;
3. set active Rule id;
4. evaluate its Place context;
5. start its Challenge.

Daily usage and Escalation are stored with Rule-id-prefixed keys.

Only one transient episode is active across the app in v0.4-alpha1.

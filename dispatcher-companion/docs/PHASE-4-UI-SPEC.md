# Phase 4: UI/UX Specification — Concept B "Freight Dispatcher Pro" (approved)

Mockups: `ui-concepts/concept-b.png` · Approved by product owner 2026-06-12.

## Design Tokens (Compose theme)

| Token | Value | Use |
|---|---|---|
| `Navy900` | `#15233B` | text on light |
| `Navy800` | `#1B2A4A` | app bar, primary surfaces |
| `Steel100` | `#E9EDF2` | screen background |
| `Steel200` | `#DDE4EC` | inset surfaces |
| `White` | `#FFFFFF` | cards |
| `Border` | `#C3CDD9` | card outlines (2dp) |
| `Orange500` | `#F97316` | money, primary action, overlay border |
| `OrangeDim` | `#FDE8D8` | chips, P/D/W/C/R tags |
| `Green700` | `#15803D` | favorable / OK status |
| `Amber600` | `#C2700C` | warnings / Limited |
| `Red700` | `#B91C1C` | broker lines / STOP / Fallback |
| `Blue700` | `#1D4ED8` | dispatcher lines |
| Radius | 10dp cards, 12dp buttons | square-ish industrial look |
| Type | Roboto; money/figures in `RobotoMono Bold` | tabular numbers |

Dark variant (auto, night): bg `#10182A`, cards `#16223A`, text `#E3EAF4` —
same accents. (Addresses Concept B's "long session" con.)

## Screens (single-activity Compose, Navigation)

1. **Home / Start** — giant `START DISPATCH MODE` button (Orange500, 96dp),
   capture-status chip, last-3 calls list, bottom nav: Home · Brokers · Calc ·
   History · Settings.
2. **Live Dashboard** (during call) — exactly the concept-B stack: PDWCR card
   (P/D/W/C/R tag chips, value, confidence dot, tap-to-edit) → Negotiation card
   (counter headline in mono, floor/ceiling/accept%, suggested reply) →
   Live Transcript card (Broker red / Dispatcher blue, auto-scroll, REC timer) →
   metrics strip (RPM · MILES · DEADHEAD · PROFIT) → red `STOP DISPATCH MODE`.
   Risk alerts appear as an Amber600 banner above the PDWCR card.
3. **Floating overlay** — 320×~520dp panel, orange 2dp border, drag handle,
   compact PDWCR, counter + floor/accept, last transcript line, buttons
   `NOTES / CALC / EXPAND`; snaps to screen edges; collapses to a 56dp orange
   bubble showing the current counter (e.g. "$2.1k") on tap-away.
4. **Call Summary** — outcome selector (BOOKED/REJECTED/FOLLOW-UP), lane header,
   PDWCR recap, 4 note styles in tabs, export row (TXT · CSV · PDF · Copy ·
   Email · Share), follow-up actions checklist.
5. **Broker Memory** — search field (FTS), list (company, MC, avg RPM,
   success-rate bar), profile screen with rate/lane history and style notes.
6. **Calculator** — lane input (city/ZIP ↔ city/ZIP), instant miles/RPM/fuel/
   profit/profitability score; reachable mid-call from overlay.
7. **Setup Wizard** — one card per permission (mic, notifications, listener,
   accessibility, overlay, autostart, battery), live granted/denied LED, a
   `FIX` button deep-linking to the exact HyperOS settings page.

## Interaction rules

- Overlay never overlaps the bottom 25% of the screen by default (RingCentral
  call controls live there); snap zones exclude it.
- All PDWCR fields editable in ≤ 2 taps; manual edits show a lock glyph.
- One-handed reach: primary actions in bottom 40% of screen.
- Status chip states: EXCELLENT (Green) · GOOD (Green outline) · LIMITED (Amber)
  · FALLBACK (Red) — always visible on dashboard + overlay.

## Phase-4 test gate

Static design review against FR list: every FR-surface (capture status FR-202,
PDWCR FR-401, copilot FR-502, calculator FR-601, wizard FR-1000, export FR-803)
has a designated screen/region above. Contrast: all token pairs used for text
meet WCAG AA (≥ 4.5:1) — verified: Navy900/White 13.9:1, Orange500/Navy800 4.6:1
(large text only — used at ≥18sp), White/Navy800 12.6:1, Green700/White 5.1:1,
Red700/White 6.5:1, Amber600/White 4.8:1.

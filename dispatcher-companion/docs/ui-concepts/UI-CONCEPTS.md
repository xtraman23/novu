# RingCentral Dispatcher Companion — UI Concepts (Phase 4 preview, pre-approval)

Five professional concepts. Each PNG (`concept-a.png` … `concept-e.png`) shows
the two screens that matter most: the **Live Call Dashboard** and the
**Floating Copilot overlay** rendered over a simulated RingCentral call.
Mockups are regenerable via `python3 generate_mockups.py`.

All concepts share the same information architecture (so the choice is purely
visual/ergonomic):

```
App screens:  Home/Start  →  Live Call Dashboard  →  Call Summary
              Broker Memory · Freight Calculator · History · Settings/Setup Wizard
Overlay:      compact PDWCR + counter-offer + last transcript line + quick actions
```

**Shared user flow**

1. Open app → big **START DISPATCH MODE** button (or it auto-arms when a
   RingCentral call is detected).
2. Call starts → floating copilot appears over RingCentral; capture-status chip
   shows Excellent/Good/Limited/Fallback.
3. During call → PDWCR fills in live, transcript streams, counter-offer +
   floor/ceiling/accept% update; tap **EXPAND** to jump to the full dashboard,
   **CALC** for lane math, **NOTES** to pin a note.
4. Call ends → summary screen (broker, lane, rate, outcome, follow-ups) with
   one-tap export (TXT/CSV/PDF/clipboard/email/share).
5. Broker profile auto-updated; searchable from Broker Memory.

---

## Concept A — Modern SaaS  (`concept-a.png`)

Light theme, indigo accent, soft rounded cards, generous spacing — the look of
Linear/Stripe-era SaaS tools.

**Layout:** card stack — PDWCR card → Negotiation card (big counter) →
Transcript card → metrics strip → stop button. Overlay is a white rounded
panel with indigo border.

**Pros**
- Familiar, polished, trustworthy; easiest to demo/sell
- Light surfaces read well outdoors and in bright cabs
- Soft hierarchy keeps the dense data approachable for new users

**Cons**
- Bright white is harsh on night drives
- Spacious cards waste vertical pixels on a 6.88" 720p screen (Redmi 14C)
- The least "trucking" personality of the five

## Concept B — Freight Dispatcher Pro  (`concept-b.png`)

Industry workhorse: navy header, safety-orange accent, square corners, denser
rows. Styled like TMS/load-board software dispatchers already know.

**Layout:** same stack as A but tighter rhythm, navy app bar, orange used only
for money/actions. Overlay has an orange border for instant find-ability.

**Pros**
- Looks native to the freight world (DAT/Truckstop adjacent) — instant credibility
- Orange-on-navy makes the rate/counter the loudest pixel on screen
- Dense rows fit more of PDWCR + extended fields without scrolling

**Cons**
- Visually dated next to A; less appealing in marketing screenshots
- Two strong brand colors (navy/orange) limit status-color vocabulary
- Light body + dark header mix can feel disjointed on long sessions

## Concept C — Dark Trading Terminal  (`concept-c.png`)

Near-black, monospace, green/red money semantics — a Bloomberg-style
negotiation terminal.

**Layout:** flat dark panels with hairline borders; every number monospaced and
column-aligned; green = favorable, red = broker pressure. Overlay is a dark
panel with green border.

**Pros**
- Best glanceability of numbers (fixed-width digits never shift)
- Excellent at night, lowest OLED-ish power draw, low glare in trucks
- "Trader" framing matches the negotiation-copilot mental model

**Cons**
- Monospace eats horizontal space → harder to fit transcript on 720p width
- Intimidating to non-technical dispatchers; weakest onboarding feel
- Red/green semantics fail for color-blind users without extra encoding

## Concept D — Operations Command Center  (`concept-d.png`)

Dark navy mission-control: cyan panel headers, status LEDs, structured panels —
between A's polish and C's intensity.

**Layout:** same stack with cyan section labels and LED-style confidence dots;
overlay matches the dashboard so it feels like a detached panel of one system.

**Pros**
- Dark but friendly; best balance of long-session comfort and approachability
- Cyan headers give strong scannability across many panels (scales to Phase 9 broker-intel widgets)
- Status-LED language naturally extends to capture status / risk alerts

**Cons**
- Cyan-on-navy contrast must be managed carefully on a budget LCD panel
- A "themed" look that needs discipline to avoid sci-fi kitsch
- Slightly heavier visual chrome than B/E at equal information density

## Concept E — Ultra Minimal Fast Workflow  (`concept-e.png`)

White, black, one green accent. The rate on the table is a 64sp headline; PDWCR
is five plain rows; one giant button. Zero decoration.

**Layout:** headline rate + counter → PDWCR as ruled rows → live snippet →
full-width START/STOP button. Overlay is the same, miniaturized.

**Pros**
- Fastest possible glance-and-talk loop — nothing competes with the numbers
- Best performance/battery headroom on the Helio G81 (minimal overdraw, no elevation)
- Trivial to keep consistent; smallest UI code surface = fewer Phase 10 test cases

**Cons**
- No room for negotiation context (floor/ceiling/probability get tiny)
- Bright white at night, same as A
- Sparse look can read as "unfinished" to buyers comparing against TMS suites

---

## Recommendation

**Concept D (Operations Command Center)** as the base, borrowing **E's**
one-tap start screen and headline-sized counter-offer. Rationale: dispatchers
run this during multi-hour shifts (dark theme comfort), the LED/status language
maps directly onto capture quality and risk alerts we must surface anyway, and
it scales to the broker-intelligence panels coming in Phase 9. Concept B is the
runner-up if industry familiarity outweighs aesthetics.

**Awaiting your selection before any UI implementation (per project brief).**
You can also mix (e.g., "D layout + C money colors").

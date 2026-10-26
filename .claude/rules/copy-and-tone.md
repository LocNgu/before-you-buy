---
description: Tone rules for every user-facing string and notification
paths:
  - "**/res/values*/strings.xml"
  - "**/res/values*/plurals.xml"
  - "**/notifications/**/*"
  - "docs/store-listing.md"
---

# Copy and tone

- Calm and neutral. The app helps people decide; it never pressures, shames or celebrates.
- **Never:** "you have money available", "you can afford", "you saved!", streaks, badges, urgency words ("only", "hurry", "last chance"), deal/price language (ADR-0012, ADR-0014).
- Name the alternative concretely on decision surfaces: "Don't buy — keep 1,200 € for your Camera (#2)" beats "Cancel" (ADR-0014 context, research §1).
- "Decided against", not "saved" or "resisted".
- Every string exists in `values/` (English) and `values-de/` (German). German uses "du". Terms: Wartezeit (cooling-off), Wunschliste, Prioritäten, Dagegen entschieden, Gekauft, Verlauf (history), Spaßgeld (fun money, ADR-0045), zurückgelegt (set aside) — confirm or extend in issue #29.
- Fun money labels (ADR-0045): "Fun money for wants", "Set aside for wants", "Not yet set aside". Never "available", "spend" or "you can afford".
- Priorities (ADR-0047): "Priorities" / „Prioritäten“, "Make priority" / „Zur Priorität machen“, "Remove from priorities", ranks "#1" (German summary: „auf Platz 2“). The head-to-head asks "Which would you rather have?" / „Was hättest du lieber?“; both answers are the item names, equal. Summaries state what changed, never "won"/"lost".
- Opportunity cost (ADR-0048): "What else this money could do" / „Was du sonst mit dem Geld machen könntest“; plain facts ("4 months of your fun money", "75% of the price of your Bike (#1)", "Buying this delays your Bike (#1) by about 3 months", "Your 3 cheapest wishlist items come to 1,148 € together"). Never "afford", "enough for" or "could buy".
- Savings per item (ADR-0046): "650 € of 1,600 € set aside · 950 € to go" / „650 € von 1.600 € zurückgelegt · noch 950 €“; buttons "Set aside" / "Take back" („Zurücklegen“ / „Zurücknehmen“). Never "saved"; nothing celebrates a full bar.

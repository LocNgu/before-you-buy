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
- Every string exists in `values/` (English) and `values-de/` (German). German uses "du". Terms: Wartezeit (cooling-off), Wunschliste, Prioritäten, Dagegen entschieden — confirm or extend in issue #29.

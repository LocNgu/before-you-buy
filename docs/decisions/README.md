# Architecture Decision Records

Every significant product or technical decision gets a short, numbered, immutable record here.

- `product/` — UX and behavioural decisions (defaults, wording, what a gesture does).
- `technical/` — implementation and framework constraints (patterns, libraries, data-model rules).

## Rules

1. **Consult before working** in an area an ADR covers. Never refactor away a pattern a technical ADR
   describes without a superseding decision.
2. **Write one** when a PR makes a decision that would shape how a future implementer approaches the same
   area — a new default, a chosen framework or pattern, a non-obvious behavioural rule. Routine bug fixes
   and mechanical changes don't need one.
3. **Copy `template.md`**, number it sequentially *within its folder* (`0001-…`, `0002-…`), and set
   **Status** to `accepted`.
4. **Finalized ADRs are immutable.** The only permitted edit is the Status line →
   `superseded by [ADR-XXXX](file.md)`. Write a new ADR instead of rewriting an old one.
5. **If a request contradicts an ADR**, name the ADR and its rationale and get human confirmation before
   proceeding.

Keep them short — context, decision, consequences. A record nobody reads is worse than none.

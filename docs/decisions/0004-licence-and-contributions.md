# ADR-0004: PolyForm Noncommercial; brand reserved; no outside code contributions

**Status**: accepted
**Type**: product
**Date**: 2026-10-04

## Context

The owner wants the code public and usable for free, forks allowed, but only the owner may commercialize it. Open source licences (MIT, Apache, GPL, AGPL) all allow commercial use. FSL and BSL convert to open source after 2–4 years. PolyForm Shield allows non-competing commercial use. Creative Commons NC is not designed for software. Code contributed by others under a non-commercial licence could not be used in the paid official app without a CLA.

## Decision

- Code: PolyForm Noncommercial 1.0.0. Forks may be shared but stay non-commercial.
- Name, logo, icon, screenshots and store assets are reserved (`TRADEMARKS.md`).
- No code contributions from others for now; issues and ideas welcome (`CONTRIBUTING.md`).
- Describe the project as "source available", never "open source".
- Only permissively licensed dependencies (Apache-2.0, MIT, BSD).

## Consequences

- Not eligible for F-Droid. Channels are Google Play and build-from-source.
- Opening contributions later requires a CLA first.

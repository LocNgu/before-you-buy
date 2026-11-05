# ADR-0055: Backup: one zip (JSON + photos), replace-all import checked before it touches anything, Auto Backup of the database, photos and preferences

**Status**: accepted
**Type**: technical
**Date**: 2026-11-05

## Context

#27 adds export/import (brief §9) and switches system backup on (it was off since the scaffold). The data is relational (items, reflections, decisions, notification log, allowance, fun money, allocations, policy), photos are files named by `items.image_ref` (ADR-0038), and the app has no `INTERNET` permission (ADR-0003). The issue left open: JSON only or JSON plus images.

Alternatives considered:

- **JSON only**: photos would be lost on every move to a new phone; the photos are the only thing that can't be re-typed.
- **Photos as base64 inside the JSON**: one file, but 33 % larger, and the whole file in memory twice.
- **Copying the Room database file**: ties the file format to the schema version and column names, can't be validated before it replaces the live database, and can't be checked for a newer version without opening it.
- **Merge on import**: ids, append-only ledgers and unique accrual months make a correct merge a project of its own; out of scope in the issue.

## Decision

- **Format:** a zip with `backup.json` and, for every item photo, `images/<ref>.jpg`, written by `java.util.zip` (no dependency). The JSON has `formatVersion` (1), `exportedAt`, `appVersion`, the settings that belong to the user (the two notification switches, onboarding done) and every table's rows. DTOs are `kotlinx.serialization` classes of their own (`BackupDto`), not the Room entities, so a column rename never changes the file. Rows keep their ids because they refer to each other. Photos are already re-encoded without metadata (ADR-0038), so they are exported as they are. "Notification permission asked" is not exported: the permission belongs to the phone.
- **Export:** one snapshot inside a Room transaction; saved with `ACTION_CREATE_DOCUMENT` (`CreateDocument`), so no storage permission.
- **Import:** `OpenDocument`; the whole file is read into the cache directory and checked first (zip, `backup.json`, `formatVersion`, every row's item exists, no duplicate ids or accrual months, slots 1–5, only the photo names the app gives out). A file from a newer `formatVersion` is refused with its own message; an older one is read. Then a preview ("12 items, 30 decisions, 5 photos"), **Replace all** and a second confirmation. Replacing writes the missing photos, then deletes and inserts every row in **one transaction**; if anything fails the transaction rolls back and the photos this call added are deleted. Settings (DataStore) are written after the commit; they are the one part outside the transaction, and losing them loses a switch, not data. Replace is the only mode; merge is out.
- **Photos and the sweep (ADR-0038):** photos are copied in before the rows, so the sweep (which spares files younger than an hour) never deletes a restored photo; photos the old data had and the backup doesn't become unreferenced and go with the next sweep.
- **Auto Backup / device transfer:** `allowBackup="true"`; `data_extraction_rules.xml` (Android 12+) and `backup_rules.xml` (≤ 11) include exactly `before-you-buy.db` and its `-wal`, `files/images/` and `files/datastore/`. Naming an include leaves everything else out: caches, the import's staging area, WorkManager's database. Android restores a backup only to the same or a newer app version, and Room migrates it (ADR-0019).

## Consequences

- A change to the DTOs that older apps can't read must raise `BACKUP_FORMAT_VERSION`; a new optional field needs only a default. A new table needs its DTO, the export, `isConsistent` and `replaceAllRows`; `RoomBackupRepositoryTest`'s round trip fails when one is forgotten.
- Backups aren't encrypted; the user decides where the file goes. Auto Backup data is encrypted by Google with the user's lock screen on supported devices.
- Applying a backup while other screens are open works because every screen observes the database; the app doesn't restart.
- Moving to a different `applicationId` (ADR-0026) is now possible through export/import.

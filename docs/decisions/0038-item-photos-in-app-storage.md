# ADR-0038: Item photos: Photo Picker, re-encoded copies in app storage, swept when unreferenced

**Status**: accepted
**Type**: technical
**Date**: 2026-10-19

## Context

Items may have a photo (brief §4.1, #16). The app has no storage permission and must not get one, has no `INTERNET` permission (ADR-0003), and keeps everything on the device (ADR-0002). A photo picked from the gallery is usually a full-resolution camera JPEG of several MB with metadata, often the location where it was taken. The URI the Photo Picker returns is only readable for a while.

Alternatives considered:

- **Keep the picker's URI and take a persistable permission**: the photo disappears when the user deletes it from the gallery, and some providers don't grant persistable access.
- **Copy the original file byte for byte**: keeps several MB per item and the original's metadata (location, device) inside app storage. Exports (#27) would carry that location along.
- **An image library (Coil, Glide)** for loading and caching: one more shipped dependency for a few local files a screen; the network modules are not wanted at all.
- **Delete a photo file when its item changes**: a crash between the database write and the delete leaves either a missing photo or an orphan, and an edit that is never saved leaves its picked photo behind.

## Decision

- Photos are picked with `ActivityResultContracts.PickVisualMedia` (images only). No storage or media permission. On Android versions without the system picker it falls back to the document picker, also without a permission.
- The picked image is decoded, turned upright by its EXIF orientation, scaled to at most 1600 px on its longer side and **re-encoded as JPEG (quality 85)** into `filesDir/images/<uuid>.jpg`. The new file has none of the original's metadata. `Item.imageRef` stores the file name; only names of that form map to files, so a stored ref can't point elsewhere.
- Photo files are **never deleted when an item changes**. At app start, a sweep deletes files no item references (`ItemRepository.imageRefs()`, active and ended items alike), except those written in the last hour: an open edit may still save them.
- Screens decode photos themselves (`BitmapFactory` with `inSampleSize`, off the main thread, a small in-memory cache). No image library.

## Consequences

- Removing or replacing a photo, or deleting an item later, frees its file at the next app start, not at once.
- Ended items (bought, decided against) keep their photos for history.
- Export/import (#27) must include `filesDir/images` alongside the database.
- No HEIC-specific code: Android decodes what it can display. An image it can't decode is refused with a message.

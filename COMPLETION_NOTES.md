# Completion build — 0.6.0 (versionCode 3)

Requested by the user on 2026-10-28 after the Phase 5 build still
showed leftover phase wording and an unfinished Preview.

## What changed

1. **Real document Preview (Compare screen).** Preview no longer
   shows a snippet for TXT files and a "later phase" note for
   everything else. It now runs the app's own extraction engine and
   shows the document's actual text for every readable format
   (TXT, CSV, HTML, DOCX, ODT, XLSX, RTF, and PDFs that have a text
   layer): paragraph and character counts, a scrollable view, and a
   "first part only" note for long documents.
   Preview never starts OCR: a scanned PDF reports honestly that it
   has no text layer, and an image points to the OCR screen.
2. **Leftover phase wording removed** from everything the user can
   see: the Compare footer note, the upload-card detection messages
   (FileValidation), the Settings privacy note, the Differences
   empty-state text, and the deleted preview placeholder string.

## Verification

- JVM unit suite: 90/90 tests pass (unchanged domain behaviour; the
  FileValidation changes are message text only).
- The two edited Android files (ComparisonViewModel, CompareScreen)
  parse with no syntax errors; full compilation is the cloud build.

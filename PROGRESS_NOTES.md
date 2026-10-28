# Progress Percentages Build (0.7.0) — what changed and why

Requested by Gifty (2026-10-28): show a percentage of progress during
conversion, extraction and editing activities.

Design rule: a percentage is shown ONLY where progress is genuinely
countable. Everywhere else the app shows the activity label with an
indeterminate bar — never an invented number.

## Where real percentages now appear

- Compare → Compare Files: reading a PDF original/product now reports
  "Reading the original (PDF) — page X of Y — NN%" for text-layer PDFs
  (PdfExtractor now strips page by page) and "Reading the original
  (scanned PDF) — page X of Y — NN%" for the OCR fallback
  (ScannedPdfOcr reports after each recognised page).
- Document Conversion (file pick) and Document Editor (file open):
  the same page progress through ToolFiles.extract.
- OCR and Text Extraction: scanned PDFs report page progress.
- PDF Tools: unchanged (operations are single quick steps).

## Label-only phases (no percentage, by design)

- "Comparing the documents…", correction apply, single-image OCR
  (one ML Kit call), and writing an output file — each is one
  un-splittable step; the UI shows the label with a moving bar.

## New code

- domain/progress/WorkProgress.kt — label + optional done/total,
  percent arithmetic clamped 0–100 (3 new JVM tests).
- ui/components/ProgressBlock.kt — determinate bar + "label — NN%"
  when countable, indeterminate bar + label otherwise.
- PdfExtractor: optional page-progress callback (default none, so
  existing callers are unchanged); per-page stripping.
- ScannedPdfOcr.ocrAllPages: optional progress callback.
- ComparisonViewModel / ConversionViewModel / EditorViewModel /
  OcrViewModel: progress state wired end to end; cleared on
  completion and on error.

## Verification

- JVM suite re-run for this build (see delivery note for the count).
- APK built by GitHub Actions and verified as a genuine package
  before the download link was refreshed.

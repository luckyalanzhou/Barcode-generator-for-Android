# Barcode Results Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve atomic batch display while reducing result-page row Bitmap memory and preparation time.

**Architecture:** Prepare and retain every raw barcode Bitmap before publishing the results page. Draw each visible result row from that raw bitmap and label directly in Compose, without creating a full-width composite preview Bitmap. Keep the existing composite renderer for export; make the Code 128 quiet-zone Canvas path pixel-correct before retaining that allocation optimization.

**Tech Stack:** Kotlin, Jetpack Compose Canvas/LazyColumn, Android Bitmap/Canvas, ZXing, Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-02-barcode-results-performance-design.md`

## Global Constraints

- Do not show result rows or toolbar until every item in the batch is ready.
- Preserve Code 128 scanner polarity and the exact light/dark page background.
- Preserve export dimensions, color, QR quiet zones, and existing save/share behavior.
- Do not change database, input limit, navigation, barcode formats, or build/signing configuration.
- Keep the full batch available in memory for the results page; no partial loading or placeholder rows.

## Review Focus

- Last item is slow, canceled, or fails: the page must stay atomic and retry must work (`ResultsContentPreparationTest`).
- 100 Code 128 or QR rows: no per-row composite preview allocation; every raw code remains usable (`BarcodeRenderPerformanceBaselineTest`).
- Long Code 128 labels and increased font scale: labels keep their current wrapping and row height (`preparedResultRowKeepsBarcodeAndLabelAligned` in `BarcodeRenderPerformanceBaselineTest`).
- Dark and light quiet zones: exact background polarity and decodability remain correct (`BarcodePreviewTransparencyTest`).
- Results are shared or saved after preview preparation: export still uses the existing full-width composite path (`ResultToolbarInteractionTest` and export tests).

---

### Task 1: Make the Canvas quiet-zone optimization scanner-safe

**Files:**
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/infrastructure/barcode/ZxingBarcodeImageRenderer.kt`
- Test: `android/app/src/test/kotlin/com/luckyalanzhou/barcodegenerator/infrastructure/barcode/BarcodePreviewTransparencyTest.kt`

**Interfaces:**
- Consumes: `addQuietZone(source: Bitmap, backgroundColor: Int): Bitmap`
- Produces: the same returned bitmap dimensions and pixels as the reference implementation, without full-frame `IntArray` copies.

- [x] **Step 1: Run the existing quiet-zone regression tests against the current candidate**

Run: `./gradlew.bat :app:testBetaDebugUnitTest --tests 'com.luckyalanzhou.barcodegenerator.infrastructure.barcode.BarcodePreviewTransparencyTest' --no-configuration-cache -PenableAppUnitTests=true`

Expected: current candidate reproduces failures in the dark opaque quiet-zone and Code 128 decode assertions.

- [x] **Step 2: Preserve exact pixel compositing with a single padded output array; Canvas compositing failed scan/parity tests**

Use `Bitmap.eraseColor(backgroundColor)` on the new `ARGB_8888` bitmap, then draw the trimmed source at the quiet-zone offset with filtering disabled. Keep exception cleanup and cache ownership unchanged.

- [x] **Step 3: Verify the regression tests pass**

Run the same command as Step 1.

Expected: all three tests pass for light and dark preview/export paths; the implementation uses one padded output array instead of the prior two arrays.

### Task 2: Prepare raw result rows and draw without composite preview bitmaps

**Files:**
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/results/ResultImageLayout.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/results/ResultsContent.kt`
- Test: `android/app/src/test/kotlin/com/luckyalanzhou/barcodegenerator/ui/feature/results/ResultsContentPreparationTest.kt`
- Test: `android/app/src/test/kotlin/com/luckyalanzhou/barcodegenerator/ui/feature/results/BarcodeRenderPerformanceBaselineTest.kt`
- Test: `android/app/src/test/java/com/luckyalanzhou/barcodegenerator/ui/feature/results/ResultExportBatchTest.java`
- Test: `android/app/src/test/java/com/luckyalanzhou/barcodegenerator/ui/feature/results/ResultExportActionTest.java`
- Test: `android/app/src/test/java/com/luckyalanzhou/barcodegenerator/ui/feature/results/ResultToolbarInteractionTest.kt`

**Interfaces:**
- Produce `PreparedResultRow(item: CodeItem, barcode: Bitmap, layout: ResultImageRowLayout)`; the bitmap is a shared renderer/cache reference and must not be recycled by the row composable.
- `prepareResultRows(...)` returns `List<PreparedResultRow>` only after all input items load successfully; it does not call `composeResultRowImage`.
- `composeResultRowImage(...)` remains available to export callers.

- [x] **Step 1: Add a preparation test for the all-or-nothing row list**

Use the existing deferred ninth item. Assert that before it completes there is no result toolbar or barcode row; after completion all nine rows are available.

- [x] **Step 2: Add row-layout geometry tests**

For Code 128 and QR, assert the prepared layout reports the same row width/height, barcode destination rectangle, and label top/height as the existing `composeResultRowImage` path at density 1 and 3, font scale 1 and 1.5.

- [x] **Step 3: Return raw bitmaps plus layout metadata from `prepareResultRows`**

Build the complete list in the existing bounded batches and keep progress updates, cancellation propagation, all-or-nothing failure, and cache ownership. Do not allocate a composite row bitmap.

- [x] **Step 4: Draw each row directly in the Compose lazy item**

Draw the page background, raw barcode into the computed destination rectangle, and the existing `StaticLayout` label on the visible row canvas. Retain row keys, content descriptions, spacing, toolbar, and the full-batch readiness gate. Remove disposal code that recycles the composite rows; do not recycle shared cache bitmaps.

- [x] **Step 5: Verify visual and scanner parity**

Add `preparedResultRowKeepsBarcodeAndLabelAligned` to `BarcodeRenderPerformanceBaselineTest`; capture Code 128 and QR result rows in light/dark themes and verify decoded contents, row bounds, and label position. Then run the row-layout, preparation, performance-baseline, `BarcodePreviewTransparencyTest`, `ResultExportBatchTest`, `ResultExportActionTest`, and `ResultToolbarInteractionTest` classes.

Expected: all tests pass; Code 128 and QR remain decodable in light/dark themes; export tests still exercise `composeResultRowImage`.

### Task 3: Measure the new pipeline and verify on the configured emulator

**Files:**
- Modify: `android/app/src/test/kotlin/com/luckyalanzhou/barcodegenerator/ui/feature/results/BarcodeRenderPerformanceBaselineTest.kt`
- Local artifact: `.local-validation/logs/` and `.local-validation/apk/` only if needed for measurement.

**Interfaces:**
- Performance test reports preparation median time and retained raw/preview Bitmap allocation for 10, 20, and 100 rows.
- Existing `composeResultRowImage` export measurement remains separately identified.

- [x] **Step 1: Add the prepared-result memory and timing report**

Assert that result preparation retains raw bitmaps but allocates zero row preview bitmaps; keep the current scan, color, and 100-row scroll assertions.

- [x] **Step 2: Run repeated Robolectric measurements**

Run the focused baseline class at least three times on the same JDK 27 and Gradle user home; compare medians against the recorded `HEAD` baseline (100 Code 128: 453 ms, raw+row estimate 141.0 MiB).

Expected: 100-row preview allocation drops to zero and total estimated retained bitmap memory falls by at least 50%; 100-row preparation median improves by at least 20%; three-run 10-row median does not regress by more than 10%.

- [x] **Step 3: Install and smoke-test the betaDebug APK on `BarcodeGenerator_Lite_API35`**

Build `:app:assembleBetaDebug`, install `BarcodeGenerator-Debug.apk`, exercise 10-item and 100-item batches, verify the result page appears only after full preparation, scroll to the end, and inspect crash logs and Bitmap memory.

Expected: no crash, no missing or recycled rows, correct barcode contents, and lower peak bitmap memory than the same AVD baseline.

- [x] **Step 4: Run the project full local gate**

Run: `./gradlew.bat verifyLocal --no-configuration-cache -PenableAppUnitTests=true` from `android` with the project `.local-validation` Gradle/Java homes.

Expected: `BUILD SUCCESSFUL`. Do not push as part of this goal.



# Liquid Glass Strength Slider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` to implement this plan task-by-task.

**Goal:** Add a persisted 0–100% Liquid Glass strength control below the existing setting, applying a single user value across all app and LAN-share glass surfaces while preserving each surface's readable appearance.

**Architecture:** Store an integer percentage in the existing SettingsRepository/DataStore path, publish it through SettingsUiState and VisualEffectsPolicy, and map it to each material's opacity baseline. Compose and LAN-share HTML/CSS consume the same percentage; blur, refraction, highlight geometry, and gesture animation parameters remain unchanged.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Preferences DataStore, Kyant Backdrop, Android string resources, local Gradle verification, Audit emulator.

**Spec:** `docs/superpowers/specs/2026-11-17-liquid-glass-strength-design.md`

## Global Constraints

- Add an “Appearance Theme” navigation row to the Settings > General group, using the existing theme palette icon. Keep Language in General. Move App Theme, Liquid Glass switch, and the strength row to the Appearance Theme page; do not add leading icons to controls within that page.
- Place the strength row directly below the Liquid Glass switch on the Appearance Theme page with a left title, center slider, and right percentage value.
- Store an integer from 0 to 100; default to 50 and keep the current appearance at 50.
- Use 10-percentage-point slider steps (0%, 10%, …, 100%; `steps = 9`) and verify every step changes the mapped material strength.
- Match the existing barcode-style slider track, gaps, thumb, colors, and touch target. Strength is discrete; do not expose the existing freeform numeric editor for this row.
- At 0%, use 75% of each surface's existing opacity baseline. At 50%, use the existing baseline. At 100%, move 35% of the remaining distance from that baseline toward full opacity. Interpolate linearly on each half and clamp the result to 0–1; a deliberately fully opaque surface remains at 1 throughout.
- Change material tint/opacity only; do not change blur, refraction, highlight geometry, or gesture animation parameters.
- Disabling Liquid Glass disables the slider but preserves its saved value.
- Preserve the existing modal backdrop source hierarchy and blurred-page appearance; nested menus must sample their owning dialog.
- Update all seven resource sets: default, Simplified Chinese, Traditional Chinese, English, Japanese, Korean, and Spanish.
- Add no third-party dependencies and preserve all unrelated local changes.
- Do not push, commit, trigger a remote build, or change Beta/Official release state in this task.

## Review Focus

1. A missing DataStore key must resolve to 50%, preserving current visuals for existing users; pin this in `SettingsStoreLiquidGlassStrengthTest`.
2. A drag across the slider must not leave an older percentage persisted after the final value; pin UI publication, write ordering, and stored bounds in settings tests.
3. Disabling and re-enabling Liquid Glass must retain the chosen percentage; pin this in `SettingsViewModelTest`.
4. High-contrast accessibility and disabled-glass policies must continue to override visual glass rendering without deleting the preference; pin this in `VisualEffectsPolicyTest`.
5. Localized titles and percentages must fit the settings row on narrow screens in both themes; pin semantics and bounds in `SettingsLiquidGlassStrengthRowTest`, then inspect all locales on the emulator.

---

### Task 1: Persist and publish the strength percentage

**Files:**
- Modify: `android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/SettingsRepository.kt`
- Modify: `android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/SettingsStore.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/settings/SettingsViewModel.kt`
- Test: `android/core/data/src/test/kotlin/com/luckyalanzhou/barcodegenerator/data/SettingsStoreLiquidGlassStrengthTest.kt`
- Test: `android/app/src/test/java/com/luckyalanzhou/barcodegenerator/SettingsViewModelTest.kt`
- Modify test fakes: `android/app/src/test/kotlin/com/luckyalanzhou/barcodegenerator/presentation/update/UpdateStartupCheckTest.kt`

**Interfaces:**
- Add `SettingsRepository.getLiquidGlassStrengthPercent(): Int` and `setLiquidGlassStrengthPercent(percent: Int): Job`.
- Add `SettingsUiState.liquidGlassStrengthPercent: Int = 50` and `SettingsViewModel.setLiquidGlassStrengthPercent(percent: Int)`.
- Add `SettingsStore.LIQUID_GLASS_STRENGTH_PERCENT` using integer preference key `appearance_liquid_glass_strength_percent`.
- Extend `SettingsViewModel.initialize` with a defaulted `liquidGlassStrengthPercent: Int = 50` argument so current test and startup callers remain compatible.

- [ ] **Step 1: Add failing persistence and ViewModel tests**
  - Missing key loads as 50; persisted values round-trip; writes clamp below 0 to 0 and above 100 to 100.
  - Setting a value updates `SettingsUiState` immediately and writes that clamped value.
  - A strength changed before `loadPersistedState()` completes remains the selected value after initialization and is persisted, following the existing Liquid Glass switch behavior.
  - Two quick writes persist in order, with the last selected percentage remaining stored.
  - Turning the Liquid Glass switch off and back on leaves the stored percentage unchanged.
  - Update every `SettingsRepository` test fake with a neutral 50% default and completed write job.
- [ ] **Step 2: Run the focused tests and confirm they fail on the missing contract/behavior**

Run from `android`: `./gradlew.bat -g .local-validation\gradle-user-home -PenableAppUnitTests=true :core:data:testDebugUnitTest :app:testBetaDebugUnitTest --no-configuration-cache`

- [ ] **Step 3: Implement the repository and DataStore contract**
  - Read missing strength as 50 and clamp writes to 0–100 through the existing serialized write queue.
- [ ] **Step 4: Implement ViewModel load, immediate state publication, persistence, and pre-load change preservation**
- [ ] **Step 5: Re-run the focused tests and confirm all persistence and initialization assertions pass**

### Task 2: Define one bounded opacity mapping and pass it through app policy

**Files:**
- Create: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/component/glass/GlassOpacityMapping.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/theme/VisualEffectsPolicy.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/ComposeAppShell.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/ComposeDialogWindow.kt`
- Test: `android/app/src/test/java/com/luckyalanzhou/barcodegenerator/ui/component/glass/GlassOpacityMappingTest.kt`
- Test: `android/app/src/test/java/com/luckyalanzhou/barcodegenerator/ui/theme/VisualEffectsPolicyTest.kt`

**Interfaces:**
- Add `glassOpacityForStrength(baseOpacity: Float, strengthPercent: Int): Float` in `GlassOpacityMapping.kt`.
- Add `glassStrengthPercent: Int = 50` to `VisualEffectsPolicy`; add an optional `glassStrengthPercent: Int = 50` parameter to `resolveVisualEffectsPolicy` and `rememberVisualEffectsPolicy`.

- [ ] **Step 1: Add mapping tests for every 10% step, monotonicity, percentage clamping, and opacity bounds**
  - For base opacity 0.8, assert 0% = 0.6, 50% = 0.8, 100% = 0.87; assert values at every 10% step strictly increase (except a fully opaque baseline), and assert 50% equals every caller's existing baseline.
- [ ] **Step 2: Run the focused tests and confirm the new mapping is missing**

Run from `android`: `./gradlew.bat -g .local-validation\gradle-user-home -PenableAppUnitTests=true :app:testBetaDebugUnitTest --tests '*GlassOpacityMappingTest' --tests '*VisualEffectsPolicyTest' --no-configuration-cache`

- [ ] **Step 3: Implement the piecewise linear mapping and policy propagation**
  - For baseline `b`, map 0–50% linearly from `0.75*b` to `b`; map 50–100% linearly from `b` to `b + 0.35*(1-b)`.
  - Clamp input percentages and output opacity to their valid bounds; map a non-finite baseline to 0.5 and preserve a baseline of 1 as 1. Keep `opaqueGlass` priority for the existing disabled and high-contrast cases.
  - Pass the loaded percentage in both the main app shell and independent Compose dialog windows.
- [ ] **Step 4: Re-run mapping and policy tests; confirm 50% preserves the current policy and high contrast still wins**

### Task 3: Add the localized settings slider row

**Files:**
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/SettingsContent.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/ComposeSettingsSlider.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/SettingsContentState.kt`
- Create: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/AppearanceThemeContent.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/AboutContent.kt` (expose the existing secondary-page header for reuse)
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/ComposePageRoute.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/navigation/NavigationRoute.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-b+zh+Hans/strings.xml`
- Modify: `android/app/src/main/res/values-b+zh+Hant/strings.xml`
- Modify: `android/app/src/main/res/values-en/strings.xml`
- Modify: `android/app/src/main/res/values-ja/strings.xml`
- Modify: `android/app/src/main/res/values-ko/strings.xml`
- Modify: `android/app/src/main/res/values-es/strings.xml`
- Test: `android/app/src/test/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/SettingsLiquidGlassStrengthRowTest.kt`

**Interfaces:**
- Add `liquidGlassStrengthPercent: Int = 50` to `SettingsContentState`.
- Add `onEnterAppearanceTheme` to `SettingsContent`; create an `AppearanceTheme` route and render its secondary page with the existing settings controls.
- Add `onLiquidGlassStrengthChange: (Int) -> Unit` to the Appearance Theme content and connect it to `SettingsViewModel.setLiquidGlassStrengthPercent` in `ComposePageRoute`.
- Add a localized “Appearance Theme” title/entry resource to all seven resource sets.
- Add localized title and percent-format resources in all seven resource sets.
  - Use title “强度” in default and Simplified Chinese, “強度” in Traditional Chinese and Japanese, “Strength” in English, “강도” in Korean, and “Intensidad” in Spanish; use a `%1$d%%` value template in each resource set.

- [ ] **Step 1: Add a Compose test for row order, title/slider/percent semantics, 50% display, and disabled state when Liquid Glass is off**
- [ ] **Step 2: Run the focused Compose test and confirm the row is absent**

Run from `android`: `./gradlew.bat -g .local-validation\gradle-user-home -PenableAppUnitTests=true :app:testBetaDebugUnitTest --tests '*SettingsLiquidGlassStrengthRowTest' --no-configuration-cache`

- [ ] **Step 3: Add the Appearance Theme entry and page; place the strength row directly below the switch**
  - Keep the title left, a weighted slider centered, and the integer percentage right on one line; use a concise localized title so the track retains usable width.
  - Keep the palette icon on the Settings home entry only; place App Theme, Liquid Glass, and Strength on the Appearance Theme page without leading icons.
  - Configure the slider with 10-percentage-point steps (`steps = 9`) and verify it exposes exactly the eleven valid values from 0 through 100.
  - Disable the slider when the parent switch is off, retain its percentage, and expose the title and percentage through Compose slider semantics.
  - Preserve the existing switch trailing padding in the dirty worktree.
- [ ] **Step 4: Add matching title and `%1$d%%` value strings in all seven resource sets**
- [ ] **Step 5: Re-run the Compose test; confirm the right percentage updates on drag and the row stays in bounds**

### Task 4: Apply the policy to all native Compose glass surfaces

**Files:**
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/component/glass/TabGlassMaterial.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/component/glass/GlassBackdrop.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/component/menu/MenuMaterialSpec.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/component/menu/MenuSurface.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/component/glass/GlassRoundActionButton.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/generate/GlassGenerateActionBar.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/lanshare/LanShareImagePreviewDialog.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/TabLiquidGlassScene.kt`
- Test: `android/app/src/test/java/com/luckyalanzhou/barcodegenerator/ui/component/glass/TabGlassMaterialTest.kt`
- Test: `android/app/src/test/java/com/luckyalanzhou/barcodegenerator/ui/component/menu/MenuMaterialSpecTest.kt`

**Interfaces:**
- Pass `VisualEffectsPolicy.glassStrengthPercent` to material builders that have a separate light/dark baseline.
- Use `glassOpacityForStrength` for explicit opacity values in the shared backdrop, generation action bar, and image-preview controls.
- Extend `tabGlassMaterial(background: Color, heightDp: Float, strengthPercent: Int = 50)`, `resultActionGlassMaterial(background: Color, strengthPercent: Int = 50)`, and `menuMaterialSpec(background: Color, foreground: Color, heightDp: Float, highContrast: Boolean, strengthPercent: Int = 50)`.
- Extend `glassFallbackOpacity(opacity: Float, smallControl: Boolean, opaque: Boolean, strengthPercent: Int = 50)`; its large-surface floor is the mapped 0.92 baseline.

- [ ] **Step 1: Extend material tests to cover every 10% step for light and dark baselines**
  - Assert opacity is monotonic and bounded, 50% matches current values, and blur/refraction/rim/motion inputs are unchanged.
- [ ] **Step 2: Run the focused material tests and confirm the new parameters are not yet consumed**

Run from `android`: `./gradlew.bat -g .local-validation\gradle-user-home -PenableAppUnitTests=true :app:testBetaDebugUnitTest --tests '*TabGlassMaterialTest' --tests '*MenuMaterialSpecTest' --no-configuration-cache`

- [ ] **Step 3: Apply the percentage exactly once to each native material path**
  - Cover the Tab background/selection surface, the four result action buttons, generate action bar, shared action/context/option menus, dialogs using `MenuSurface`, the LAN QR dialog, and LAN image-preview toolbar/close control.
  - Leave `blur`, `lens`, highlight, shadow, pointer tracking, and motion values unchanged.
  - Preserve `trackPointer`, dialog backdrop window selection, modal blur, and nested-menu backdrop ownership from existing local changes.
  - Update the legacy large-surface fallback floor in `GlassBackdrop.kt` from its current fixed 0.92 baseline to the same mapping of 0.92 at 50%; retain opaque/high-contrast overrides.
- [ ] **Step 4: Re-run material tests and confirm the common midpoint is visually unchanged in the model outputs**

### Task 5: Apply the same preference to the LAN-share browser page

**Files:**
- Modify: `android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/web/LanShareWebGlassProvider.kt`
- Modify: `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/di/AppLanShareWebGlassProvider.kt`
- Modify: `android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/server/LanShareServer.kt`
- Modify: `android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/web/LanShareWebTemplates.kt`
- Modify: `android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/web/LanShareWebStyles.kt`
- Test: `android/core/lan-share/src/test/java/com/luckyalanzhou/barcodegenerator/LanShareWebGlassStrengthTest.kt`
- Modify test fakes: `android/app/src/test/kotlin/com/luckyalanzhou/barcodegenerator/presentation/update/UpdateStartupCheckTest.kt` only if its provider construction changes.

**Interfaces:**
- Keep `LanShareWebGlassProvider` a `fun interface`; add a default `getLiquidGlassStrengthPercent(): Int = 50` method.
- Extend `LanShareWebTemplates.page` with `glassStrengthPercent: Int = 50` after the existing enable flag.
- Extend `LanShareWebStyles.render` with the same defaulted percentage parameter.

- [ ] **Step 1: Add generated-page tests asserting 0% is clearer, 50% retains current CSS opacity values, 100% is more tinted, and disabled glass still uses the existing solid overrides**
- [ ] **Step 2: Run the focused LAN-share test and confirm it fails for the absent strength parameter**

Run from `android`: `./gradlew.bat -g .local-validation\gradle-user-home :core:lan-share:testDebugUnitTest --tests '*LanShareWebGlassStrengthTest' --no-configuration-cache`

- [ ] **Step 3: Pass the cached DataStore percentage into each newly rendered LAN-share page**
  - Map light/dark message bubbles, download controls, and other translucent glass controls with the same 0/50/100 piecewise rule; refresh is reflected on the next page request.
- [ ] **Step 4: Re-run the LAN-share tests and confirm CSS at 50% is unchanged and the disabled override still wins**

### Task 6: Run local integration and visual acceptance

**Files:**
- Evidence: `.local-validation/evidence/liquid-glass-strength-20261117/`

- [ ] **Step 1: Run focused module tests**

Run from `android`: `./gradlew.bat -g .local-validation\gradle-user-home -PenableAppUnitTests=true :core:domain:test :core:data:testDebugUnitTest :core:lan-share:testDebugUnitTest :app:testBetaDebugUnitTest --no-configuration-cache`

- [ ] **Step 2: Build the Beta Debug APK for the Audit emulator**

Run from `android`: `./gradlew.bat -g .local-validation\gradle-user-home -PenableAppUnitTests=true :app:assembleBetaDebug --no-configuration-cache`

- [ ] **Step 3: Install and inspect the setting row in all six explicit languages**
  - Check Simplified Chinese, Traditional Chinese, English, Japanese, Korean, and Spanish in Light and Dark themes.
  - Confirm title, slider, and percentage stay on one line, remain aligned, and retain a usable touch target and slider track.
- [ ] **Step 4: Inspect all native glass surface families in Light and Dark themes at 0%, 50%, and 100%, and verify every 10% slider step updates the live material**
  - Check Tab navigation, generate action bar, result circular actions, option/context/long-press menus, a nested menu inside a dialog, LAN QR dialog, and LAN image-preview controls.
  - Confirm the left endpoint is more transparent, the right endpoint more tinted, and 50% matches the existing default.
  - Confirm motion/refraction remains unchanged and nested menus continue to sample their owning dialog's backdrop.
- [ ] **Step 5: Check the LAN-share browser page at 0%, 50%, and 100%, then confirm changing the setting is reflected after reloading the page**
- [ ] **Step 6: Save emulator evidence, restore the emulator's prior theme/language/glass settings, and clean up only processes started for this acceptance**
- [ ] **Step 7: After no build is using the Gradle user directory, run `./gradlew.bat --stop` and confirm the task-owned Gradle daemons exit**

## Handoff

The user previously selected subagent-parallel implementation for a cross-module Android feature. After this plan is approved, use that execution method; keep shared interface and policy decisions centralized, then review the integrated diff and all evidence before reporting completion. Do not commit or push.

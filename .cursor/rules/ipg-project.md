---
description: Project context and architecture for Icon Pack Generator (IPG). Read this when working on any file in this repo.
globs: **/*
---

# Icon Pack Generator (IPG) -- Project Memory

## Identity

- **App**: Icon Pack Generator
- **Package**: `com.meowgi.iconpackgenerator`
- **Repo**: `git@github.com:ovehbe/ipg.git`
- **Latest tag**: v0.4.0
- **Language**: Kotlin (app) + Java (packviewer module)
- **UI**: Jetpack Compose with Material 3
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35

## What It Does

Scans installed Android apps, extracts launcher icons, converts them to monochrome silhouettes using AI background removal + Otsu two-tone rendering, then builds/signs/installs real icon-pack APKs on-device. No server, no cloud.

## Architecture

```
Extract Icon → U2-Net Background Removal → Otsu Two-Tone Monochrome → Build APK (ARSCLib) → Sign (apksig) → Install (PackageInstaller)
```

## Key Technical Decisions (DO NOT change without good reason)

### APK Building: ARSCLib (not aapt2)
- `io.github.reandroid:ARSCLib:1.3.5` creates binary resources.arsc + AndroidManifest.xml
- Pure Java, works on Android, replaces aapt2 which has no ARM64 build
- v1.3.5 API: use `createChildElement()` not `newElement()` on ResXmlElement
- Resource entries for drawables: `pkg.getOrCreate("nodpi-v4", "drawable", name)` then `entry.setValueAsString(filePath)`
- DEX files MUST use `ZipEntry.STORED` (uncompressed) or Android can't parse them

### APK Signing: apksig
- `com.android.tools.build:apksig:8.7.3`, pure Java v1+v2 signing
- Signing key stored in app-private BKS keystore (`filesDir/ipg_signing.bks`)
- Same key across all generated packs enables update flow

### Background Removal: U2-Net-P via ONNX Runtime
- Model: `assets/u2netp.onnx` (4.4MB, from rembg project)
- Runtime: `com.microsoft.onnxruntime:onnxruntime-android:1.23.0`
- Input: 320x320, CHW order, ImageNet normalization
- Output: saliency mask normalized to 0-1, thresholded (not used as direct alpha multiplier)
- Thresholds configurable via settings: keepThreshold (default 0.15), cutThreshold (default 0.05)

### Monochrome Conversion: Otsu Two-Tone
- Otsu's method splits foreground luminance into two groups
- Larger group = primary (255 alpha), smaller = secondary (configurable, default 80 alpha)
- Min luminance gap check prevents splitting monochromatic icons
- All parameters adjustable via Settings screen

### Icon Pack Format: ADW Standard
- Manifest intent-filters: `org.adw.ActivityStarter.THEMES`, `com.novalauncher.THEME`, plus Apex/GO/ADW
- `assets/appfilter.xml` with `ComponentInfo{pkg/activity}` → drawable mappings
- `assets/drawable.xml` for launcher manual picker
- `res/drawable-nodpi-v4/*.png` at 192x192
- Activity class: `com.meowgi.ipg.viewer.PackViewerActivity` (from packviewer DEX)

### Pack Viewer: Separate Module
- `packviewer/` module compiles a standalone Java Activity to DEX
- Gradle task `extractViewerDex` copies it to `app/src/main/assets/viewer_classes.dex`
- The viewer reads icons directly from APK zip entries as fallback (getIdentifier may fail with ARSCLib-built tables)
- Pure Android framework APIs only (no dependencies) to keep DEX small (~8KB)

## Module Map

```
app/src/main/java/com/meowgi/iconpackgenerator/
├── scanner/AppScanner.kt          -- PackageManager MAIN/LAUNCHER query
├── icon/
│   ├── IconExtractor.kt           -- Adaptive/Vector/Bitmap → 192x192 Bitmap
│   ├── BackgroundRemover.kt       -- U2-Net ONNX inference, saliency mask
│   └── MonochromeConverter.kt     -- Otsu two-tone rendering
├── builder/
│   ├── IconPackBuilder.kt         -- Orchestrator (scan → extract → convert → build → sign)
│   ├── ApkAssembler.kt            -- ARSCLib APK creation (resources + manifest + DEX)
│   ├── ApkSigner.kt               -- apksig v1+v2 signing
│   ├── KeyStoreManager.kt         -- RSA 2048 key generation + BKS persistence
│   └── AppFilterBuilder.kt        -- XML generation for appfilter + drawable
├── installer/
│   ├── PackageInstallerHelper.kt   -- Session install + intent fallback
│   └── InstallReceiver.kt         -- Install result broadcast receiver
├── worker/IconPackWorker.kt       -- WorkManager CoroutineWorker
├── domain/
│   ├── AppInfo.kt                 -- Data class for scanned apps
│   ├── IconPackConfig.kt          -- Style + color + package name
│   ├── GenerationResult.kt        -- Result + progress types
│   └── ConversionSettings.kt      -- Tunable parameters + SharedPrefs persistence
├── ui/
│   ├── MainScreen.kt              -- Main Compose UI with color selection, dry-run, build
│   ├── SettingsScreen.kt          -- Sliders for all conversion parameters
│   ├── ColorPickerDialog.kt       -- RGB sliders + hex input + presets
│   ├── GeneratorViewModel.kt      -- State management + WorkManager dispatch
│   └── theme/Theme.kt             -- Material 3 dynamic color theme
└── util/
    ├── ResourceNameSanitizer.kt   -- Component → valid resource name
    └── VersionTracker.kt          -- SharedPrefs versionCode per package
```

## Generated APK Package Names
- `com.meowgi.ipg.white` -- white icons
- `com.meowgi.ipg.black` -- black icons
- `com.meowgi.ipg.c_<rrggbb>` -- custom color (lowercase hex)

## Build Commands
```bash
./gradlew :app:assembleDebug          # Debug APK
./gradlew :app:assembleRelease        # Signed release APK
./gradlew :app:testDebugUnitTest      # 17 unit tests
```

## Release Signing
- Keystore: `release-keystore.jks` (git-ignored, in project root)
- Alias: `ipg-release`
- Password: `ipg2026release`
- Configured in `app/build.gradle.kts` signingConfigs

## Known Gotchas
- ARSCLib v1.3.5 uses `createChildElement()`, NOT `newElement()` (main branch API)
- DEX in generated APKs MUST be `ZipEntry.STORED` or Android rejects it with "List too large for map size"
- `getResources().getIdentifier()` may return 0 for ARSCLib-built resource tables; the pack viewer has a ZipFile fallback
- The `extractViewerDex` Gradle task runs before app build; if packviewer changes, it auto-rebuilds
- `noCompress += "onnx"` in android resources block prevents ONNX model compression
- Android 11+ package visibility needs `<queries>` in manifest for launcher activity scanning
- Generated packs use `targetSdk=28` to avoid v2-only signing requirements on older devices
- `FLAG_KEEP_SCREEN_ON` set in MainActivity for long generation runs

## Conversion Pipeline Tunables (Settings Screen)
| Parameter | Default | Effect |
|-----------|---------|--------|
| BG Keep Threshold | 0.15 | U2-Net saliency above this = fully kept. Lower = keep more |
| BG Cut Threshold | 0.05 | U2-Net saliency below this = removed. Lower = remove less |
| Detail Opacity | 80 | Alpha for secondary Otsu group (0-255) |
| Min Luminance Gap | 30 | Min difference for two-tone split to activate |
| Icon Padding | 16% | Space around icon in output |
| Use Full Icon | false | Render full adaptive icon vs foreground-only |

## Version History
- v0.1.0: Initial implementation -- full pipeline end-to-end
- v0.2.0: U2-Net background removal, Otsu two-tone conversion
- v0.3.0: Pack viewer, custom icons, UI overhaul, dry-run preview
- v0.4.0: Settings screen with tunable conversion parameters

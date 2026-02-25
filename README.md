<a href="https://github.com/ovehbe/ipg/releases/tag/v0.4.0">
  <img src="https://img.shields.io/badge/Download-Pre--Release-red?style=for-the-badge" />
</a>

# Icon Pack Generator (IPG)

An Android app that generates monochrome icon packs **on-device** by building, signing, and installing real icon-pack APKs that work with launchers like Nova, Lawnchair, ADW, and others.

No server. No cloud. No shared database. Everything runs locally.

## Features

- **Scans all installed apps** and their launcher activities
- **AI-powered background removal** using U2-Net (4.4MB on-device model)
- **Monochrome conversion** with two-tone detail preservation (Otsu's method)
- **Three color modes**: White, Black, or any custom color via hex/picker
- **Builds real APKs** on-device using ARSCLib (no aapt2 binary needed)
- **Signs APKs** with apksig (v1+v2 schemes, persistent signing key)
- **Installs via PackageInstaller** session API (no root required)
- **Dry-run preview** with random 24-icon sampling before committing to a full build
- **Built-in viewer** in generated packs — tap the icon pack in your app drawer to browse all icons
- **Deterministic package names** — regenerating the same color updates the existing pack

## Architecture

```
Extract Icon → U2-Net Background Removal → Otsu Two-Tone Monochrome → Build APK (ARSCLib) → Sign (apksig) → Install
```

### Key Libraries

| Library | Purpose |
|---------|---------|
| [ARSCLib](https://github.com/REAndroid/ARSCLib) | Creates binary resources.arsc + AndroidManifest.xml, packages APKs (replaces aapt2) |
| [apksig](https://android.googlesource.com/platform/tools/apksig/) | APK signing with v1+v2 schemes + zipalign (replaces apksigner binary) |
| [ONNX Runtime](https://onnxruntime.ai/) | Runs U2-Net-P model for on-device salient object detection |
| [U2-Net-P](https://github.com/xuebinqin/U-2-Net) | Lightweight (4.4MB) salient object detection model for background removal |

### Generated Icon Pack Format

Each generated APK follows the ADW icon pack standard:

- `AndroidManifest.xml` with `org.adw.ActivityStarter.THEMES` + `com.novalauncher.THEME` intent-filters
- `assets/appfilter.xml` mapping `ComponentInfo{pkg/activity}` to drawable names
- `assets/drawable.xml` for launcher manual icon picker
- `res/drawable-nodpi-v4/*.png` — 192x192 monochrome icons
- `classes.dex` with a built-in icon viewer Activity
- Deterministic package names: `com.meowgi.ipg.white`, `com.meowgi.ipg.black`, `com.meowgi.ipg.c_<hex>`

### Project Structure

```
ipg/
├── app/                          Main IPG application
│   └── src/main/
│       ├── assets/
│       │   ├── u2netp.onnx       U2-Net-P model (4.4MB)
│       │   ├── pack_icon.png     Icon for generated packs
│       │   └── viewer_classes.dex  Pre-compiled viewer Activity
│       └── java/com/meowgi/iconpackgenerator/
│           ├── scanner/          App scanning (PackageManager queries)
│           ├── icon/             Icon extraction, background removal, monochrome conversion
│           ├── builder/          APK assembly, signing, orchestration
│           ├── installer/        PackageInstaller session + fallback
│           ├── worker/           WorkManager background processing
│           ├── ui/               Jetpack Compose UI
│           └── util/             Resource name sanitization, version tracking
├── packviewer/                   Standalone viewer Activity (compiled to DEX)
│   └── src/main/java/.../PackViewerActivity.java
├── build.gradle.kts
└── settings.gradle.kts
```

## Building

```bash
# Requires Android SDK with build-tools 35+
echo "sdk.dir=/path/to/android/sdk" > local.properties
./gradlew :app:assembleDebug
```

The build automatically compiles the packviewer module and extracts its DEX into app assets.

## Requirements

- Android 8.0+ (API 26)
- ~100MB storage for the app (ONNX Runtime native libs + model)
- "Install unknown apps" permission for installing generated packs

## What Requires User Interaction

- **APK installation** — system confirmation dialog (unavoidable without device-owner)
- **"Install unknown apps" permission** — guided via in-app dialog + settings intent
- **Updates** — same signing key + package name = system shows "Update" instead of "Install"

## License

All rights reserved.

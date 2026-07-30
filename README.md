# First Responder Kit — CPR Metronome

A native Android app that beats out a steady compression rate for CPR training and
practice. Fully offline, no accounts, no ads, no analytics, no internet permission.

> Personal training aid. Not a medical device, and not a substitute for clinical judgement,
> local protocols or hands-on training.

## Download

**[Download the latest APK](https://github.com/shavei/first-responder-kit/releases/latest/download/first-responder-kit.apk)**
— or browse every build on the [releases page](https://github.com/shavei/first-responder-kit/releases/latest).

Android 8.0 (API 26) or newer. It is a sideload, not a Play Store install, so open the file
from your Downloads and allow your browser or file manager to "install unknown apps" when
Android asks. The app requests no permissions beyond vibration and has no internet
permission at all.

Releases are built by [`.github/workflows/release.yml`](.github/workflows/release.yml) —
push a tag and the workflow builds, tests and attaches the APK:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Unless the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`
repository secrets are set, the APK is signed with the debug key. It installs fine, but the
signature is not stable between builds, so upgrading over a previous install will fail until
you uninstall it first. Set those secrets to sign with a real key and updates work normally.

## What it does

- **Home screen** — three full-size buttons (👶 Infant · 🧒 Child · 🧑 Adult) plus Settings.
  The patient type set as the default in Settings is accented.
- **Metronome screen** — large BPM readout inside a circle that pulses on every beat,
  −/+ rate control, and one big Start/Stop button.
- **Every beat** plays a short click, fires a haptic pulse and animates the circle. Sound
  and vibration can each be turned off.
- **Rate** — 110 BPM by default, adjustable from 100 to 120 BPM.
- **Settings** — sound, vibration, keep-screen-awake, default BPM, default patient type
  and theme (system / light / dark). Stored locally with DataStore.

## Building

Requirements: Android Studio (Ladybug or newer) or a JDK 17+ command line, plus the
Android SDK for API 35.

### Android Studio

1. **File → Open…** and select this directory.
2. Let Gradle sync (it downloads the wrapper and the dependencies on first run).
3. Press **Run** with a device or emulator on Android 8.0 (API 26) or newer.

### Command line

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest # unit tests, including the timing tests
./gradlew lintRelease       # static analysis
```

`local.properties` must point at your SDK (Android Studio writes it for you):

```properties
sdk.dir=/path/to/Android/sdk
```

### Signing a release build

Release builds are minified and shrunk with R8. Without a keystore they are signed with
the debug key, which is enough to sideload for personal use. To sign with your own key,
create `keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=../release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## Project layout

```
app/src/main/java/com/firstresponder/kit/
├─ MainActivity.kt           single activity, sets the Compose content
├─ FirstResponderApp.kt      Application; creates the container lazily
├─ AppContainer.kt           manual dependency container (no DI framework)
├─ audio/
│  ├─ ClickSynth.kt          generates the click as raw PCM
│  ├─ ClickPlayer.kt         playback interface
│  ├─ AudioTrackClickPlayer.kt   low-latency AudioTrack implementation
│  └─ MetronomeEngine.kt     drift-free beat scheduler
├─ domain/PatientType.kt     infant / child / adult
├─ settings/                 UserSettings, repository interface, DataStore implementation
├─ util/                     BPM math, haptics, keep-screen-on effect
├─ viewmodel/                MetronomeViewModel, SettingsViewModel
└─ ui/
   ├─ components/            big buttons, pulse circle, BPM stepper, setting rows
   ├─ navigation/            routes, tool registry, NavHost
   ├─ screens/               Home, Metronome, Settings
   └─ theme/                 colours, typography, theme
```

Architecture is MVVM: the view models own state and talk to the engine and the settings
repository; the screens are stateless composables driven by a single UI-state object, each
with a thin stateful "route" wrapper. Nothing in `audio/`, `settings/` or `domain/` depends
on Compose.

## How the timing works

Accuracy is the whole point of the app, so the beat is not scheduled with
`Handler.postDelayed` or a coroutine `delay` loop — both of which accumulate error.

`MetronomeEngine` runs a dedicated `THREAD_PRIORITY_URGENT_AUDIO` thread that:

1. computes each beat time by adding one period to the **ideal** time of the previous beat
   (never to the time it actually woke up), so jitter stays bounded instead of accumulating
   — the grid is still exact after ten minutes;
2. uses the monotonic `System.nanoTime()` clock, immune to wall-clock changes;
3. parks until ~2 ms before the beat and then yields in a tight loop for the final
   approach, which is where sub-millisecond accuracy comes from;
4. re-anchors instead of firing a burst of catch-up beats if the thread is ever starved for
   longer than a whole period.

Changing the BPM while running applies from the next beat and preserves the grid.

`MetronomeEngineTest` runs the engine for real on the JVM and asserts that every beat lands
on the ideal grid, that the sound and vibration switches are independent, and that starting
twice does not spawn a second beat stream.

Audio uses `AudioTrack` in `MODE_STATIC` with `PERFORMANCE_MODE_LOW_LATENCY`, at the
device's native sample rate — the conditions the platform needs before it grants the fast
audio path. `MediaPlayer` is deliberately avoided. The click itself is synthesised at
startup, so there is no asset to decode and no file I/O on the timing path.

The metronome stops when the screen leaves the foreground. There is no foreground service,
so it deliberately does not beat in the background.

## Startup

Cold start is a single frame of work: no splash screen, no eager I/O in `Application`, no
DI graph to build, and the home screen holds no state. Settings load asynchronously and the
UI starts from the defaults; the manifest theme's window background matches the dark colour
scheme, so nothing flashes while that happens.

## Adding another tool

The navigation is already built around a registry so the kit can grow (pulse counter,
respiratory rate timer, GCS calculator, paediatric weight estimator, drug dosage
calculator, trauma reference cards, timers…). To add one:

1. add a `KitTool` entry to `ToolRegistry.tools` in `ui/navigation/Destinations.kt`;
2. add a route constant to `Destinations` and a matching `composable(…)` block in
   `KitNavHost`;
3. write the screen under `ui/screens/` and, if it needs state, a view model in
   `viewmodel/` with a `Factory` that pulls its dependencies from `AppContainer`.

The home screen renders registered tools automatically under the CPR buttons. Nothing else
has to change.

## Tech

Kotlin 2.0 · Jetpack Compose (Material 3) · Navigation Compose · DataStore · AGP 8.7 ·
minSdk 26 · targetSdk 35. Dependency versions are pinned in `gradle/libs.versions.toml`;
the project builds cleanly as pinned, so bump them deliberately rather than on sight.

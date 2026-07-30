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

- **Home screen** — four full-size buttons (🍼 Newborn · 👶 Infant · 🧒 Child · 🧑 Adult)
  plus Settings. The patient type set as the default in Settings is accented.
- **Metronome screen** — large BPM readout inside a circle that pulses on every beat, the
  compression depth, hand technique and compressions-to-breaths ratio for that patient,
  a −/+ rate control, and one big Start/Stop button.
- **Every beat** plays a short click, fires a burst of haptic hits and animates the circle.
  Sound and vibration can each be turned off.
- **Rate** — the patient's protocol decides. 100–120 BPM for an adult, child or infant
  (110 by default, adjustable). A newborn is fixed at 120 *events* per minute — 90
  compressions plus 30 breaths at 3:1 — so the rate control sits disabled there.
- **Vibration strength** — a slider across the vibrator's full range, appearing under the
  vibration toggle only while that toggle is on. Releasing it fires one beat at the chosen
  strength, so it can be set by feel. Full strength by default, where the phone rattles hard
  enough to hear.
- **Settings** — sound, vibration, vibration strength, keep-screen-awake, default BPM,
  default patient type and theme (system / light / dark). Stored locally with DataStore.

## How the timing works

A compression metronome is only worth carrying if the pace it gives is the pace you get, so
the beat is not driven by a timer at all.

- **The audio hardware keeps the tempo.** The clicks are drawn into one continuous audio
  stream at exact frame offsets rather than triggered one at a time, so their spacing is set
  by the output's crystal rather than by thread scheduling. A busy CPU, a garbage collection
  or a thermal throttle cannot change it. Beat positions are computed from the start of the
  session in exact integer arithmetic, so nothing rounds off and accumulates — every beat is
  within half a frame, about ten microseconds, of where it belongs, however long the session
  runs.
- **The three cues are aligned, not merely simultaneous.** A click crosses a buffered audio
  pipeline, a vibration is a binder call plus a motor that takes milliseconds to reach full
  strength, and an animation cannot appear before the next display frame. Fired together they
  arrive tens of milliseconds apart — a fifth of a beat, felt as a smeared double cue. So the
  app asks the platform where the stream really is (`AudioTrack.getTimestamp`), works out the
  instant each beat will be *heard*, and fires every other cue early by its own delivery lag
  so they land on the ear, the skin and the eye at the same moment.
- **The stream keeps running when the sound is off**, because it is the timebase for the
  vibration too — vibrate-only practice is timed by the same clock.
- **A beat that cannot go out on time is dropped, never rushed.** A missing cue is a smaller
  lie about the pace than two cues in quick succession, and every gap the user feels stays at
  least a full period.
- **It is measured.** Every cue is compared against the instant it was scheduled for, and a
  summary — beats, worst error, RMS jitter, the period actually delivered, audio underruns —
  is logged under the `Metronome` tag when the metronome stops, so the accuracy can be checked
  on a real device rather than assumed:

  ```
  adb logcat -s Metronome
  vibration: 118 beats, max 1.83 ms off, rms 0.51 ms, period 545.45 ms, 0 dropped | …
  ```

The timing arithmetic — the beat grid, the audio clock and the statistics — is plain Kotlin
with no Android types, and is covered by unit tests that run on the JVM: no accumulated drift
over an hour at every supported rate, no gap shortened by a rate change, and each cue fired
early by exactly its own lag.

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
├─ domain/PatientType.kt     newborn / infant / child / adult, with their BLS values
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
on the ideal grid, that the sound and vibration switches are independent, that the configured
vibration strength reaches every pulse and follows a mid-session change, and that starting
twice does not spawn a second beat stream.

## How the vibration works

The beat is an explicit `VibrationEffect` waveform, **not** `VibrationEffect.EFFECT_CLICK`. The
predefined effects are the platform's UI haptics: fixed strength, tuned for a tick in the hand
while you are looking at the screen, and far too faint to feel during compressions. Only a
hand-built waveform exposes the motor's full range, so that is what the strength slider drives
— 1 to 255, the platform's own amplitude scale, stored directly rather than as a percentage so
the slider's ends really are the device's minimum and maximum.

Amplitude alone runs out of road at 255, and past that the way to make a beat hit harder is to
hit *more often*. So a beat is not one flat pulse but a **burst**: up to four hits, each held
long enough to reach full excursion, separated by 18 ms of silence. A vibration motor is a mass
on a spring — held at a constant drive it settles into a steady hum the hand stops noticing
within a few tens of milliseconds, whereas cutting the drive lets the mass swing back so the
next hit lands as a fresh impact. Those repeated transients are what the skin reads as a *hit*
and what the case actually radiates as sound, which is why a burst is heard across a room while
a continuous buzz of the same amplitude is not. The strength slider drives all three knobs
together: number of hits, length of each hit and amplitude. At maximum the burst runs 234 ms,
still leaving 266 ms of silence before the next beat at the 120 BPM maximum, so beats never run
into one another — `VibrationPatternTest` asserts that.

Devices whose motor cannot vary its strength (`Vibrator.hasAmplitudeControl()` is false) ignore
the amplitude argument entirely. There the burst alone carries the setting — more hits, held
longer, being the only remaining way to make a beat more noticeable — and the settings screen
says so. The slider therefore does something on every device.

The whole beat also declares **alarm** usage rather than sonification. The system scales haptics
by the usage they declare, and a sonification pulse is treated as a UI tick: attenuated by the
touch-feedback intensity setting and, on some devices, suppressed outright under Do Not Disturb.
Alarm usage is scaled by the alarm intensity and is exempt from that suppression, so the same
amplitude arrives at the motor considerably stronger.

`VibrationEffect` instances are cached and rebuilt only when the strength changes, which keeps
the promise that nothing on the timing thread allocates: within a session the strength is
constant, so every beat after the first reuses one object.

Audio uses `AudioTrack` in `MODE_STATIC` with `PERFORMANCE_MODE_LOW_LATENCY`, at the
device's native sample rate — the conditions the platform needs before it grants the fast
audio path. `MediaPlayer` is deliberately avoided. The click itself is synthesised at
startup, so there is no asset to decode and no file I/O on the timing path.

The click declares **alarm** usage too, for the same reason the vibration does and for one
more: a track that declares sonification is routed to the system stream, which the platform
force-mutes whenever the ringer is on silent or vibrate. On a phone kept on silent — which
is most of them — the metronome was therefore completely inaudible, with the vibration
still working and nothing on screen to explain the silence. The alarm stream ignores the
ringer mode and is exempt from Do Not Disturb's default policy. It also carries its own
volume, so the activity sets `volumeControlStream` to it and the hardware keys adjust the
click from anywhere in the app rather than moving a media volume nothing here uses.

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

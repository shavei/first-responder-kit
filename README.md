# First Responder Kit

A native Android app for BLS practice: a metronome that beats out a steady compression
rate, and an oxygen reference that works out how long the cylinder lasts. In English and
Hebrew. Fully offline, no accounts, no ads, no analytics, no internet permission.

> Personal training aid. Not a medical device, and not a substitute for clinical judgement,
> local protocols or hands-on training.

## Download

**[Download the latest APK](https://github.com/shavei/first-responder-kit/releases/latest/download/first-responder-kit.apk)**
— or browse every build on the [releases page](https://github.com/shavei/first-responder-kit/releases/latest).

Android 8.0 (API 26) or newer. It is a sideload, not a Play Store install, so Android asks
for confirmation twice on the way in. Both prompts are normal and neither means anything
was found in the app — it requests no permissions beyond vibration and has no internet
permission at all.

### Installing it, prompt by prompt

1. Tap the download link, then open the file from your browser's downloads or the Files app.
2. **"…is not allowed to install unknown apps"** — tap **Settings** in that prompt, turn the
   permission on for whichever app you opened the APK from, and come back. Android asks this
   once per app, per device.
3. **"App blocked to protect your device"** — tap **More details**, then **Install anyway**.
   The collapsed dialog shows only **Got it**; the install button is behind *More details*,
   which is easy to miss.
4. If step 3 offers no **Install anyway** at all, Play Protect is set to block outright
   rather than warn. Open **Play Store → your profile → Play Protect → ⚙ → Scan apps with
   Play Protect**, turn it off, install, then **turn it back on**. It is the only scanner
   most phones have, so leaving it off is not the trade to make.

### If Android refuses the install

- **"For your security, your phone is not allowed to install unknown apps from this
  source."** The ordinary sideload prompt, not an error. Tap the link in it, turn the
  permission on for whichever app you opened the APK from — the browser that downloaded
  it, or your file manager — and go back. Android asks this once per app, per device.
- **"App not installed", or an installer complaining that the signature does not match.**
  The build you have was signed with a different key from the one you are installing.
  Uninstall the app and install again; nothing in it is stored outside the app, so only
  the settings are lost. Releases up to **v1.6.0** each carried a different throwaway key,
  so this hit every upgrade between them — from v1.6.1 on the key is stable and upgrades
  install straight over the top.
- **"App blocked to protect your device — Play Protect hasn't seen an app from this
  developer before."** Not a verdict on the build: Play Protect scores the signing key's
  reputation, and a key only earns one by being installed at scale through a store, so
  everything sideloaded starts unknown. Tap **More details**, then **Install anyway**.
  Every release lists the APK's SHA-256 and its signing certificate's SHA-256; check the
  download against them first if you want to be sure it is the file this repository's
  workflow built. See [Developer verification](#developer-verification) for what removes
  the warning rather than clicking past it.

Releases are built by [`.github/workflows/release.yml`](.github/workflows/release.yml) —
push a tag and the workflow builds, tests, verifies the signature and attaches the APK:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

The workflow needs the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD` repository secrets, and fails without them rather than publishing an APK
signed with the runner's debug key. That key is generated fresh on each runner, so such a
build cannot be installed over any other. Run
[`./tools/make-release-key.sh`](tools/make-release-key.sh) once: it creates the keystore
and prints the four values to paste under **Settings → Secrets and variables → Actions**.
Back the keystore up — Android identifies the app by that key, and losing it means every
future release has to be installed fresh.

### Developer verification

Play Protect's "hasn't seen an app from this developer before" block, and the mandatory
registration Google is phasing in, are the same question: is this signing key tied to a
verified developer? For a key that only ever appears on GitHub Releases, the answer is no,
so the block stands however clean the app is. Two things change that, both keyed to the
certificate `make-release-key.sh` created — one more reason it has to outlive every
release:

- **A limited distribution account on the [Android Developer Console](https://developer.android.com/developer-verification)**,
  registering `com.firstresponder.kit` against that certificate. It is free, and it caps at
  20 devices the installer explicitly authorises. For a kit carried by its author and a
  couple of colleagues that cap is not a constraint, so this is the path this project
  wants — a handful of devices is exactly the shape the tier was written for.
- **Publish through Google Play**, including a closed or internal test track. Play scans
  the binary and vouches for the developer, which is what actually retires the Play
  Protect warning; verification comes with it. This is what a public download link needs,
  and it is more account than a two-person kit calls for. If it is ever done, upload
  *this* keystore as the app signing key rather than letting Play generate one, otherwise
  the Play build and the GitHub APK carry different signatures and neither installs over
  the other.

Registration is not urgent yet. Enforcement begins 30 September 2026 in Brazil, Indonesia,
Singapore and Thailand only, expanding globally through 2027; on a device outside those
regions an unregistered app still installs today, warning and all. What ends is the
warning, not the ability to install.

For your own devices there is also a route with no dialogs at all: `adb install
first-responder-kit.apk` over USB, with developer options on. ADB is deliberately exempt —
Google keeps it as the escape hatch for developers, and it stays exempt after verification
is enforced. It needs a computer, so it suits the phones you own rather than anyone you
send a link to.

None of these is a code change, and there is no manifest flag, permission or build setting
that substitutes for them. The app already declares one permission and no internet access,
so there is nothing left to remove: it holds none of the permissions — `RECEIVE_SMS`,
`READ_SMS`, `NOTIFICATION_LISTENER`, `ACCESSIBILITY` — that Google documents as what makes
Play Protect block a sideload outright. The block here is about the key, not the build.

Debug signing is not the way out either, though it looks like one. A debug-signed APK
carries `CN=Android Debug, O=Android, C=US`, a placeholder shared by a vast number of
builds, and sidesteps the unknown-developer check by having no developer identity to be
unknown — which is why some sideloaded apps install with only a scan prompt. It costs the
things this project needs: the debug keystore's password is public and its format is
fixed, so anyone could sign an APK that Android accepts as an update to this one, and no
verified account can ever be tied to a certificate the whole world shares. Telling users to
switch Play Protect off is not the answer either — it is the only scanner most of them
have.

## What it does

- **Home screen** — four full-size buttons (🍼 Newborn · 👶 Infant · 🧒 Child · 🧑 Adult),
  the other tools in the kit, and Settings. The patient type set as the default in Settings
  is accented.
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
- **Oxygen** — what each device delivers (mask or bag-valve mask, with or without a
  reservoir bag, at 10–15 L/min), a calculator for how long a cylinder has left —
  pressure × cylinder volume ÷ flow, over the 20 L and 2.4 L cylinders — and the handling
  rules that go with a cylinder of oxygen.
- **Home-screen widget** — a 1×1 tile, resizable in both directions, where everything is
  chosen by you: what it opens (including a metronome that is already beating), what it
  shows, and what it looks like. See [The home-screen widget](#the-home-screen-widget).
- **Language** — English or Hebrew, switchable in Settings and applied on the spot, with
  the layout mirrored for Hebrew. Following the device's own language is the default.
- **Settings** — sound, vibration, vibration strength, keep-screen-awake, default BPM,
  default patient type, theme (system / light / dark) and language. Stored locally with
  DataStore.

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

Release builds are minified and shrunk with R8. Without a keystore they fall back to the
debug key, which is enough to put a build on your own device but not to give to anyone:
that key is per-machine, and Android will not install an APK over one signed with a
different key. Generate a real key once —

```bash
./tools/make-release-key.sh
```

— and write the `keystore.properties` it prints into the project root (git-ignored):

```properties
storeFile=release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Check what a built APK is actually signed with:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

`CN=Android Debug` in that output means the keystore was not picked up.

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
├─ domain/
│  ├─ PatientType.kt         newborn / infant / child / adult, with their BLS values
│  └─ Oxygen.kt              delivery devices and cylinder sizes
├─ settings/                 UserSettings, repository interface, DataStore implementation
├─ util/                     BPM math, oxygen duration, haptics, keep-screen-on effect
├─ viewmodel/                MetronomeViewModel, SettingsViewModel
├─ widget/
│  ├─ WidgetConfig.kt        what one home-screen widget is: action, display, colours
│  ├─ WidgetLayout.kt        fits the glyph, value and caption into the space there is
│  ├─ WidgetRenderer.kt      builds the RemoteViews the launcher is sent
│  ├─ KitWidget.kt           the AppWidgetProvider
│  ├─ WidgetStore.kt         per-widget settings, kept where a broadcast can read them
│  └─ WidgetConfigActivity.kt   the configuration screen's host
└─ ui/
   ├─ AppLocale.kt           applies the chosen language to the whole tree
   ├─ components/            big buttons, pulse circle, steppers, setting rows
   ├─ navigation/            routes, tool registry, NavHost
   ├─ screens/               Home, Metronome, Oxygen, Settings, widget configuration
   └─ theme/                 colours, typography, theme

app/src/main/res/
├─ values/strings.xml        English (the default)
└─ values-iw/strings.xml     Hebrew — `iw`, the code Android reports Hebrew locales under
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

## The home-screen widget

One widget, one cell, and nothing about it fixed. Long-press the home screen → **Widgets** →
**First Responder Kit**, and the configuration screen decides:

- **What tapping it does** — open the kit, open the metronome, *start compressions* (the
  metronome is already beating by the time you are looking at it), open oxygen, or open
  settings. The metronome actions carry their own patient type and their own rate, which
  can follow the default in Settings or be pinned to anything that patient's protocol
  allows.
- **What it shows** — one of six glyphs or none, a value (the rate, the patient emoji, or
  the compressions-to-breaths ratio), and a caption that is either the name of the action
  or whatever you type.
- **What it looks like** — background and ink from an eight-colour palette, ink on
  automatic if you would rather it work out its own contrast, opacity from opaque to
  invisible, corner rounding from a square tile to a circle, and text size. Rounding runs
  0–100%, and 100% is a circle rather than a rounded rectangle: the radius that rounds a
  square into a circle only rounds the ends of an oblong into a lozenge, so at the top of
  the slider the disc is inscribed in the cell and centred instead. Launcher cells are
  rarely square.

It is placed as 1×1 and resizes freely in both directions from a single cell up. The layout
is not a set of breakpoints: every element is a share of the shorter side, so a big widget
is the same widget drawn bigger, and anything that would end up too small to read is
dropped — caption first, then value — which is what lets a single cell be a glyph and
nothing else without you having to turn the caption off. `WidgetLayoutsTest` covers that
end of it.

Several widgets can sit side by side with entirely different settings; each is stored
against its own widget id and deleted with it. An existing widget can be reconfigured
later — long-press it and pick the reconfigure affordance your launcher offers — and
backing out of the screen leaves it exactly as it was.

The preview at the top of the configuration screen is not a mock-up. It is the same
`RemoteViews` the launcher is sent, inflated in place at 1×1, 2×1 or 2×2, so what you are
looking at is what will land on the home screen.

Three things are worth knowing about the implementation, all in `widget/`:

- **No background work.** `updatePeriodMillis` is zero and there is no service. Nothing a
  widget shows changes on its own, so it is redrawn when it is placed, resized or
  reconfigured, and when the app is left — which is the only moment a setting it mirrors
  can have changed underneath it.
- **A widget is drawn by the launcher**, in the launcher's process and the *device's*
  locale. This app picks its language itself, so the provider renders through a context
  localised to the language the app was last in, mirrored into the widget's own store
  alongside the default rate.
- **Colours, sizes and rounding are baked into two small bitmaps** — the background and the
  tinted glyph — because the set of `RemoteViews` calls that behaves identically from API
  26 to today is narrow, and "configurable" has to mean it works everywhere rather than
  only on the newest phones.

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
has to change — the oxygen tool is exactly those three steps, and is worth reading as the
worked example.

Anything with user-visible text needs both `values/strings.xml` and `values-iw/strings.xml`
— lint reports any string that has only one of them.

## Tech

Kotlin 2.0 · Jetpack Compose (Material 3) · Navigation Compose · DataStore · AGP 8.7 ·
minSdk 26 · targetSdk 35. Dependency versions are pinned in `gradle/libs.versions.toml`;
the project builds cleanly as pinned, so bump them deliberately rather than on sight.

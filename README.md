# Lucky Charm (Android)

A floating lucky-charm overlay that hangs from the top of your screen over
other apps — pick a charm, drag it anywhere along the top edge, tap it for a
little "ritual" animation, and it sways with real spring physics the rest of
the time.

This is an **original** app inspired by the general idea of a desktop
menu-bar charm app, built from scratch with its own artwork (five simple
Canvas-drawn charms: clover, star, horseshoe, moon, ladybird), its own code,
and no shared assets, icons, or branding from any existing product.

## How it works

- **MainActivity** — requests the `SYSTEM_ALERT_WINDOW` ("Display over other
  apps") permission, lets you pick a charm from a grid, and starts/stops the
  overlay service.
- **CharmOverlayService** — a foreground service that adds a small
  `WindowManager` overlay window pinned to the top of the screen. Runs as a
  foreground service (with a persistent low-priority notification, required
  on Android 8+) so the overlay survives while other apps are in use.
- **CharmView** — a custom `View` that simulates the hanging thread as a
  **multi-segment Verlet rope** (not a single rigid rod): ~10 connected
  points, each pulled by gravity and relaxed against its neighbors by a
  distance constraint every frame via `Choreographer`. This is what gives
  the thread its whip-like curve — a flick travels down the rope as a wave
  instead of the whole thing snapping around one pivot, matching how the
  reference site's charm actually moves. Grabbing the charm moves its rope
  point directly to your finger; letting go leaves real momentum baked in
  (Verlet doesn't need explicit velocity tracking — the previous frame's
  position *is* the velocity), so it flies off and settles naturally. A
  separate grab zone near the top peg lets you re-hang the whole charm
  elsewhere along the top edge. A gentle sinusoidal "wind" force keeps it
  swaying at rest instead of ever looking frozen.
- **CharmType** — five original hand-drawn charm designs as Kotlin
  `Canvas`/`Path` code (no image assets needed): clover, star, horseshoe,
  moon, ladybird, and a heart ("Love Dangle") whose ritual is a quick
  double heartbeat pulse.

## Opening the project

1. Open this folder (`LuckyCharm/`) in Android Studio (Koala or newer).
2. Let Gradle sync — it will generate the Gradle wrapper automatically if
   your Android Studio is configured to do so; otherwise run
   `gradle wrapper` once from a terminal with Gradle installed, or just use
   Android Studio's "Run" button, which manages the wrapper for you.
3. Run on a device or emulator running **Android 8.0 (API 26)** or newer.
4. On first run, tap **Grant overlay permission**, allow "Display over other
   apps" in the system settings screen, come back, pick a charm, and tap
   **Hang it up**.

## Things you might want to extend

- Add more charm designs to `CharmType.kt` (each one is just a `draw()`
  function using `Canvas`/`Path` — no external art pipeline needed).
- Persist the chosen charm and its screen position with `SharedPreferences`
  so it's restored after a reboot (hook into `RECEIVE_BOOT_COMPLETED`,
  already declared in the manifest but not yet wired up).
- Add a Quick Settings Tile to toggle the charm on/off without opening the
  app.
- Swap the tap "ritual" for something per-charm (e.g. the star does a full
  spin, the ladybird's wings fully open) the same way the reference concept
  gives each charm its own small interaction.
- Let users pick a custom color or upload their own emoji/image as a charm.

## Permissions used

- `SYSTEM_ALERT_WINDOW` — to draw the floating charm over other apps.
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` — required on
  modern Android to keep the overlay alive.
- `POST_NOTIFICATIONS` — for the required foreground-service notification on
  Android 13+.

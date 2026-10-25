# Bear

A small bear that lives on your phone. It wanders, plays fetch, eats, sleeps and sometimes
gets worried; pat it and it lets out a big relieved sigh.

This is the interface-first build: the app, its screens and the full behaviour from
`bear-prototype.html`, drawn with flat placeholder shapes in the LED palette. The dot-matrix
renderer and the Glyph Toy come later and will plug into the same `core` code.

## Open and run

1. Open the `bear/` folder in Android Studio (Narwhal or newer).
2. Let Gradle sync. Studio's bundled JDK 21 is fine.
3. Run the `app` configuration on a phone or an Android 14+ emulator.

Command line: `./gradlew :app:installDebug` and `./gradlew :core:test`.

## Modules

| Module | What it is |
|:--|:--|
| `core` | Pure Kotlin, no Android. The simulation (`BearSim`, ported from the prototype's `step()`), the bear's layout (`BearGeometry`, from `build()`), the floor projection, and every tuning number in `Tuning.kt`. Unit tested. |
| `app` | Compose app. Scene screen, character sheet, placeholder bear drawing, input. |

Planned: `glyph` (Glyph Toy service) once the dot-matrix look is in.

## Screens

- **Scene**: the bear wanders the floor with depth. Tap it to pat; swipe the ball up from
  the bottom to throw; Feed drops a berry. The HUD shows happiness, energy and health.
  The debug line under the scene shows the current mode, mood and stats.
- **Character sheet**: cub and adult in every mood and pose at N = 52, 25 and 13, animated.

## Notes

- Timing is still the prototype's (everything fast). Real-time stats and saving come in the
  behaviour and persistence milestone.
- The bear is laid out with the same geometry the rasteriser will use, so its size and
  position on screen won't change when the dot matrix arrives.

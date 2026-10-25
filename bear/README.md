# Bear

A small bear that lives on your phone. It wanders, plays fetch, eats, sleeps and sometimes
gets worried; pat it and it lets out a big relieved sigh.

The app, its screens and the full behaviour from `prototype/bear-prototype.html`, drawn as
the 120 × 200 LED dot matrix. The Glyph Toy comes later and will use the same `core` code.

## Open and run

1. Open the `bear/` folder in Android Studio (Narwhal or newer).
2. Let Gradle sync. Studio's bundled JDK 21 is fine.
3. Run the `app` configuration on a phone or an Android 14+ emulator.

Command line: `./gradlew :app:installDebug` and `./gradlew :core:test`.

## Modules

| Module | What it is |
|:--|:--|
| `core` | Pure Kotlin, no Android. The simulation (`BearSim`, from the prototype's `step()`), the bear rasteriser (`BearRaster`, from `build()`), the scene rasteriser (`SceneRaster`, from `render()`), and every tuning number in `Tuning.kt`. Unit tested. |
| `app` | Compose app. Dot-matrix renderer, scene screen, character sheet, input. |

Planned: `glyph` (Glyph Toy service) once the dot-matrix look is in.

## Screens

- **Scene**: the bear wanders the floor with depth. Tap it to pat; swipe the ball up from
  the bottom to throw; Feed drops a berry. The HUD shows happiness, energy and health.
  The debug line under the scene shows the current mode, mood and stats.
- **Character sheet**: cub and adult in every mood and pose at N = 52, 25 and 13, animated.

## Matching the prototype

`tools/prototype-frames.mjs` runs the prototype in Node and writes reference data to
`core/src/test/resources/prototype/`: 1,620 bears (cub and adult, every mood and pose,
sizes from 13 to 52) and a scripted 2,600-tick session with feeding, pats, worry, sighs,
throws, sleep and a tired bear. `PrototypeMatchTest` checks the Kotlin port reproduces every
bear and every scene frame dot for dot. Re-run the script if the prototype changes.

## Notes

- Timing is still the prototype's (everything fast). Real-time stats and saving come in the
  behaviour and persistence milestone.

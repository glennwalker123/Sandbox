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

## Colour

Everything is the four LED colours, plus tiny neon hints from `core/Tint.kt`: pink hearts
(floating and the happiness icon), an amber energy bolt, a mint health cross, a cyan sweat
drop, lilac sleep Zs, and the coloured part of each food (berry flesh, apple leaf, honey
label). Each tint has a lit and a dim shade. Fur, bars and text stay plain. The Glyph
Matrix mirror stays monochrome, like the hardware.

## Screens

- **Scene**: the bear wanders the floor with depth. Tap it to pat. The HUD across the top
  shows happiness, energy and health on one line.
- **Care button**: the raised paw button under the screen. Press and hold to fan out the
  menu, slide onto an option and let go; or tap to open it and tap an option. Options:
  play fetch (puts the ball in your hand to swipe up and throw; again to put it away) and
  four foods (berry, apple, fish, honey), each with its own effect in `Tuning.FOOD`.
  The menu's contents live in `core/CareMenu.kt`, shared with the web preview.
- **Activities**: Talk and Fetch are modes. Choosing one from the care menu puts a title
  bar across the top of the LED screen (lit borders, dim stripes, the name in a 5 × 7 dot
  font, a close box in the corner; `core/ModeBar.kt`) and swaps the paw button for that
  mode's controls. Tap the close box to leave. Leaving fetch mid-throw puts the ball away
  once the bear brings it back. Food stays a one-off.
- **Fetch**: the ball sits in your hand; swipe it up to throw. Under the screen, a tally
  of throws and catches.
- **Talk**: in the care menu. The bear's words appear on the LED screen in Doto, a
  dot-matrix monospace; you type underneath or tap a suggested reply. Claude writes the
  bear's side (`core/BearTalk.kt` holds its personality, what it knows about its own state,
  and the reply format). Kind replies make it hop; reassurance settles a worried bear.
  The app needs a Claude API key (Debug → Claude key), stored only on the phone. This is
  the one network call the app makes. Health data is never part of it. The web preview talks through your claude.ai account.
- **Health Connect**: your own sleep and steps feed the bear (`core/HealthLink.kt`). A
  night's sleep gives energy once per night (up to +45 at 8 hours); steps add health through
  the day (up to +40 at 8,000). Rewards only: a short night or a quiet day gives less, never a
  penalty. Read on the phone through Android's built-in Health Connect (READ_SLEEP and
  READ_STEPS); nothing is sent anywhere, including to Claude. Under the screen: last night's
  sleep, today's steps, and what they added. The web preview has sliders to simulate it.
- **Debug row**: mode, mood and stats, plus Make it worry, cub/adult, the character sheet
  and the Claude key.
- **Character sheet**: cub and adult in every mood and pose at N = 52, 25 and 13, animated.

## Web preview

`web/` compiles the same `core` code to JavaScript and wraps it in one self-contained page
with the scene, the controls, the Glyph mirror and the character sheet:

    ./gradlew -p web preview    # writes web/build/preview/bear-preview.html

Open the file in any browser. It's a separate build so the Android project stays JVM-only.

## Matching the prototype

`tools/prototype-frames.mjs` runs the prototype in Node and writes reference data to
`core/src/test/resources/prototype/`: 1,620 bears (cub and adult, every mood and pose,
sizes from 13 to 52) and a scripted 2,600-tick session with feeding, pats, worry, sighs,
throws, sleep and a tired bear. `PrototypeMatchTest` checks the Kotlin port reproduces every
bear and every scene frame dot for dot. Re-run the script if the prototype changes.

## Notes

- `app/src/main/res/font/doto_black.ttf` is Doto by the Doto Project Authors, under the
  SIL Open Font License (`third_party/Doto-OFL.txt`).

- Timing is still the prototype's (everything fast). Real-time stats and saving come in the
  behaviour and persistence milestone.

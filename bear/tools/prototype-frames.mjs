// Runs bear-prototype.html headlessly and writes reference data for the core tests:
//   bears.txt  every bear the prototype's build() makes for a set of looks and sizes
//   scene.txt  a scripted session: actions, every Math.random() value used, a hash of
//              each 120 × 200 frame and a few full frames
//
// Usage: node tools/prototype-frames.mjs   (from the bear/ folder)

import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";

const root = path.dirname(path.dirname(new URL(import.meta.url).pathname));
const html = fs.readFileSync(path.join(root, "prototype/bear-prototype.html"), "utf8");
const source = html.slice(html.indexOf("<script>") + 8, html.lastIndexOf("</script>"));
const outDir = path.join(root, "core/src/test/resources/prototype");
fs.mkdirSync(outDir, { recursive: true });

// ---- Minimal DOM so the script runs ----
let now = 0;
const randoms = [];
let seed = 0x2f6b1d3;
function random() {
  // mulberry32
  seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  const v = ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  randoms.push(v);
  return v;
}
const ctx = new Proxy({}, { get: (o, k) => (k in o ? o[k] : () => {}), set: (o, k, v) => ((o[k] = v), true) });
function element() {
  const listeners = {};
  return {
    listeners,
    width: 0, height: 0, textContent: "",
    getContext: () => ctx,
    addEventListener: (type, fn) => (listeners[type] = fn),
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 120, height: 200 }),
    setPointerCapture: () => {},
  };
}
const elements = {};
const document = {
  getElementById: (id) => (elements[id] ??= element()),
  createElement: () => element(),
};
const context = vm.createContext({ document, setInterval: () => {}, performance: { now: () => now }, __random: random });
vm.runInContext(
  "Math.random = __random;\n" + source + `
globalThis.__api = {
  G, step, build,
  setEnergy: (v) => { en = v; },
  state: () => ({ mode, hap, en, hp, wx, wz, tick }),
};`,
  context,
);
const api = context.__api;

// ---- bears.txt ----
const bears = [];
const looks = [];
for (const cub of [true, false])
  for (const st of ["calm", "happy", "worried", "sleepy", "relieved"])
    for (const N of [52, 44, 25, 21, 20, 13])
      for (const t of [0, 5, 12])
        for (const o of [{}, { back: 1 }, { carry: 1 }, { step: 1 }, { step: 2, look: 1 }, { look: -1 }, { hAdj: 1 }, { hAdj: -1, puff: 1 }, { hAdj: -1, puff: 2 }])
          looks.push({ cub, st, N, t, o });
for (const { cub, st, N, t, o } of looks) {
  const b = api.build(cub, N, st, t, { ...o });
  const opts = Object.entries(o).map(([k, v]) => `${k}=${v}`).join(",") || "-";
  const rows = [];
  for (let y = 0; y < N; y++) rows.push(Array.from(b.g.slice(y * N, y * N + N)).join(""));
  bears.push(`${cub ? "cub" : "adult"} ${st} ${N} ${t} ${opts} ${b.c} ${b.bottom} ${b.hw}\n${rows.join("\n")}`);
}
fs.writeFileSync(path.join(outDir, "bears.txt"), bears.join("\n") + "\n");

// ---- scene.txt ----
const lcd = elements.lcd.listeners;
const toScene = (x, y) => [(x - 0) / 120 * 120, (y - 0) / 200 * 200];
function pointer(type, x, y, t) {
  now = t;
  lcd[type]({ clientX: x, clientY: y, pointerId: 1 });
  const [ux, uy] = toScene(x, y);
  return `${type} ${ux} ${uy} ${t}`;
}
function throwBall(t0) {
  return [
    pointer("pointerdown", 60, 186, t0),
    pointer("pointermove", 60, 170, t0 + 30),
    pointer("pointermove", 64, 140, t0 + 60),
    pointer("pointerup", 66, 120, t0 + 80),
  ];
}
function tap(x, y, t0) {
  return [pointer("pointerdown", x, y, t0), pointer("pointerup", x, y, t0 + 60)];
}
const script = {
  30: () => (elements.feed.listeners.click(), ["feed"]),
  200: () => (elements.glyph.listeners.click(), ["pat"]),
  260: () => (elements.worry.listeners.click(), ["worry"]),
  300: () => (elements.glyph.listeners.click(), ["pat"]),
  420: () => throwBall(420 * 60),
  520: () => tap(40, 150, 520 * 60),
  700: () => (elements.age.listeners.click(), ["age"]),
  720: () => (elements.feed.listeners.click(), ["feed"]),
  900: () => throwBall(900 * 60),
  1090: () => (elements.glyph.listeners.click(), ["pat"]),
  1140: () => (api.setEnergy(2), ["energy 2"]),
  1450: () => (elements.worry.listeners.click(), ["worry"]),
  1460: () => (elements.glyph.listeners.click(), ["pat"]),
  1520: () => (api.setEnergy(20), ["energy 20"]),
  1530: () => throwBall(1530 * 60),
  1650: () => (api.setEnergy(90), ["energy 90"]),
  1700: () => throwBall(1700 * 60),
  1850: () => throwBall(1850 * 60),
  2000: () => throwBall(2000 * 60),
  2150: () => throwBall(2150 * 60),
  2300: () => throwBall(2300 * 60),
  2450: () => throwBall(2450 * 60),
};
const TICKS = 2600;
const fullAt = new Set([1, 40, 300, 320, 440, 470, 760, 1160, 1460, 1560, 1720, 2200, 2600]);
const actions = [];
const modes = new Set();
const hashes = [];
const full = [];
randoms.length = 0;
for (let tick = 1; tick <= TICKS; tick++) {
  if (script[tick]) for (const a of script[tick]()) actions.push(`${tick} ${a}`);
  api.step();
  modes.add(api.state().mode);
  let h = 0x811c9dc5;
  for (const v of api.G) h = Math.imul(h ^ v, 0x01000193) >>> 0;
  hashes.push(h.toString(16));
  if (fullAt.has(tick)) {
    const rows = [];
    for (let y = 0; y < 200; y++) rows.push(Array.from(api.G.slice(y * 120, y * 120 + 120)).join(""));
    full.push(`frame ${tick}\n${rows.join("\n")}`);
  }
}
fs.writeFileSync(
  path.join(outDir, "scene.txt"),
  [
    `ticks ${TICKS}`,
    `actions ${actions.length}`, ...actions,
    `randoms ${randoms.length}`, ...randoms.map(String),
    `hashes`, hashes.join(" "),
    ...full,
  ].join("\n") + "\n",
);
console.log(`bears: ${looks.length}, scene: ${TICKS} ticks, ${actions.length} actions, ${randoms.length} randoms, modes: ${[...modes].join(" ")}`);

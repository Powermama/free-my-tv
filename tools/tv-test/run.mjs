// End-to-end checks on a real TV over adb: one walk through the screens, as a person would press the remote. Each step
// presses keys, waits until the expected screen is in front (polling, no fixed sleeps), takes a screenshot and compares
// it with the approved one (goldens.local/, per TV model); then it measures the home screen's jank and memory. It only
// looks: it never opens an app, and stops at once if anything else comes to the front.
//
//   TV=192.168.1.50 npm test              # compare with the goldens
//   TV=192.168.1.50 npm run update-goldens  # approve what the TV shows now
//
// Injected keys skip accessibility key filters (RemoteKeys), so the remote's app buttons can't be tested here.
import { execFileSync } from "node:child_process";
import { connect } from "node:net";
import { mkdirSync, existsSync, copyFileSync, writeFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const { compare } = createRequire(import.meta.url)("odiff-bin");
const here = dirname(fileURLToPath(import.meta.url));
const serial = process.env.TV ? (process.env.TV.includes(":") ? process.env.TV : `${process.env.TV}:5555`) : null;
const update = process.env.UPDATE_GOLDENS === "1";

const HOME = "io.freemytv/.HomeActivity";
const PANEL = "io.freemytv/.QuickPanelActivity";
const SETTINGS = "io.freemytv/.SettingsActivity";

// Regions that change by themselves (1920×1080): left out of every comparison.
const CLOCK_WEATHER = { x1: 60, y1: 290, x2: 1100, y2: 720 };      // date, clock, weather, prices
const SETTINGS_LIVE = { x1: 1000, y1: 590, x2: 1900, y2: 1060 };   // measured numbers, notices with their age

// One walk, each step from where the last one left off. Home first: the top buttons from there are Up, then all the
// way left (Live TV, or the HDMI input it opens).
const STEPS = [
  { name: "home", keys: ["HOME"], expect: HOME, mask: [CLOCK_WEATHER] },
  { name: "top-live-focus", keys: ["DPAD_UP", "DPAD_LEFT", "DPAD_LEFT", "DPAD_LEFT"], expect: HOME, mask: [CLOCK_WEATHER] },
  { name: "hdmi-focus", keys: ["DPAD_RIGHT"], expect: HOME, mask: [CLOCK_WEATHER] },
  { name: "hdmi-popover", keys: ["DPAD_CENTER"], expect: PANEL, mask: [CLOCK_WEATHER] },
  { name: "hdmi-popover-closed", keys: ["BACK"], expect: HOME, mask: [CLOCK_WEATHER] },
  { name: "settings", keys: ["DPAD_RIGHT", "DPAD_CENTER"], expect: SETTINGS, mask: [SETTINGS_LIVE] },
];
const SETTLE_MS = 700;   // the springs' longest move, after the screen is in front

function adb(...args) {
  return execFileSync("adb", serial ? ["-s", serial, ...args] : args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
}
const shell = (cmd) => adb("shell", cmd);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
// Keys go through Android's monkey server (monkey --port): a few ms each, where every `input keyevent` call starts a
// new Java process (~0.8 s). Without it, `input keyevent` still works, just slower.
const MONKEY_PORT = 1080;
let monkey = null;
async function startMonkey() {
  try {
    // A monkey left by an earlier run may still hold the port while it shuts down: stop it, start ours, and use it
    // only once it answers.
    shell(`pkill -f "[c]om.android.commands.monkey"; monkey --port ${MONKEY_PORT} >/dev/null 2>&1 &`);
    adb("forward", `tcp:${MONKEY_PORT}`, `tcp:${MONKEY_PORT}`);
    for (let i = 0; i < 20 && !monkey; i++) {
      await sleep(150);
      monkey = await new Promise((ok) => {
        const sock = connect(MONKEY_PORT, "127.0.0.1", () => ok(sock)).on("error", () => ok(null));
      });
      if (monkey && !(await send("sleep 0").then(() => true, () => false))) monkey = monkey.destroy() && null;
    }
  } catch {
    monkey = null;
  }
}
function send(line) {
  return new Promise((ok, fail) => {
    const timer = setTimeout(() => fail(new Error(`monkey: no answer to ${line}`)), 3000);   // keeps the process alive too
    monkey.once("data", (d) => {
      clearTimeout(timer);
      String(d).startsWith("OK") ? ok() : fail(new Error(`monkey: ${d}`));
    });
    monkey.write(line + "\n");
  });
}
async function keys(list) {
  if (!monkey) return list.length && shell(`input keyevent ${list.map((k) => "KEYCODE_" + k).join(" ")}`);
  for (const k of list) await send(`press KEYCODE_${k}`);
}
const front = () => (shell("dumpsys activity activities").match(/mResumedActivity: ActivityRecord\{\S+ u0 (\S+)/) || [])[1] || "?";

/** Polls until `want` is in front (or 5 s), then lets the animation settle. Returns what is in front. */
async function waitFor(want) {
  let now = front();
  for (const end = Date.now() + 5000; now !== want && Date.now() < end; now = front()) await sleep(150);
  await sleep(SETTLE_MS);
  return now;
}

// The TV's screencap prints vendor debug text on stdout, so the PNG goes through a file on the TV (removed at the end).
const SHOT = "/data/local/tmp/tv-test.png";
function screenshot(to) {
  shell(`screencap -p ${SHOT}`);
  adb("pull", SHOT, to);
}

const num = (out, re) => +((out.match(re) || [])[1] || 0);

async function main() {
  const started = Date.now();
  if (serial) execFileSync("adb", ["connect", serial], { stdio: "ignore" });
  await startMonkey();
  const model = shell("getprop ro.product.model").trim().replace(/\W+/g, "_");
  const goldens = join(here, "goldens.local", model);
  const run = join(here, "report", new Date().toISOString().replace(/[:.]/g, "-"));
  mkdirSync(goldens, { recursive: true });
  mkdirSync(run, { recursive: true });
  const results = [];

  for (const s of STEPS) {
    await keys(s.keys);
    const now = await waitFor(s.expect);
    const actual = join(run, `${s.name}.png`);
    screenshot(actual);
    const r = { name: s.name, screen: now };
    if (now !== s.expect) {
      r.status = "fail";
      r.reason = `expected ${s.expect} in front`;
      results.push(r);
      await keys(["HOME"]);
      break;   // the walk is off its path: every next step would press keys on the wrong screen
    }
    const golden = join(goldens, `${s.name}.png`);
    if (update || !existsSync(golden)) {
      copyFileSync(actual, golden);
      r.status = update ? "approved" : "new golden";
    } else {
      const d = await compare(golden, actual, join(run, `${s.name}.diff.png`), {
        threshold: 0.1, antialiasing: true, ignoreRegions: s.mask || [],
      });
      r.status = d.match ? "pass" : "fail";
      if (!d.match) r.reason = d.reason === "pixel-diff" ? `${d.diffPercentage.toFixed(2)}% of pixels differ` : d.reason;
    }
    results.push(r);
  }
  // Leave the TV as a person would find it: Settings back on its first page and closed (it stays alive in the
  // background and would reopen where the walk left it).
  for (let i = 0; i < 6 && front() !== HOME; i++) await keys(["BACK"]), await sleep(300);

  // Jank and memory: the shelf scrolled right and back, twice, on the home screen (its own process, :home).
  await keys(["HOME"]);
  await waitFor(HOME);
  shell("dumpsys gfxinfo io.freemytv:home reset");
  for (let i = 0; i < 2; i++) {
    for (const k of ["DPAD_RIGHT", "DPAD_LEFT"]) {
      for (let j = 0; j < 4; j++) await keys([k]), await sleep(120);   // a person's pace, so every move draws
      await sleep(400);
    }
  }
  const gfx = shell("dumpsys gfxinfo io.freemytv:home");
  const frames = num(gfx, /Total frames rendered: (\d+)/);
  const metrics = {
    framesRendered: frames,
    jankyPercent: frames ? +(100 * num(gfx, /Janky frames: (\d+)/) / frames).toFixed(1) : null,
    p95FrameMs: num(gfx, /95th percentile: (\d+)ms/),
    homeScreenMB: Math.round(num(shell("dumpsys meminfo io.freemytv:home"), /TOTAL\s+(\d+)/) / 1024),
    memAvailableMB: Math.round(num(shell("cat /proc/meminfo"), /MemAvailable:\s+(\d+)/) / 1024),
  };
  if (monkey) monkey.end("quit\n");   // monkey closes without answering "quit"
  if (monkey) adb("forward", "--remove", `tcp:${MONKEY_PORT}`);
  shell(`rm -f ${SHOT}`);

  const failed = results.filter((r) => r.status === "fail");
  const lines = [
    `# TV test ${model}`, "",
    "| Scenario | Result | Note |", "| --- | --- | --- |",
    ...results.map((r) => `| ${r.name} | ${r.status} | ${r.reason || ""} |`), "",
    `Jank: ${metrics.jankyPercent}% of ${metrics.framesRendered} frames, 95th percentile ${metrics.p95FrameMs} ms.`,
    `Memory: home screen ${metrics.homeScreenMB} MB, TV has ${metrics.memAvailableMB} MB available.`,
    `Took ${((Date.now() - started) / 1000).toFixed(1)} s.`,
  ];
  writeFileSync(join(run, "report.md"), lines.join("\n") + "\n");
  writeFileSync(join(run, "results.json"), JSON.stringify({ model, results, metrics }, null, 2));
  console.log(lines.join("\n"));
  console.log(`\nScreenshots and diffs: ${run}`);
  process.exit(failed.length ? 1 : 0);
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(2);
});

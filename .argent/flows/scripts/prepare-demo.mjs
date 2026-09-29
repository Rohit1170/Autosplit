// Runs before each screenshot flow (argent `script:` step, cwd = project root).
// Grants runtime permissions so no system dialogs end up in the captures,
// parks the emulator's GPS in Bengaluru (where the demo pins are), and seeds
// sample data through the DEBUG-only DemoDataActivity (no-op if data exists).
import { execFileSync } from "node:child_process";
import { join } from "node:path";

const APP = "com.example.splitwiseauto";
const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
const adb = sdk ? join(sdk, "platform-tools", process.platform === "win32" ? "adb.exe" : "adb") : "adb";

const run = (...args) => {
  try {
    execFileSync(adb, args, { stdio: "ignore", timeout: 20000 });
  } catch {
    // A permission that doesn't exist on this API level is fine to skip.
  }
};

for (const p of [
  "RECEIVE_SMS", "READ_SMS", "POST_NOTIFICATIONS", "CAMERA",
  "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION",
]) run("shell", "pm", "grant", APP, `android.permission.${p}`);

run("emu", "geo", "fix", "77.6245", "12.9352"); // lon, lat — Koramangala
run("shell", "am", "start", "-W", "-n", `${APP}/.DemoDataActivity`);

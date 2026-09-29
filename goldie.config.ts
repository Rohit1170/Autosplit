import type { GoldieConfig } from "goldie";
import { fileURLToPath } from "node:url";

/**
 * Google Play screenshots for Splitlee (goldie, by Software Mansion).
 *
 * Before running:
 *   1. ./gradlew assembleDebug        (DEBUG build: it carries the demo-data
 *                                      seeder; the release APK does not)
 *   2. Start a Pixel 10 Pro / Pixel 9 Pro emulator:  emulator -avd <name>
 *   3. goldie doctor   then   goldie all      (goldie studio to tweak)
 *
 * Each scene's flow (.argent/flows) grants permissions, seeds demo data,
 * opens the tab, and goldie captures after the last step.
 */
const APP_ROOT = fileURLToPath(new URL(".", import.meta.url));

const config: GoldieConfig = {
  appRoot: APP_ROOT,
  android: {
    appPath: "app/build/outputs/apk/debug/app-debug.apk",
    applicationId: "com.example.splitwiseauto",
  },

  devices: ["pixel-10-pro"], // Android only
  locales: ["en-US"],
  appearance: "light",

  theme: {
    background: "linear-gradient(160deg, #E9F7F0 0%, #F5FBF8 55%, #FFFFFF 100%)",
    headlineColor: "#0F2A1F",
    subheadColor: "#4F6B5E",
    fontFamily: "Lato", // bundled; Lexend/Work Sans would need fontFiles
    copyHeightRatio: 0.24,
    deviceWidthRatio: 0.84,
    template: "editorial",
    layout: "classic",
  },

  store: {
    name: "Splitlee",
    subtitle: { "en-US": "Split bills, automatically" },
    developer: "Akhil Sharma",
    category: "Finance",
    rating: 4.8,
    ratingCount: "1.2K Ratings",
    ageRating: "3+",
    price: "Free",
    description: {
      "en-US":
        "Splitlee reads your bank SMS on your phone and turns every payment into an expense you can split with friends in one tap.\n\n" +
        "See who owes what, where you spent on a map, and where your money goes each month. Works fully offline — your data never leaves your phone.",
    },
  },

  scenes: [
    {
      kind: "screenshot",
      id: "home",
      flow: "store-01-home",
      headline: { "en-US": "Every payment, tracked for you" },
      subhead: { "en-US": "Bank SMS become expenses automatically." },
    },
    {
      kind: "screenshot",
      id: "friends",
      flow: "store-02-friends",
      headline: { "en-US": "Know who owes what" },
      subhead: { "en-US": "Split with friends and settle up in a tap." },
    },
    {
      kind: "screenshot",
      id: "map",
      flow: "store-03-map",
      headline: { "en-US": "See where you spend" },
      subhead: { "en-US": "Every expense, pinned on the map." },
    },
    {
      kind: "screenshot",
      id: "insights",
      flow: "store-04-insights",
      headline: { "en-US": "Your month at a glance" },
      subhead: { "en-US": "Trends and categories, no spreadsheet needed." },
    },
  ],
};

export default config;

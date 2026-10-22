# Kana Rush 🌸⚡📚

An Android app for learning **hiragana**, **katakana** and **beginner vocabulary (Lessons 1–5)**, built to be hard to put down.

## What's inside

- **208 kana**: all basic hiragana and katakana, dakuten/handakuten, and combo sounds (きゃ, シュ…), in 18 units per script.
- **125 words** across Lessons 1–5: greetings & people, things & shopping, daily life, places & time, adjectives & travel. Each word has romaji, meaning and kanji where it's commonly used.
- **Teach → quiz flow**: new items appear as cards (with Japanese audio) and are quizzed right away; mistakes come back at the end of the session.
- **Your Anki deck**: import any Japanese vocabulary deck (.apkg) and it becomes a fourth track, in sets of 25 words with readings, meanings and example sentences. Import fresh, or keep your Anki review history so studied words keep their intervals. The deck stays on your device and is never bundled with the app. Export from Anki with "Include scheduling information" and "Support older Anki versions" ticked and media unticked.
- **Kanji mnemonics**: a keyword and a short story built from its parts for 1,039 common kanji (`app/src/main/resources/mnemonics.tsv`), shown on teach cards and after a miss. Write your own story for any kanji and it replaces the built-in one.
- **My kana**: instead of drilling every row, tap any kana you can't read on a word card to add it to a personal pool, and drill just those. Reading questions include a lookalike trap (ぬる vs める); falling for it adds both kana automatically. A 2-minute **hiragana check** of all 46 basic kana marks the ones you know and pools the rest. Pooled kana graduate once you know them well. Romaji under words is hidden by default (tap to reveal) so the kana get read.
- **Spaced repetition**: every item moves through 6 boxes (10 min → 1 day → 3 days → 1 week → 3 weeks). The Review card shows what's due.
- **Audio**: everything is spoken with the phone's Japanese text-to-speech voice.

## The hooks

| Mechanic | How it works |
|---|---|
| 🔥 Streak | Practice daily to grow it. Every 7 days earns a ❄️ freeze that covers one missed day. |
| 🎯 Daily goal | 50/100/200/300 XP; reaching it opens a +20 XP chest. |
| ☄️ Combos | 5 in a row = 1.5× XP, 10 = 2×, 20 = 3×. |
| ⭐ Stars & unlocks | 1–3 stars per unit by accuracy; 1 star unlocks the next unit on the path. |
| ⚡ Lightning | 60-second rapid-fire kana round with a high score to beat. |
| ⬆️ Levels | XP levels with a progress bar always on screen. |
| 🏆 Achievements | 14 badges (combos, streaks, perfect units, finishing each script…). |
| 🔔 Reminders | A 7pm nudge if you haven't practiced, especially when your streak is at risk. |
| 🎉 Juice | Confetti, popping stars, haptics, shake-on-miss, animated counters. |

## Build

Requirements: JDK 17 and the Android SDK (platform 35).

```bash
./gradlew testDebugUnitTest   # core logic tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Every push runs the **Android build** GitHub Actions workflow, which runs the tests and uploads the debug and release APKs as the `kana-rush-apk` artifact. Download it from the workflow run and install it on your phone.

## Code map

- `model/` data classes · `data/` kana tables, vocab lessons, `Course`
- `engine/` pure Kotlin: SRS, XP/levels, achievements, progression, quiz generation and sessions (unit-tested)
- `storage/` progress saved as JSON in SharedPreferences
- `ui/` Jetpack Compose screens: home path, quiz, results, chart, profile
- `audio/` text-to-speech · `reminder/` daily notification

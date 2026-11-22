# Khotwa IELTS

Independent, original IELTS Academic practice web app. Plain HTML, CSS and JavaScript, no build step, no backend.

- Placement test, 28-day plan (target 5 to 7), 36 lessons
- 2 full timed mocks: Reading, Listening (browser text-to-speech), Writing, Speaking
- 24 writing tasks, 8 speaking sets, science and academic vocabulary cards
- Progress is stored on the device only

Not affiliated with IELTS, the British Council or IDP. All questions and texts are original. Band estimates are approximate and uncalibrated.

Run locally: `python3 -m http.server` in this folder.

## Android app (v2)

- Adaptive program: the placement test sets a level per skill, then the app builds a plan of 4 to 52 weeks up to your target band (Build, Skill practice, Exam practice). Checkpoints every 7 days update the levels and re-plan the coming days.
- Arabic interface option in Settings (exam content stays in English).
- 3 full mocks (Reading 40 + Listening 40 each), 11 reading passages, 14 listening sections, 98 bank questions, practice by question type.
- Releases are signed with a fixed key from GitHub Secrets `KHOTWA_KEYSTORE_B64` and `KHOTWA_STORE_PASS`, so updates install over older versions.

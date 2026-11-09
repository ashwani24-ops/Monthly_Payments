# Monthly Payments (offline Android app)

Checklist of monthly payments that resets to unpaid on the 1st of every month.
Tap an item to mark it paid today; use ⋮ to pick another date. Past months keep their paid dates (← arrow).
No internet permission — all data stays on the phone.

## Option A — Build on GitHub (no installs needed)
1. Create a new GitHub repository and upload the contents of this folder (keep the `.github` folder).
2. Open the repo's **Actions** tab → "Build APK" runs automatically (or click *Run workflow*).
3. When it finishes (~4 min), open the run → **Artifacts** → download `MonthlyPayments-apk` → unzip → `app-debug.apk`.

## Option B — Android Studio
1. File → Open → select this folder. Let Gradle sync.
2. Build → Build Bundle(s) / APK(s) → Build APK(s).
3. APK: `app/build/outputs/apk/debug/app-debug.apk`

## Install on phone
Copy the APK to the phone, open it, and allow "Install unknown apps" for your file manager when prompted.

## Backups
Data is stored only on the phone. Before uninstalling or installing a newer build,
use **Back up or restore data** (bottom of the screen) → Save backup → send to Drive/WhatsApp/email.
After reinstalling, paste that text into the restore box.
(A new build from a different machine/GitHub run may need uninstall-then-install, which clears data — so back up first.)

## Customise
- App name: `app/src/main/res/values/strings.xml`
- UI and logic: `app/src/main/assets/index.html`

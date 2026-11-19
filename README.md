# 🎮 GTA Clipper for Android

This is the Android app for GTA Clipper. It opens your online GTA Clipper (on Hugging Face) as a real phone app:

- full-screen app with its own icon
- upload videos from the phone gallery
- clips save to **Downloads/GTA Clipper**
- fullscreen video playback
- a "waking up / try again" screen if the server is sleeping
- the server link can be changed inside the app

GitHub builds the APK for you, for free. You don't need Android Studio.

---

## Build the APK (one time, about 10 minutes)

**Before you start:** your online GTA Clipper must already be running on Hugging Face, and you need its link, e.g.
`https://huggingface.co/spaces/yourname/gta-clipper`

1. Create a free account on **github.com** and log in.
2. Click **+** (top right) → **New repository**.
   - Name: `gta-clipper-android`
   - Choose **Public** (so the APK download link works without logging in)
   - Click **Create repository**
3. On the next page click the link **"uploading an existing file"**.
4. Unzip `gta-clipper-android.zip` on your PC. Open the folder and **select everything inside it** (`.github`, `app`, `build.gradle`, `settings.gradle`, `gradle.properties`, `README.md`, …) and drag it all into the GitHub page. Click **Commit changes**.
5. Check that the repository now shows a **`.github`** folder. If it's missing, see "Troubleshooting" below.
6. Open the **Actions** tab. If GitHub asks, click **"I understand my workflows, go ahead and enable them"**.
7. On the left click **Build GTA Clipper APK** → **Run workflow** (right side) → paste your Hugging Face link → green **Run workflow** button.
8. Wait about 5 minutes until the run shows a green ✓.
9. Go back to the **Code** tab. On the right side under **Releases**, click the newest build → **GTA-Clipper.apk**. That's the APK download link. Send it to your brother.

## Install on the phone

1. Open the APK link on the Android phone and download **GTA-Clipper.apk**.
2. Tap the downloaded file. Android will ask to **allow installing apps from this source** (Chrome or Files). Allow it.
3. If **Play Protect** warns that the app is unknown, tap **More details → Install anyway**. It warns because the app isn't from the Play Store.
4. Open **GTA Clipper** from the home screen.

## Good to know

- The online clipper **sleeps** after about 2 days without use. Opening the app wakes it up, which takes about 1 minute. Tap **Try again** if you see the waking-up screen.
- Keep the app **open** while clips are being made. The screen stays on automatically.
- Save clips with the **⬇️ Download all clips** list at the bottom.
- To point the app to a different server: if the app can't connect, tap **Change server link**. You can also rebuild the APK with the new link.

## Troubleshooting

- **No `.github` folder after uploading:** in your repository click **Add file → Create new file**, type the name `.github/workflows/build-apk.yml`, open the file `.github/workflows/build-apk.yml` from the zip in Notepad, copy everything into the GitHub editor, then click **Commit changes**.
- **The build failed (red ✗):** open the failed run, click the red step, and send a screenshot to whoever set this up.

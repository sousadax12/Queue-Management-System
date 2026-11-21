# Deploying the operator app

How to sign and release the native operator app (`android-tv/`, module `:operator`) and how to
move a box from the Flutter app to it. Background: the "Distribution and upgrade" section of the
[native operator design](../design/2026-11-21-native-operator-app.md).

The short version:

- The native app **replaces** the Flutter app: same `applicationId`
  (`pro.joaosousa.queue_management_system`), same Firebase registration.
- Android only installs an update over an app signed with **the same key**. The Flutter APKs on
  the boxes were signed with debug or CI-runner keys we don't hold, so each box needs a
  **one-time uninstall** of Flutter. After that, every release is signed with one release key and
  installs in place with `adb install -r`.
- Releases are sideloaded APKs from GitHub Releases, built by
  [`operator-release.yml`](../../.github/workflows/operator-release.yml) when you push an
  `operator-v*` tag.

## 1. One-time: create the release key

Do this once, on your own machine. Nobody else (and no agent) should ever see the keystore or
its password.

1. Create the keystore. Use a long random password. PKCS12 keystores use the store password for
   the key too, so you only choose one password:

   ```bash
   keytool -genkeypair -v -keystore queue-operator-release.jks -storetype PKCS12 -alias queue-operator -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Queue Management System operator"
   ```

2. Note the certificate's SHA-256 fingerprint. Every release's notes repeat it, so you can check
   that a release was signed with your key:

   ```bash
   keytool -list -v -keystore queue-operator-release.jks -alias queue-operator | grep SHA256
   ```

3. Add the four repository secrets. `gh secret set` without a value prompts for it, so the
   password never lands in your shell history. Paste the same password for both password secrets:

   ```bash
   base64 -i queue-operator-release.jks | gh secret set OPERATOR_KEYSTORE_BASE64 -R sousadax12/Queue-Management-System
   ```

   ```bash
   gh secret set OPERATOR_KEYSTORE_PASSWORD -R sousadax12/Queue-Management-System
   ```

   ```bash
   gh secret set OPERATOR_KEY_PASSWORD -R sousadax12/Queue-Management-System
   ```

   ```bash
   echo -n queue-operator | gh secret set OPERATOR_KEY_ALIAS -R sousadax12/Queue-Management-System
   ```

   Check that all four are listed:

   ```bash
   gh secret list -R sousadax12/Queue-Management-System
   ```

4. **Back up the keystore and its password**, together, somewhere that survives losing this
   laptop (a password manager entry with the `.jks` attached, plus an offline copy). Then delete
   any loose copies.

> [!WARNING]
> If the keystore or its password is lost, no future release can update the installed app.
> Every box would need another uninstall and reinstall, by hand, like the first cutover. GitHub
> secrets can't be read back, so the secret is not a backup.

The same variables also work for a local signed build (handy to test a box before a release):

```bash
cd android-tv
OPERATOR_KEYSTORE_BASE64="$(base64 -i ~/path/queue-operator-release.jks)" OPERATOR_KEYSTORE_PASSWORD=... OPERATOR_KEY_ALIAS=queue-operator OPERATOR_KEY_PASSWORD=... ./gradlew :operator:assembleRelease -PoperatorVersionCode=1000
```

Without them, `./gradlew :operator:assembleRelease` builds `operator-release-unsigned.apk`. It
never signs with the debug key. Setting only some of the four fails the build and names the
missing ones.

## 2. Cut a release

1. Make sure `main` is green and has what you want to ship.
2. Tag the commit and push the tag. The tag is `operator-v<major>.<minor>.<patch>`, starting at
   `operator-v2.0.0` (Flutter was 1.x). A suffix makes a pre-release, e.g. `operator-v2.0.0-rc.1`:

   ```bash
   git tag operator-v2.0.0 origin/main
   ```

   ```bash
   git push origin operator-v2.0.0
   ```

3. The **Operator release** workflow builds the APK, checks it with `apksigner verify`, and
   creates the GitHub Release `operator-v2.0.0` with two files:
   `queue-operator-2.0.0.apk` and `queue-operator-2.0.0.apk.sha256`. The release notes show the
   signing certificate's SHA-256: it must match the one you noted in step 1.

Versions:

- `versionName` is the tag without `operator-v`.
- `versionCode` is `1000 + github.run_number` of this workflow, so each release is higher than
  the last and higher than any Flutter build (those were all 1). Re-running a failed run keeps
  its number. Don't rename the workflow file: GitHub would restart `run_number` at 1, and the
  next release would be a "downgrade" that `adb install -r` refuses.

If a secret is missing, the first step fails with
`Missing signing secrets: Add these repository secrets: ...` and lists them.

You can also run the workflow by hand (**Actions → Operator release → Run workflow**). That
builds and verifies a signed APK and keeps it as a workflow artifact, but publishes nothing.

## 3. Cut a box over from Flutter

You need: a computer with `adb` and the Android SDK build tools (for `apksigner`), the box on the
same network with network debugging on (or a USB cable), the release APK, the operator account
for the box's site, and the box's current Flutter APK as a rollback (step 4 below).

Run these one at a time. `$BUILD_TOOLS` is your newest `$ANDROID_HOME/build-tools/<version>`.

1. **Connect.** On the box enable developer options and network (or USB) debugging, then:

   ```bash
   adb connect <box-ip>:5555
   ```

   ```bash
   adb devices
   ```

2. **Check the Android version.** The app needs Android 8.0 (API 26) or newer. This must print
   26 or more; if it doesn't, stop: the box can't run the native app.

   ```bash
   adb shell getprop ro.build.version.sdk
   ```

3. **Check what is installed and who signed it.**

   ```bash
   adb shell dumpsys package pro.joaosousa.queue_management_system | grep -E "versionCode|versionName"
   ```

   ```bash
   adb pull "$(adb shell pm path pro.joaosousa.queue_management_system | head -1 | sed 's/^package://' | tr -d '\r')" flutter-<box-name>.apk
   ```

   ```bash
   "$BUILD_TOOLS/apksigner" verify --print-certs flutter-<box-name>.apk
   ```

   - `versionCode` 1 (or below 1000) and a certificate that isn't yours: it's the Flutter app.
     Go on. **Keep `flutter-<box-name>.apk`**: it is this box's rollback.
   - `versionCode` 1000 or more and your release certificate: the box already runs the native
     app. Skip the uninstall and just `adb install -r` the new APK (step 5).

4. **Uninstall Flutter.** This also removes its local data (only the old sign-in session).

   ```bash
   adb uninstall pro.joaosousa.queue_management_system
   ```

5. **Install the native app.** Check the download first, then install:

   ```bash
   shasum -a 256 -c queue-operator-2.0.0.apk.sha256
   ```

   ```bash
   adb install -r queue-operator-2.0.0.apk
   ```

   If it fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE ... signatures do not match`, an app
   signed with another key is still installed: go back to step 4.

6. **Launch it** from the box's launcher (or with adb):

   ```bash
   adb shell am start -n pro.joaosousa.queue_management_system/pro.joaosousa.queuedisplay.operator.OperatorActivity
   ```

7. **Sign in** with the site's operator account, created with `pnpm grant-operator`
   ([operator accounts](../operator-accounts.md)). Type the email with the remote, Enter, the
   password, Enter. **No operator access** means the account has no claims or an unverified
   email: re-run `grant-operator` for it.
8. **Pick the queue** this box drives: left/right moves the highlight, select picks it.
9. **Check that it works.** Press right on the remote: the number goes up by one and the chime
   plays. Check that the new number shows on a display of that queue (another box, the TV
   display app, or the queue's document in the Firestore console). Press left to put it back.
10. Restart the box (power off and on), open the app from the launcher, and check that it is
    still signed in, on the same queue. (Like the Flutter app, it doesn't start by itself at boot.)
11. Disconnect: `adb disconnect`, and turn network debugging off again if the box shouldn't
    keep it.

Write down for each box: its name, Android version, the date, the version installed, and where
its `flutter-<box-name>.apk` is kept.

## 4. Roll back to Flutter

The native app and the Flutter APK are signed with different keys, so a rollback is again an
uninstall plus an install:

```bash
adb uninstall pro.joaosousa.queue_management_system
```

```bash
adb install flutter-<box-name>.apk
```

Then sign in to the Flutter app as before. Where to get the Flutter APK, best first:

1. The `flutter-<box-name>.apk` you pulled from that box in step 3 of the cutover: exactly what
   the box ran.
2. The latest `app-debug-apk` artifact from `main`. The **CI** workflow
   ([`ci.yml`](../../.github/workflows/ci.yml)) builds it, through
   [`build-apk.yml`](../../.github/workflows/build-apk.yml), on pushes that touch the Flutter
   app, so not every CI run has one. Artifacts expire after 30 days, so download one before the
   pilot and keep it with the box APKs. This finds the newest unexpired one from `main`:

   ```bash
   gh run download -R sousadax12/Queue-Management-System "$(gh api 'repos/sousadax12/Queue-Management-System/actions/artifacts?name=app-debug-apk&per_page=50' --jq '[.artifacts[] | select(.workflow_run.head_branch == "main" and (.expired | not))][0].workflow_run.id')" --name app-debug-apk
   ```

3. Build it from the repo root: `flutter build apk --debug --no-tree-shake-icons`
   (APK in `build/app/outputs/flutter-apk/app-debug.apk`).

Note that the current Flutter `main` build uses the claims too, so it works under the same
accounts and rules as the native app.

## 5. Pilot sign-off

Run the native app on **one** box for **one full working day** before cutting over the others.
The owner signs off when every item holds:

- [ ] Before the day: the box was cut over with the checklist above, and its
      `flutter-<box-name>.apk` and a Flutter `app-debug-apk` are saved for rollback.
- [ ] At opening time, opening the app from the launcher (as with Flutter) lands on home, still
      signed in, on the right queue.
- [ ] Every press of the remote moved the number by exactly one, at once, with the chime, all day.
- [ ] Each change showed on the displays for that queue within a few seconds.
- [ ] Wrap-around works (99 → 0 with right, 0 → 99 with left).
- [ ] The clock is right and the news ticker scrolls.
- [ ] The queue's video plays and loops (if the queue has one). An `http://` video URL stays black
      in this build; switch such queues to `https://` or YouTube before the pilot.
- [ ] A network drop (unplug or turn Wi-Fi off for a minute, press a few times, reconnect):
      the box keeps counting and the displays catch up after the reconnect.
- [ ] After a power cut or restart in the day, the app opens signed in on the same queue.
- [ ] No crash or "Access denied" all day. If one happened, note the time and pull the log with
      `adb logcat -d > pilot-<box-name>.log`.
- [ ] Operators found nothing missing compared with the Flutter app.

After sign-off, cut over the other boxes with the same checklist. Retiring the Flutter app
(`lib/`, `ios/`, `web/`, `test/`, `build-apk.yml`) is a separate PR after that.

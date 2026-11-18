# Operator screenshots

Rendered from the real composables by `:ui`'s `ScreenRenderTest` (Robolectric with
native graphics), which writes them to `ui/build/screenshots/` on every run; the
copies here are committed so a PR can show them and a reviewer can diff them.

| File | What it shows |
|---|---|
| `01-setup.png` | Setup: the site picker and the screen picker, both selected, with `Continuar` |
| `02-login.png` | Login: email/password, with the continue-anonymously path |
| `03-home.png` | Home: two queues with their contract colours, the selected one showing 42, the video zone (nothing to play) |
| `04-home-ticker-down.png` | Home with a dead feed: `tickerError` shows, the counter keeps working |
| `05-live-emulator.png` | The app running on the headless emulator against the real `santos-1b6f6` project |

Regenerate with:

```sh
cd android-operator
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=$HOME/android
./gradlew :ui:testDebugUnitTest
```

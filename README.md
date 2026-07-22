# JMeter Viewer for JetBrains IDEs

JetBrains plugin that opens local `.jmx` files with Apache JMeter's native
editable tree, element forms, structural actions, and `Copy Code` support.
The ordinary XML editor remains available beside the visual editor.

The plugin uses an Apache JMeter 5.6.3 installation selected by the user. This
keeps the plugin small and lets JMeter discover compatible components installed
in that installation's `lib/ext` directory.

## Configure JMeter

1. Install the official Apache JMeter 5.6.3 binary distribution.
2. In the IDE, open **Settings/Preferences > Tools > JMeter**.
3. Select the JMeter root directory. It must contain
   `bin/ApacheJMeter.jar`, `bin/jmeter.properties`, and `lib/ext`.
4. Open a local `.jmx` file and select its **JMeter** editor tab.

An invalid or unsupported directory is rejected in Settings. If no installation
is configured, the editor offers a **Configure JMeter** action. Changing or
clearing the configured home after JMeter's native editor has loaded requires an
IDE restart because JMeter keeps process-global GUI state.

## Editor ownership

JMeter supplies its standard tree, forms, context menus, validation, and JMX
serialization. JetBrains supplies editor tabs, file navigation, Save/Save All,
external-change handling, and the application look and feel. JMeter's standalone
toolbar, menu bar, logger, and file actions are not exposed. The native tree's
right-click **Start**, **Start no pauses**, and **Validate** actions are retained.

JMeter's GUI state is process-global, so the plugin deliberately owns one shared
native workspace. Before another visual JMX tab becomes active, the current
model is flushed into the IDE document and saved. A save failure cancels the
switch. If XML and visual state both changed, the editor offers `Reload
external`, `Overwrite visual`, or `Cancel`.

The editor supports local physical JMX files and loads the standard modules and
compatible third-party plugins from the selected JMeter 5.6.3 installation.
Recorder, templates, and report generation are intentionally not exposed in the
JetBrains UI.

Multiline fields currently use JMeter's native Swing editors. The experimental
IntelliJ text-area adapter, language selection, and reformatting implementation
remain in the source behind a disabled feature switch for later debugging.
Per-field undo and visual-form JMX undo are also temporarily suspended; undo in
the XML source editor remains available. Test Plan search starts collapsed and
can be opened with the **Search** button or `Ctrl+F` (`Cmd+F` on macOS).

Selected thread groups run through a normal IntelliJ **JMeter Selected Thread
Groups** Run Configuration and the configured installation's `bin/jmeter`
launcher (`jmeter.bat` on Windows). The IDE process console owns Stop, only one
JMeter process may run at a time, and the shared visual workspace remains bound
to that JMX until completion. Other XML editors remain usable. A token-protected
loopback bridge streams samples into the native **Results Tree** and **Aggregate
Report** tabs, with a temporary journal as a delivery fallback.

The process uses a valid inherited `JAVA_HOME`/`JRE_HOME` when available and
otherwise falls back to the IDE runtime. Its Java `bin` directory is added to
the child process path, so desktop-launched IDEs do not require separate shell
environment setup before running a thread group.

## Runtime isolation

JMeter and its dependencies are loaded from the selected installation in an
isolated class loader. The distributable contains only the JetBrains plugin jar
and a small patched JMeter core compatibility bridge. That bridge fixes native
menu discovery when an external component contributes an item without a default
menu-order entry; it also avoids leaking JMeter's logging, XML, and Kotlin
dependencies into the IntelliJ Platform class loader.

Apache JMeter 5.6.3 source is vendored under `vendor/apache-jmeter-5.6.3` so the
compatibility patch remains reviewable and reproducible.

## Build and verify

The repository includes JDK 17 wrappers for the normal verification paths:

```bash
./scripts/compile-jdk17.sh
./scripts/test-jdk17.sh
./scripts/gui-smoke-jdk17.sh
./scripts/build-jdk17.sh
./scripts/verify-jdk17.sh
./scripts/plugin-verifier-jdk17.sh
```

`test-jdk17.sh` accepts Gradle test filters, for example
`./scripts/test-jdk17.sh --tests '*JMeterInstallationTest'`. Override the
default local JDK path with `JMETER_VIEWER_JAVA_HOME=/path/to/jdk-17`.

`compile-jdk17.sh` is the fast production-source check, while
`build-jdk17.sh` runs the normal Gradle build. Both accept additional Gradle
arguments.

`gui-smoke-jdk17.sh` uses an existing display or a local Xvfb instance to open,
snapshot, and reopen a complex JMX plan in JMeter's native workspace. It then
runs three selected thread groups against an embedded localhost server and
verifies the live request and response data in Results Tree.
`verify-jdk17.sh` includes that smoke test after the complete headless suite,
builds the distributable, checks that no JMeter installation or conflicting
runtime libraries were bundled, and enforces a 5 MiB archive-size ceiling.
`plugin-verifier-jdk17.sh` runs the IntelliJ Plugin Verifier against the
supported PyCharm compatibility targets.

The plugin targets JVM 11 and IntelliJ Platform build 221 (PyCharm Community
2022.1.4). The distributable is written to `build/distributions/`.

Launch the development IDE:

```bash
JAVA_HOME=/home/duync/toolchains/jdk-17.0.4.1 \
PATH=/home/duync/toolchains/jdk-17.0.4.1/bin:$PATH \
./gradlew runIde
```

`runIde` requires a graphical environment. Run `./gradlew
prepareJMeterTestHome` if you want a generated JMeter 5.6.3 installation for
local development, then select `build/test-jmeter-home` in the sandbox IDE's
JMeter settings.

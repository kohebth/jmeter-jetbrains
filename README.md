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
clearing the configured home while visual sessions are open requires an IDE
restart. Closing all visual JMX tabs first allows the installation to be changed
without mixing two JMeter homes.

## Editor ownership

JMeter supplies its standard tree, forms, context menus, validation, and JMX
serialization. JetBrains supplies editor tabs, file navigation, Save/Save All,
external-change handling, and the application look and feel. JMeter's standalone
toolbar, menu bar, logger, and file actions are not exposed. The native tree's
right-click **Start**, **Start no pauses**, and **Validate** actions are retained.

Each open physical JMX file owns an isolated JMeter class loader, native
workspace, result views, undo state, and process slot. Different files can be
edited and run simultaneously across IDE project windows. Opening the same
physical file visually twice is intentionally blocked; the second window keeps
its XML editor available until the first visual tab closes. If XML and visual
state both changed, the editor offers `Reload external`, `Overwrite visual`, or
`Cancel`.

The editor supports local physical JMX files and loads the standard modules and
compatible third-party plugins from the selected JMeter 5.6.3 installation.
Recorder, templates, and report generation are intentionally not exposed in the
JetBrains UI.

Multiline fields currently use JMeter's native Swing editors. The experimental
IntelliJ text-area adapter, language selection, and reformatting implementation
remain in the source behind a disabled feature switch for later debugging.
JMeter's bounded native per-field undo remains available. Only JetBrains-backed
visual JMX undo is temporarily suspended; undo in the XML source editor remains
available. Test Plan search starts collapsed and can be opened with the
**Search** button or `Ctrl+F` (`Cmd+F` on macOS).

JMeter's embedded-safe shortcuts are registered only on the visual editor and
its JMeter tool window. Native text-field copy, cut, paste, select-all, undo, and
redo take priority. Tree copy/cut/paste supports multiple selected nodes,
preserves source order, removes descendants whose ancestor is selected, and
uses a portable JMX clipboard flavor so nodes can be pasted into another JMX
file or IDE window. Native right-click **Add** menus include compatible
third-party elements from the configured installation.

Selected thread groups run through a normal IntelliJ **JMeter Selected Thread
Groups** Run Configuration and the configured installation's `bin/jmeter`
launcher (`jmeter.bat` on Windows). Each JMX file permits one run at a time, while
different files may run in parallel. Temporary configurations are pinned to the
exact JMX session and do not activate the IDE Run tool window. A token-protected
loopback bridge streams samples into that file's native **Results Tree** and
**Aggregate Report** tabs, with a temporary journal as a delivery fallback.
Background sessions never replace the tool-window surfaces of the JMX file the
user is currently viewing.

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
snapshot, and reopen a complex JMX plan in JMeter's native workspace. It also
opens two isolated workspaces together, verifies cross-classloader multi-node
copy/paste and a third-party native context-menu Add action, then runs three
selected thread groups against an embedded localhost server and verifies the
live request and response data in Results Tree.
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

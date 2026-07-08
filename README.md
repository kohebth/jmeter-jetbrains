# JMeter Viewer for JetBrains IDEs

This plugin opens Apache JMeter `.jmx` files directly in JetBrains IDEs such as PyCharm and IntelliJ IDEA, using JMeter's own Swing UI panels for the visual editor.

Repository: https://github.com/kohebth/jmeter-jetbrains

## Latest Snapshot

Download the current pre-release build from [v0.1.2-SNAPSHOT](https://github.com/kohebth/jmeter-jetbrains/releases/tag/v0.1.2-SNAPSHOT).

Install the ZIP in JetBrains IDEs with `Settings | Plugins | Install Plugin from Disk`.

## Features

- Registers `.jmx` files as JMeter test plans.
- Adds an embedded JMeter-style visual editor before the IDE's default text editor.
- Keeps the IDE text editor available for raw `.jmx` editing, Git highlighting, and normal IDE shortcuts.
- Shows the JMeter test tree on the left.
- Shows JMeter's native selected-element UI on the right.
- Shows a categorized palette of bundled JMeter elements that can be dragged onto valid tree nodes.
- Saves modified test plans back to `.jmx` through JMeter's own serializer.
- Supports basic tree editing actions from the toolbar: delete, duplicate, copy, paste, move up, and move down.
- Supports tree context menus with categorized Add actions and common edit actions.
- Supports enable/disable toggling for tree elements.
- Supports expanding/collapsing selected subtrees and the full test plan tree.
- Supports keyboard shortcuts for delete, copy, paste, duplicate, enable/disable, and move up/down.
- Palette coverage includes bundled thread groups, test fragments, samplers, controllers, config elements, assertions, pre-processors, post-processors, timers, and listeners from the included JMeter modules.
- Discovers additional addable `JMeterGUIComponent` implementations from JMeter runtime search paths and merges them into the palette when available.
- Supports tree search with next/previous navigation across names, comments, GUI classes, and test classes.
- Runs and stops the current test plan through JMeter's `StandardJMeterEngine`.
- Displays run results, request/response details, run status, and diagnostics in the bottom JMeter tool window.

## Editing Status

Drag-and-drop additions, context-menu additions, runtime element discovery, basic tree editing, enable/disable, tree expand/collapse, tree search, explicit Save, raw IDE text editing, basic Run/Stop, structured sample result viewing, and diagnostics logging are supported. Full JMeter editing parity is still in progress; full JMeter-native action routing, all listener visualizer behavior inside the IDE, and distributed run controls are not complete yet.

## Compatibility

- Targets PyCharm Community 2022.1.
- Declares compatibility from JetBrains build `221` onward, so the same ZIP can be installed in PyCharm 2022.x and newer JetBrains IDEs such as IntelliJ IDEA 2026.
- Uses Java 11 bytecode for compatibility with the 2022 IDE runtime.

## Build

Use the checked-in Gradle wrapper. The JetBrains Gradle plugin required for PyCharm 2022 is not compatible with Gradle 9.

```bash
./gradlew build
```

## Run in a Development IDE

```bash
./gradlew runIde
```

## Package

```bash
./gradlew buildPlugin
```

The plugin ZIP is written under `build/distributions/`.
Snapshot builds are named like `jmeter-jetbrains-plugin-<version>.zip`.

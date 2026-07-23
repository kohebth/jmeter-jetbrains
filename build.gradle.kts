import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.DownloadRobotServerPluginTask
import org.jetbrains.intellij.tasks.RunPluginVerifierTask
import org.jetbrains.intellij.tasks.RunIdeForUiTestTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.zip.ZipFile

plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.github.kohebth"
version = "0.1.12-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

val jmeterVersion = "5.6.3"
val remoteRobotVersion = "0.11.23"

val jmeterBridge by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val jmeterTestRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val jmeterPluginTest by sourceSets.creating
val ideUiTest by sourceSets.creating

configurations.named(jmeterPluginTest.compileClasspathConfigurationName) {
    extendsFrom(jmeterTestRuntime)
}

configurations.named("runtimeClasspath") {
    // IntelliJ supplies these libraries. Bundling another copy breaks binary
    // compatibility with the platform, especially on the 2022.1 baseline.
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains.kotlinx")
}

dependencies {
    add(jmeterBridge.name, "org.apache.jmeter:ApacheJMeter_core:$jmeterVersion")

    listOf(
        "ApacheJMeter_config",
        "ApacheJMeter_core",
        "ApacheJMeter_components",
        "ApacheJMeter_functions",
        "ApacheJMeter_bolt",
        "ApacheJMeter_ftp",
        "ApacheJMeter_http",
        "ApacheJMeter_java",
        "ApacheJMeter_jdbc",
        "ApacheJMeter_jms",
        "ApacheJMeter_junit",
        "ApacheJMeter_ldap",
        "ApacheJMeter_mail",
        "ApacheJMeter_mongodb",
        "ApacheJMeter_native",
        "ApacheJMeter_tcp",
    ).forEach { module ->
        add(jmeterTestRuntime.name, "org.apache.jmeter:$module:$jmeterVersion")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")

    add(
        ideUiTest.implementationConfigurationName,
        "com.intellij.remoterobot:remote-robot:$remoteRobotVersion",
    )
    add(
        ideUiTest.implementationConfigurationName,
        "com.intellij.remoterobot:remote-fixtures:$remoteRobotVersion",
    )
    add(ideUiTest.implementationConfigurationName, "com.squareup.okhttp3:okhttp:4.12.0")
    add(ideUiTest.implementationConfigurationName, "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.23")
    add(ideUiTest.implementationConfigurationName, "org.junit.jupiter:junit-jupiter:5.10.3")
    add(ideUiTest.runtimeOnlyConfigurationName, "org.slf4j:slf4j-simple:2.0.13")
}

kotlin {
    jvmToolchain(17)
}

intellij {
    pluginName.set("jmeter-jetbrains-plugin")
    version.set("2022.1.4")
    type.set("PC")
    downloadSources.set(false)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
    // Build against the Kotlin API shipped by the 2022.1 IDE baseline. In
    // particular, do not emit the newer kotlin.enums.EnumEntries ABI.
    kotlinOptions.languageVersion = "1.6"
    kotlinOptions.apiVersion = "1.6"
    kotlinOptions.freeCompilerArgs += "-Xjvm-default=all"
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

val testJMeterHome = layout.buildDirectory.dir("test-jmeter-home")
val ideUiTestProject = layout.buildDirectory.dir("ide-ui-test/project")
val ideUiTestReports = layout.buildDirectory.dir("reports/ide-ui-smoke")
val ideUiTestSystem = layout.buildDirectory.dir("idea-sandbox/system-uiTest")
val ideUiTestIdeaLog = ideUiTestSystem.map { it.file("log/idea.log") }
val ideUiTestPort = providers.environmentVariable("JMETER_IDE_UI_PORT").orElse("8580")

val jmeterTestPluginJar by tasks.registering(Jar::class) {
    archiveFileName.set("jmeter-viewer-test-plugin.jar")
    from(jmeterPluginTest.output)
}

val prepareJMeterTestHome by tasks.registering(Sync::class) {
    dependsOn(jmeterTestPluginJar)
    into(testJMeterHome)
    duplicatesStrategy = DuplicatesStrategy.FAIL

    from(layout.projectDirectory.dir("vendor/apache-jmeter-5.6.3/bin")) {
        include(
            "jmeter.properties",
            "saveservice.properties",
            "upgrade.properties",
            "user.properties",
            "system.properties",
            "log4j2.xml",
            "jmeter",
            "jmeter.sh",
            "jmeter.bat",
        )
        into("bin")
    }
    from(jmeterTestRuntime) {
        include("ApacheJMeter-$jmeterVersion.jar")
        rename { "ApacheJMeter.jar" }
        into("bin")
    }
    from(jmeterTestRuntime) {
        exclude("ApacheJMeter-$jmeterVersion.jar")
        exclude("ApacheJMeter_*.jar")
        into("lib")
    }
    from(jmeterTestRuntime) {
        include("ApacheJMeter_*.jar")
        into("lib/ext")
    }
    from(jmeterTestPluginJar) {
        into("lib/ext")
    }
}

val prepareIdeUiTestProject by tasks.registering(Sync::class) {
    into(ideUiTestProject)
    from(layout.projectDirectory.dir("src/test/resources/jmx/smoke")) {
        include("complex-localhost-plan.jmx", "users.csv")
    }
    doLast {
        ideUiTestProject.get().file("clipboard-target.txt").asFile.writeText("")
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    dependsOn(prepareJMeterTestHome)
    dependsOn(jmeterBridge)
    doFirst {
        systemProperty("jmeter.test.home", testJMeterHome.get().asFile.absolutePath)
        systemProperty("jmeter.bridge.jar", jmeterBridge.singleFile.absolutePath)
    }
}

tasks.named<DownloadRobotServerPluginTask>("downloadRobotServerPlugin") {
    version.set(remoteRobotVersion)
}

tasks.named<PrepareSandboxTask>("prepareUiTestingSandbox") {
    dependsOn(prepareJMeterTestHome)
    doLast {
        val optionsDirectory = file(configDir.get()).resolve("options")
        check(optionsDirectory.mkdirs() || optionsDirectory.isDirectory) {
            "Could not create the IDE UI test settings directory: $optionsDirectory"
        }
        val escapedJMeterHome = testJMeterHome.get().asFile.absolutePath
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        optionsDirectory.resolve("jmeter-viewer.xml").writeText(
            """
            <application>
              <component name="JMeterViewerSettings">
                <option name="jmeterHome" value="$escapedJMeterHome" />
              </component>
            </application>
            """.trimIndent() + "\n",
        )
    }
}

tasks.named<RunIdeForUiTestTask>("runIdeForUiTests") {
    dependsOn(prepareIdeUiTestProject)
    dependsOn(prepareJMeterTestHome)
    systemDir.set(ideUiTestSystem.map { it.asFile })
    args(ideUiTestProject.get().asFile.absolutePath)

    systemProperty("ide.browser.jcef.enabled", "false")
    systemProperty("ide.mac.file.chooser.native", "false")
    systemProperty("ide.mac.message.dialogs.as.sheets", "false")
    systemProperty("ide.show.tips.on.startup.default.value", "false")
    systemProperty("idea.trust.all.projects", "true")
    systemProperty("jb.consents.confirmation.enabled", "false")
    systemProperty("jb.privacy.policy.text", "<!--999.999-->")
    systemProperty("jbScreenMenuBar.enabled", "false")
    systemProperty("apple.laf.useScreenMenuBar", "false")

    doFirst {
        systemProperty("robot-server.port", ideUiTestPort.get())
        ideUiTestIdeaLog.get().asFile.delete()
    }
}

tasks.register<Test>("ideUiTest") {
    group = "verification"
    description = "Runs the real-IDE JMeter Swing keyboard and clipboard smoke test."
    testClassesDirs = ideUiTest.output.classesDirs
    classpath = ideUiTest.runtimeClasspath
    dependsOn(ideUiTest.classesTaskName)
    dependsOn(prepareIdeUiTestProject)
    shouldRunAfter(tasks.test)
    useJUnitPlatform()
    maxParallelForks = 1
    failFast = true
    outputs.upToDateWhen { false }

    reports {
        junitXml.outputLocation.set(ideUiTestReports.map { it.dir("junit") })
        html.outputLocation.set(ideUiTestReports.map { it.dir("tests") })
    }

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }

    doFirst {
        systemProperty("remote-robot-url", "http://127.0.0.1:${ideUiTestPort.get()}")
        systemProperty("jmeter.ide.ui.project.dir", ideUiTestProject.get().asFile.absolutePath)
        systemProperty("jmeter.ide.ui.report.dir", ideUiTestReports.get().asFile.absolutePath)
        systemProperty("jmeter.ide.ui.log.path", ideUiTestIdeaLog.get().asFile.absolutePath)
    }
}

// Gradle IntelliJ Plugin 1.x prepends the regular instrumented test output to
// every Test task after project evaluation. Restore this task's isolated source
// set after all plugin callbacks have run so it cannot rediscover unit tests.
gradle.projectsEvaluated {
    tasks.named<Test>("ideUiTest") {
        testClassesDirs = ideUiTest.output.classesDirs
        classpath = ideUiTest.runtimeClasspath
    }
}

tasks.named<RunPluginVerifierTask>("runPluginVerifier") {
    ideVersions.set(listOf("PC-2022.1.4", "PC-2025.1.6.1"))
}

tasks.patchPluginXml {
    sinceBuild.set("221")
    untilBuild.set(provider { null })
}

tasks.withType<PrepareSandboxTask>().configureEach {
    intoChild(pluginName.map { it + "/jmeter-bridge" })
        .from(jmeterBridge)
}

tasks.processResources {
    from(
        layout.projectDirectory.file(
            "vendor/apache-jmeter-5.6.3/src/core/src/main/resources/" +
                "org/apache/jmeter/images/feather.gif",
        ),
    ) {
        into("icons")
        rename { "jmeter-feather.gif" }
    }
}

tasks.jar {
    from(layout.projectDirectory.file("LICENSE")) {
        into("META-INF")
        rename { "LICENSE-plugin" }
    }
    from(layout.projectDirectory.file("NOTICE")) {
        into("META-INF")
        rename { "NOTICE-plugin" }
    }
    from(layout.projectDirectory.file("vendor/apache-jmeter-5.6.3/LICENSE")) {
        into("META-INF")
        rename { "LICENSE-apache-jmeter" }
    }
    from(layout.projectDirectory.file("vendor/apache-jmeter-5.6.3/NOTICE")) {
        into("META-INF")
        rename { "NOTICE-apache-jmeter" }
    }
}

val verifyPluginRuntime by tasks.registering {
    group = "verification"
    description = "Checks the isolated JMeter bridge and plugin archive size."
    dependsOn(tasks.buildPlugin)

    doLast {
        val archive = tasks.buildPlugin.get().archiveFile.get().asFile
        ZipFile(archive).use { zip ->
            val root = intellij.pluginName.get()
            val entries = zip.entries().asSequence().map { it.name }.toList()
            val pluginLibraries = entries
                .filter { it.startsWith("$root/lib/") && it.endsWith(".jar") }
            check(pluginLibraries.size == 1 && pluginLibraries.single().substringAfterLast('/').contains(root)) {
                "Plugin lib must contain only the plugin jar: ${pluginLibraries.joinToString()}"
            }

            val bridges = entries
                .filter { it.startsWith("$root/jmeter-bridge/") && it.endsWith(".jar") }
            check(bridges.size == 1) {
                "Expected exactly one JMeter compatibility bridge: ${bridges.joinToString()}"
            }
            check(bridges.single().substringAfterLast('/') == "ApacheJMeter_core-$jmeterVersion.jar") {
                "Unexpected JMeter bridge: ${bridges.single()}"
            }

            val bundledHome = entries.filter { it.startsWith("$root/jmeter-home/") }
            check(bundledHome.isEmpty()) {
                "Plugin still bundles a JMeter home: ${bundledHome.joinToString()}"
            }

            val forbidden = entries
                .filter { it.startsWith("$root/lib/") }
                .map { it.substringAfterLast('/') }
                .filter {
                    it.startsWith("ApacheJMeter") ||
                        it.startsWith("slf4j-") ||
                        it.startsWith("log4j-") ||
                        it.startsWith("xerces") ||
                        it.startsWith("xml-apis") ||
                        it.startsWith("kotlin-stdlib") ||
                        it.startsWith("kotlinx-coroutines")
                }
                .toList()
            check(forbidden.isEmpty()) {
                "Plugin bundles runtime libraries outside the isolated bridge: ${forbidden.joinToString()}"
            }

            val maximumArchiveBytes = 5L * 1024L * 1024L
            check(archive.length() <= maximumArchiveBytes) {
                "Plugin archive is ${archive.length()} bytes; maximum is $maximumArchiveBytes"
            }
        }
    }
}

tasks.named("buildSearchableOptions") {
    enabled = false
}

tasks.named("jarSearchableOptions") {
    enabled = false
}

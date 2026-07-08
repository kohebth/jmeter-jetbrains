package com.github.kohebth.jmeterviewer.validation;

import com.github.kohebth.jmeterviewer.runtime.JMeterPluginClasspath;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class JMeterPlanDiagnosticsTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearClasspath() {
        JMeterPluginClasspath.clear();
    }

    @Test
    void reportsEmbeddedRuntimeWhenNoExternalHomeConfigured() {
        String report = JMeterPlanDiagnostics.inspect(model()).format();

        assertTrue(report.contains("Environment:"));
        assertTrue(report.contains("- Runtime: embedded JMeter"));
        assertTrue(report.contains("- Plugin/runtime paths: 0"));
    }

    @Test
    void reportsConfiguredExternalRuntime() throws Exception {
        Path home = jmeterHome();
        JMeterPluginClasspath.addPath(home.toFile());

        String report = JMeterPlanDiagnostics.inspect(model()).format();

        assertTrue(report.contains("- Runtime: " + home.toFile().getCanonicalPath()));
        assertTrue(report.contains("- Plugin/runtime paths: "));
    }

    private JMeterTreeModel model() {
        TestPlan plan = new TestPlan("Plan");
        plan.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.control.gui.TestPlanGui");
        plan.setProperty(TestElement.TEST_CLASS, TestPlan.class.getName());
        return new JMeterTreeModel(plan);
    }

    private Path jmeterHome() throws Exception {
        Path home = tempDir.resolve("apache-jmeter");
        Files.createDirectories(home.resolve("bin"));
        Files.createDirectories(home.resolve("lib"));
        Files.createFile(home.resolve("bin/jmeter.properties"));
        return home;
    }
}

package com.github.kohebth.jmeterviewer.results;

import com.github.kohebth.jmeterviewer.runtime.EmbeddedJMeterRuntime;
import com.github.kohebth.jmeterviewer.runtime.JMeterPluginClasspath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

final class JMeterNativeVisualizerPanelTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearClasspath() {
        JMeterPluginClasspath.clear();
    }

    @Test
    void createsViewResultsTreeWithActivatedJMeterContext() throws Exception {
        EmbeddedJMeterRuntime.ensureReady();
        ClassLoader previous = JMeterPluginClasspath.activateThread();
        try {
            Object instance = JMeterPluginClasspath
                    .loadClass("org.apache.jmeter.visualizers.ViewResultsFullVisualizer")
                    .getDeclaredConstructor()
                    .newInstance();

            assertInstanceOf(JComponent.class, instance);
        } finally {
            JMeterPluginClasspath.restoreThread(previous);
        }
    }

    @Test
    void panelLoadsViewResultsTreeWithoutFallbackError() throws Exception {
        EmbeddedJMeterRuntime.ensureReady();
        JMeterNativeVisualizerPanel panel = new JMeterNativeVisualizerPanel(
                "View Results Tree", "org.apache.jmeter.visualizers.ViewResultsFullVisualizer");

        Method load = JMeterNativeVisualizerPanel.class.getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(panel);

        assertNull(fallback(panel));
    }

    @Test
    void panelLoadsViewResultsTreeWithExternalJMeterHomeConfigured() throws Exception {
        Path home = tempDir.resolve("apache-jmeter");
        Files.createDirectories(home.resolve("bin"));
        Files.createDirectories(home.resolve("lib"));
        Files.createFile(home.resolve("bin/jmeter.properties"));
        JMeterPluginClasspath.addPath(home.toFile());
        JMeterNativeVisualizerPanel panel = new JMeterNativeVisualizerPanel(
                "View Results Tree", "org.apache.jmeter.visualizers.ViewResultsFullVisualizer");

        Method load = JMeterNativeVisualizerPanel.class.getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(panel);

        assertNull(fallback(panel));
    }

    private JTextArea fallback(JMeterNativeVisualizerPanel panel) throws Exception {
        java.lang.reflect.Field field = JMeterNativeVisualizerPanel.class.getDeclaredField("fallback");
        field.setAccessible(true);
        return (JTextArea) field.get(panel);
    }
}

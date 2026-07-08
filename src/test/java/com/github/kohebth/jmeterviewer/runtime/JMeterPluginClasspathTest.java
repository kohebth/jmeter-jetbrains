package com.github.kohebth.jmeterviewer.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JMeterPluginClasspathTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearClasspath() {
        JMeterPluginClasspath.clear();
    }

    @Test
    void addsAndRemovesWholeJMeterHome() throws Exception {
        Path home = tempDir.resolve("apache-jmeter");
        Path bin = Files.createDirectories(home.resolve("bin"));
        Path lib = Files.createDirectories(home.resolve("lib"));
        Path ext = Files.createDirectories(lib.resolve("ext"));
        Files.createFile(bin.resolve("jmeter.properties"));
        Files.createFile(bin.resolve("ApacheJMeter.jar"));
        Files.createFile(lib.resolve("ApacheJMeter_core.jar"));
        Files.createFile(ext.resolve("plugin.jar"));

        JMeterPluginClasspath.addPath(home.toFile());

        java.util.Set<String> paths = paths();
        assertTrue(paths.contains(home.toFile().getCanonicalPath()));
        assertTrue(paths.contains(bin.toFile().getCanonicalPath()));
        assertTrue(paths.contains(lib.resolve("ApacheJMeter_core.jar").toFile().getCanonicalPath()));
        assertTrue(paths.contains(ext.resolve("plugin.jar").toFile().getCanonicalPath()));

        JMeterPluginClasspath.remove(home.toFile());

        String homePath = home.toFile().getCanonicalPath();
        assertFalse(paths().stream().anyMatch(path -> path.startsWith(homePath)));
    }

    @Test
    void detectsJMeterHomeFromCommonInstallChildren() throws Exception {
        Path home = jmeterHome();

        assertDetectedHome(home, home.resolve("bin"));
        assertDetectedHome(home, home.resolve("lib"));
        assertDetectedHome(home, home.resolve("lib/ext"));
        assertDetectedHome(home, home.resolve("bin/ApacheJMeter.jar"));
    }

    private void assertDetectedHome(Path expectedHome, Path selectedPath) throws Exception {
        JMeterPluginClasspath.clear();
        JMeterPluginClasspath.addPath(selectedPath.toFile());

        File home = JMeterPluginClasspath.jmeterHome();
        assertTrue(home != null, "Expected JMeter home for " + selectedPath);
        assertEquals(expectedHome.toFile().getCanonicalPath(), home.getCanonicalPath());
    }

    private Path jmeterHome() throws Exception {
        Path home = tempDir.resolve("apache-jmeter-" + System.nanoTime());
        Path bin = Files.createDirectories(home.resolve("bin"));
        Path lib = Files.createDirectories(home.resolve("lib"));
        Path ext = Files.createDirectories(lib.resolve("ext"));
        Files.createFile(bin.resolve("jmeter.properties"));
        Files.createFile(bin.resolve("ApacheJMeter.jar"));
        Files.createFile(lib.resolve("ApacheJMeter_core.jar"));
        Files.createFile(ext.resolve("plugin.jar"));
        return home;
    }

    private java.util.Set<String> paths() {
        return JMeterPluginClasspath.paths().stream()
                .map(this::canonicalPath)
                .collect(Collectors.toSet());
    }

    private String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception exception) {
            return file.getAbsolutePath();
        }
    }
}

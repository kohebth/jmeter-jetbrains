package com.github.kohebth.jmeterviewer.runtime;

import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.util.JMeterUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EmbeddedJMeterRuntime {
    private static final AtomicBoolean READY = new AtomicBoolean(false);

    private EmbeddedJMeterRuntime() {
    }

    public static void ensureReady() throws IOException {
        if (READY.get()) {
            return;
        }
        synchronized (EmbeddedJMeterRuntime.class) {
            if (READY.get()) {
                return;
            }
            initialize();
            READY.set(true);
        }
    }

    private static void initialize() throws IOException {
        Path home = Files.createTempDirectory("jetbrains-jmeter-home");
        Path bin = Files.createDirectories(home.resolve("bin"));
        copyResource("/bin/saveservice.properties", bin.resolve("saveservice.properties"));
        copyResource("/bin/upgrade.properties", bin.resolve("upgrade.properties"));
        copyResource("/bin/reportgenerator.properties", bin.resolve("reportgenerator.properties"));
        copyResourceTree("bin/report-template", bin.resolve("report-template"));
        copyResourceTree("bin/templates", bin.resolve("templates"));

        Path properties = bin.resolve("jmeter.properties");
        Files.writeString(properties, String.join(System.lineSeparator(),
                "saveservice_properties=saveservice.properties",
                "upgrade_properties=bin/upgrade.properties",
                "language=en",
                "jmeter.reportgenerator.overall_granularity=60000",
                ""));

        JMeterUtils.setJMeterHome(home.toString());
        JMeterUtils.loadJMeterProperties(properties.toString());
        JMeterUtils.initLocale();
        SaveService.loadProperties();
    }

    private static void copyResource(String resource, Path target) throws IOException {
        try (InputStream input = EmbeddedJMeterRuntime.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing bundled JMeter resource: " + resource);
            }
            Files.copy(input, target);
        }
    }

    private static void copyResourceTree(String resource, Path target) throws IOException {
        Enumeration<URL> roots = EmbeddedJMeterRuntime.class.getClassLoader().getResources(resource);
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            try {
                if ("jar".equals(root.getProtocol())) {
                    copyJarTree(root, resource, target);
                } else if ("file".equals(root.getProtocol())) {
                    copyFileTree(Paths.get(root.toURI()), target);
                }
            } catch (Exception exception) {
                if (exception instanceof IOException) {
                    throw (IOException) exception;
                }
                throw new IOException("Unable to copy resource directory: " + resource, exception);
            }
        }
        if (!Files.isDirectory(target)) {
            throw new IOException("Missing bundled JMeter resource directory: " + resource);
        }
    }

    private static void copyJarTree(URL root, String resource, Path target) throws IOException {
        try (JarFile jarFile = openJar(root)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().startsWith(resource + "/") || entry.isDirectory()) {
                    continue;
                }
                Path output = target.resolve(entry.getName().substring(resource.length() + 1));
                Files.createDirectories(output.getParent());
                try (InputStream input = jarFile.getInputStream(entry)) {
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static JarFile openJar(URL root) throws IOException {
        try {
            if (root.openConnection() instanceof JarURLConnection) {
                return ((JarURLConnection) root.openConnection()).getJarFile();
            }
        } catch (ClassCastException ignored) {
        }
        String spec = root.toExternalForm();
        int separator = spec.indexOf("!/");
        if (separator < 0) {
            throw new IOException("Unsupported bundled resource URL: " + spec);
        }
        String jarPath = spec.substring(0, separator);
        if (jarPath.startsWith("jar:")) {
            jarPath = jarPath.substring(4);
        }
        if (jarPath.startsWith("file:")) {
            jarPath = jarPath.substring(5);
        }
        return new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8.name()));
    }

    private static void copyFileTree(Path source, Path target) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(source)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    Path output = target.resolve(source.relativize(path).toString());
                    Files.createDirectories(output.getParent());
                    Files.copy(path, output, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof IOException) {
                throw (IOException) exception.getCause();
            }
            throw exception;
        }
    }
}

package com.github.duync.jmeterviewer;

import com.intellij.openapi.vfs.VirtualFile;
import org.apache.jmeter.util.JMeterUtils;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

final class JMeterPluginClasspath {
    private static final LinkedHashSet<File> PATHS = new LinkedHashSet<>();
    private static volatile URLClassLoader loader;

    private JMeterPluginClasspath() {
    }

    static synchronized void add(VirtualFile file) {
        if (file != null) {
            add(new File(file.getPath()));
            resetRuntimeViews();
        }
    }

    static synchronized void addPath(File file) {
        add(file);
        resetRuntimeViews();
    }

    static synchronized void addPaths(Collection<String> paths) {
        for (String path : paths) {
            if (path != null && !path.trim().isEmpty()) {
                add(new File(path.trim()));
            }
        }
        resetRuntimeViews();
    }

    static synchronized void remove(File file) {
        if (file != null) {
            File normalized = normalize(file);
            PATHS.removeIf(path -> samePath(path, normalized) || isChildJar(normalized, path));
            resetRuntimeViews();
        }
    }

    static synchronized void removeMissing() {
        PATHS.removeIf(path -> !path.exists());
        resetRuntimeViews();
    }

    static synchronized void clear() {
        PATHS.clear();
        resetRuntimeViews();
    }

    static synchronized List<File> paths() {
        return new ArrayList<>(PATHS);
    }

    static synchronized String[] searchPaths() {
        List<String> paths = new ArrayList<>();
        for (File path : PATHS) {
            paths.add(path.getAbsolutePath());
            if (path.isDirectory()) {
                File[] jars = path.listFiles(file -> file.getName().endsWith(".jar"));
                if (jars != null) {
                    for (File jar : jars) {
                        paths.add(jar.getAbsolutePath());
                    }
                }
            }
        }
        return paths.toArray(new String[0]);
    }

    static Class<?> loadClass(String className) throws ClassNotFoundException {
        URLClassLoader classLoader = classLoader();
        if (classLoader != null) {
            try {
                return Class.forName(className, true, classLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return Class.forName(className);
    }

    static void activate() {
        URLClassLoader classLoader = classLoader();
        if (classLoader != null) {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
    }

    private static void add(File file) {
        if (!file.exists()) {
            return;
        }
        PATHS.add(normalize(file));
        if (file.isDirectory()) {
            File[] jars = file.listFiles(child -> child.getName().endsWith(".jar"));
            if (jars != null) {
                for (File jar : jars) {
                    PATHS.add(normalize(jar));
                }
            }
        }
    }

    private static void resetRuntimeViews() {
        loader = null;
        updateJMeterSearchPaths();
        JMeterPaletteCatalog.reset();
    }

    private static void updateJMeterSearchPaths() {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(Arrays.asList(JMeterUtils.getSearchPaths()));
        values.addAll(Arrays.asList(searchPaths()));
        values.removeIf(String::isBlank);
        if (!values.isEmpty()) {
            JMeterUtils.setProperty("search_paths", String.join(File.pathSeparator, values));
        }
    }

    private static synchronized URLClassLoader classLoader() {
        if (PATHS.isEmpty()) {
            return null;
        }
        if (loader == null) {
            loader = new URLClassLoader(urls(), JMeterPluginClasspath.class.getClassLoader());
        }
        return loader;
    }

    private static URL[] urls() {
        List<URL> urls = new ArrayList<>();
        for (File path : PATHS) {
            try {
                urls.add(path.toURI().toURL());
            } catch (Exception ignored) {
            }
        }
        return urls.toArray(new URL[0]);
    }

    private static File normalize(File file) {
        try {
            return file.getCanonicalFile();
        } catch (Exception ignored) {
            return file.getAbsoluteFile();
        }
    }

    private static boolean samePath(File left, File right) {
        return normalize(left).equals(normalize(right));
    }

    private static boolean isChildJar(File directory, File path) {
        if (!directory.isDirectory() || !path.getName().endsWith(".jar")) {
            return false;
        }
        File parent = normalize(path).getParentFile();
        return parent != null && parent.equals(directory);
    }
}

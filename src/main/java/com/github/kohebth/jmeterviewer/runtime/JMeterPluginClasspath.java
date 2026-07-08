package com.github.kohebth.jmeterviewer.runtime;

import com.github.kohebth.jmeterviewer.palette.JMeterPaletteCatalog;

import com.intellij.openapi.vfs.VirtualFile;
import org.apache.jmeter.util.JMeterUtils;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public final class JMeterPluginClasspath {
    private static final LinkedHashSet<File> PATHS = new LinkedHashSet<>();
    private static volatile URLClassLoader loader;
    private static volatile File jmeterHome;

    private JMeterPluginClasspath() {
    }

    public static synchronized void add(VirtualFile file) {
        if (file != null) {
            add(new File(file.getPath()));
            resetRuntimeViews();
        }
    }

    public static synchronized void addPath(File file) {
        add(file);
        resetRuntimeViews();
    }

    public static synchronized void addPaths(Collection<String> paths) {
        for (String path : paths) {
            if (path != null && !path.trim().isEmpty()) {
                add(new File(path.trim()));
            }
        }
        resetRuntimeViews();
    }

    public static synchronized void remove(File file) {
        if (file != null) {
            File normalized = normalize(file);
            PATHS.removeIf(path -> samePath(path, normalized) || isChildJar(normalized, path));
            resetRuntimeViews();
        }
    }

    public static synchronized void removeMissing() {
        PATHS.removeIf(path -> !path.exists());
        resetRuntimeViews();
    }

    public static synchronized void clear() {
        PATHS.clear();
        jmeterHome = null;
        resetRuntimeViews();
    }

    public static synchronized List<File> paths() {
        return new ArrayList<>(PATHS);
    }

    public static synchronized File jmeterHome() {
        return jmeterHome;
    }

    public static synchronized String[] searchPaths() {
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

    public static Class<?> loadClass(String className) throws ClassNotFoundException {
        return loadClass(className, true);
    }

    public static Class<?> loadClassLazy(String className) throws ClassNotFoundException {
        return loadClass(className, false);
    }

    private static Class<?> loadClass(String className, boolean initialize) throws ClassNotFoundException {
        ClassLoader classLoader = activeClassLoader();
        if (classLoader != null) {
            try {
                return Class.forName(className, initialize, classLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return Class.forName(className, initialize, JMeterPluginClasspath.class.getClassLoader());
    }

    public static void activate() {
        Thread.currentThread().setContextClassLoader(activeClassLoader());
        activateJMeterHome();
    }

    public static ClassLoader activateThread() {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        activate();
        return previous;
    }

    public static void restoreThread(ClassLoader previous) {
        Thread.currentThread().setContextClassLoader(previous);
    }

    private static void add(File file) {
        if (!file.exists()) {
            return;
        }
        PATHS.add(normalize(file));
        File home = jmeterHome(file);
        if (home != null) {
            jmeterHome = home;
            addJMeterHome(home);
        } else if (file.isDirectory()) {
            addJars(file);
        }
    }

    private static void resetRuntimeViews() {
        loader = null;
        updateJMeterSearchPaths();
        JMeterPaletteCatalog.reset();
    }

    private static void updateJMeterSearchPaths() {
        Properties properties = JMeterUtils.getJMeterProperties();
        if (properties == null) {
            return;
        }
        activateJMeterHome();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(Arrays.asList(JMeterUtils.getSearchPaths()));
        values.addAll(Arrays.asList(searchPaths()));
        values.removeIf(String::isBlank);
        if (!values.isEmpty()) {
            properties.setProperty("search_paths", String.join(File.pathSeparator, values));
        }
    }

    private static void activateJMeterHome() {
        File home = jmeterHome;
        if (home == null || !home.isDirectory()) {
            return;
        }
        JMeterUtils.setJMeterHome(home.getAbsolutePath());
        File properties = new File(home, "bin/jmeter.properties");
        if (properties.isFile()) {
            JMeterUtils.loadJMeterProperties(properties.getAbsolutePath());
        }
    }

    private static void addJMeterHome(File home) {
        File lib = new File(home, "lib");
        File ext = new File(lib, "ext");
        addJars(lib);
        addJars(ext);
        PATHS.add(normalize(new File(home, "bin")));
    }

    private static void addJars(File directory) {
        File[] jars = directory.listFiles(child -> child.getName().endsWith(".jar"));
        if (jars == null) {
            return;
        }
        for (File jar : jars) {
            PATHS.add(normalize(jar));
        }
    }

    private static File jmeterHome(File file) {
        File normalized = normalize(file);
        if (looksLikeJMeterHome(normalized)) {
            return normalized;
        }
        File parentHome = parentJMeterHome(normalized);
        if (parentHome != null) {
            return parentHome;
        }
        if (normalized.isFile() && "jmeter.properties".equals(normalized.getName())) {
            File bin = normalized.getParentFile();
            File home = bin == null ? null : bin.getParentFile();
            return looksLikeJMeterHome(home) ? normalize(home) : null;
        }
        return null;
    }

    private static File parentJMeterHome(File file) {
        File current = file.isDirectory() ? file : file.getParentFile();
        for (int i = 0; i < 4 && current != null; i++) {
            File candidate = current.getParentFile();
            if (looksLikeJMeterHome(candidate)) {
                return normalize(candidate);
            }
            current = candidate;
        }
        return null;
    }

    private static boolean looksLikeJMeterHome(File directory) {
        return directory != null
                && new File(directory, "bin/jmeter.properties").isFile()
                && new File(directory, "lib").isDirectory();
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

    private static ClassLoader activeClassLoader() {
        URLClassLoader classLoader = classLoader();
        return classLoader == null ? JMeterPluginClasspath.class.getClassLoader() : classLoader;
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
        if (!directory.isDirectory()) {
            return false;
        }
        File parent = normalize(path).getParentFile();
        File normalizedDirectory = normalize(directory);
        while (parent != null) {
            if (parent.equals(normalizedDirectory)) {
                return true;
            }
            parent = parent.getParentFile();
        }
        return false;
    }
}

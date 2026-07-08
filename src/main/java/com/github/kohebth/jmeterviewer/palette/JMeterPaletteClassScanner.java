package com.github.kohebth.jmeterviewer.palette;

import com.github.kohebth.jmeterviewer.runtime.JMeterPluginClasspath;

import org.apache.jmeter.util.JMeterUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class JMeterPaletteClassScanner {
    private JMeterPaletteClassScanner() {
    }

    public static List<String> classNames() {
        List<String> names = new ArrayList<>();
        for (String path : searchPaths()) {
            names.addAll(classNames(path));
        }
        return names;
    }

    private static List<String> classNames(String path) {
        File file = new File(path);
        if (file.isFile() && file.getName().endsWith(".jar")) {
            return jarClassNames(file);
        }
        if (file.isDirectory()) {
            return directoryClassNames(file.toPath());
        }
        return Collections.emptyList();
    }

    private static List<String> jarClassNames(File jar) {
        List<String> names = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                addClassName(names, entries.nextElement().getName());
            }
        } catch (IOException ignored) {
        }
        return names;
    }

    private static List<String> directoryClassNames(Path root) {
        List<String> names = new ArrayList<>();
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(path ->
                    addClassName(names, root.relativize(path).toString().replace(File.separatorChar, '/')));
        } catch (IOException ignored) {
        }
        return names;
    }

    private static void addClassName(List<String> names, String entryName) {
        if (!entryName.endsWith(".class") || entryName.contains("$") || !entryName.startsWith("org/apache/jmeter/")) {
            return;
        }
        names.add(entryName.substring(0, entryName.length() - ".class".length()).replace('/', '.'));
    }

    private static String[] searchPaths() {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        paths.addAll(Arrays.asList(JMeterUtils.getSearchPaths()));
        paths.addAll(Arrays.asList(JMeterPluginClasspath.searchPaths()));
        addSystemClassPath(paths);
        addClassLoaderPaths(paths, Thread.currentThread().getContextClassLoader());
        addClassLoaderPaths(paths, JMeterPaletteClassScanner.class.getClassLoader());
        addKnownJMeterCodeSources(paths);
        paths.removeIf(path -> path == null || path.trim().isEmpty());
        return paths.toArray(new String[0]);
    }

    private static void addSystemClassPath(Set<String> paths) {
        String classPath = System.getProperty("java.class.path", "");
        if (classPath.isEmpty()) {
            return;
        }
        for (String path : classPath.split(File.pathSeparator)) {
            if (isJMeterPath(path)) {
                paths.add(path);
            }
        }
    }

    private static void addKnownJMeterCodeSources(Set<String> paths) {
        for (JMeterPaletteItem item : JMeterPaletteItem.DEFAULT_ITEMS) {
            addCodeSource(paths, item.guiClassName());
            addCodeSource(paths, item.testClassName());
        }
    }

    private static void addCodeSource(Set<String> paths, String className) {
        if (className == null) {
            return;
        }
        try {
            URL location = JMeterPluginClasspath.loadClass(className)
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
            if (location != null && "file".equals(location.getProtocol())) {
                String path = new File(location.getPath()).getAbsolutePath();
                if (isJMeterPath(path)) {
                    paths.add(path);
                }
            }
        } catch (Exception | LinkageError ignored) {
        }
    }

    private static void addClassLoaderPaths(Set<String> paths, ClassLoader classLoader) {
        ClassLoader current = classLoader;
        while (current != null) {
            for (URL url : urls(current)) {
                if ("file".equals(url.getProtocol())) {
                    String path = new File(url.getPath()).getAbsolutePath();
                    if (isJMeterPath(path)) {
                        paths.add(path);
                    }
                }
            }
            current = current.getParent();
        }
    }

    private static List<URL> urls(ClassLoader classLoader) {
        if (classLoader instanceof URLClassLoader) {
            return Arrays.asList(((URLClassLoader) classLoader).getURLs());
        }
        for (String methodName : new String[]{"getURLs", "getUrls"}) {
            try {
                Method method = classLoader.getClass().getMethod(methodName);
                Object value = method.invoke(classLoader);
                if (value instanceof URL[]) {
                    return Arrays.asList((URL[]) value);
                }
                if (value instanceof Collection) {
                    List<URL> urls = new ArrayList<>();
                    for (Object item : (Collection<?>) value) {
                        if (item instanceof URL) {
                            urls.add((URL) item);
                        }
                    }
                    return urls;
                }
            } catch (Exception ignored) {
            }
        }
        return Collections.emptyList();
    }

    private static boolean isJMeterPath(String path) {
        String name = new File(path).getName();
        return name.startsWith("ApacheJMeter") || name.startsWith("jorphan");
    }
}

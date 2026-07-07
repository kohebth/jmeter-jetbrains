package com.github.duync.jmeterviewer;

import org.apache.jmeter.gui.JMeterGUIComponent;
import org.apache.jmeter.gui.util.MenuFactory;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.reflect.ClassFinder;

import java.io.File;
import java.util.*;

final class JMeterPaletteDiscovery {
    private JMeterPaletteDiscovery() {
    }

    static List<JMeterPaletteItem> discover() {
        List<JMeterPaletteItem> items = new ArrayList<>();
        for (String className : guiClassNames()) {
            JMeterPaletteItem item = itemFor(className);
            if (item != null) {
                items.add(item);
            }
        }
        items.sort(Comparator.comparing(JMeterPaletteItem::toString));
        return items;
    }

    @SuppressWarnings("deprecation")
    private static List<String> guiClassNames() {
        try {
            return ClassFinder.findClassesThatExtend(
                    searchPaths(),
                    new Class<?>[]{JMeterGUIComponent.class},
                    false
            );
        } catch (Exception exception) {
            return Collections.emptyList();
        }
    }

    private static String[] searchPaths() {
        LinkedHashSet<String> paths = new LinkedHashSet<>(Arrays.asList(JMeterUtils.getSearchPaths()));
        String classPath = System.getProperty("java.class.path", "");
        if (!classPath.isEmpty()) {
            paths.addAll(Arrays.asList(classPath.split(File.pathSeparator)));
        }
        paths.addAll(Arrays.asList(JMeterPluginClasspath.searchPaths()));
        paths.removeIf(String::isEmpty);
        return paths.toArray(new String[0]);
    }

    private static JMeterPaletteItem itemFor(String className) {
        try {
            JMeterGUIComponent gui = (JMeterGUIComponent) JMeterPluginClasspath.loadClass(className)
                    .getDeclaredConstructor()
                    .newInstance();
            if (!gui.canBeAdded()) {
                return null;
            }
            JMeterPaletteItem.Kind kind = kindFor(gui.getMenuCategories());
            if (kind == null) {
                return null;
            }
            return JMeterPaletteItem.discovered(gui.getStaticLabel(), kind, className);
        } catch (Exception | LinkageError error) {
            return null;
        }
    }

    private static JMeterPaletteItem.Kind kindFor(Collection<String> categories) {
        if (categories == null) {
            return null;
        }
        if (categories.contains(MenuFactory.THREADS)) {
            return JMeterPaletteItem.Kind.THREAD_GROUP;
        }
        if (categories.contains(MenuFactory.FRAGMENTS)) {
            return JMeterPaletteItem.Kind.TEST_FRAGMENT;
        }
        if (categories.contains(MenuFactory.SAMPLERS)) {
            return JMeterPaletteItem.Kind.SAMPLER;
        }
        if (categories.contains(MenuFactory.CONTROLLERS)) {
            return JMeterPaletteItem.Kind.CONTROLLER;
        }
        if (categories.contains(MenuFactory.CONFIG_ELEMENTS)) {
            return JMeterPaletteItem.Kind.CONFIG;
        }
        if (categories.contains(MenuFactory.ASSERTIONS)) {
            return JMeterPaletteItem.Kind.ASSERTION;
        }
        if (categories.contains(MenuFactory.TIMERS)) {
            return JMeterPaletteItem.Kind.TIMER;
        }
        if (categories.contains(MenuFactory.PRE_PROCESSORS)) {
            return JMeterPaletteItem.Kind.PRE_PROCESSOR;
        }
        if (categories.contains(MenuFactory.POST_PROCESSORS)) {
            return JMeterPaletteItem.Kind.POST_PROCESSOR;
        }
        if (categories.contains(MenuFactory.LISTENERS)) {
            return JMeterPaletteItem.Kind.LISTENER;
        }
        if (categories.contains(MenuFactory.NON_TEST_ELEMENTS)) {
            return JMeterPaletteItem.Kind.NON_TEST;
        }
        return null;
    }
}

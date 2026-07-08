package com.github.kohebth.jmeterviewer.palette;

import com.github.kohebth.jmeterviewer.runtime.EmbeddedJMeterRuntime;
import com.github.kohebth.jmeterviewer.runtime.JMeterPluginClasspath;

import org.apache.jmeter.gui.JMeterGUIComponent;
import org.apache.jmeter.gui.util.MenuFactory;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.TestElement;

import java.util.*;

public final class JMeterPaletteDiscovery {
    private JMeterPaletteDiscovery() {
    }

    public static List<JMeterPaletteItem> discover() {
        List<JMeterPaletteItem> items = new ArrayList<>();
        try {
            EmbeddedJMeterRuntime.ensureReady();
        } catch (Exception exception) {
            return items;
        }
        ClassLoader previous = JMeterPluginClasspath.activateThread();
        try {
            items.addAll(scanSearchPaths());
        } finally {
            JMeterPluginClasspath.restoreThread(previous);
        }
        items.sort(Comparator.comparing(JMeterPaletteItem::toString));
        return items;
    }

    private static List<JMeterPaletteItem> scanSearchPaths() {
        List<JMeterPaletteItem> items = new ArrayList<>();
        for (String className : JMeterPaletteClassScanner.classNames()) {
            if (isPaletteCandidate(className) && isAddableClass(className)) {
                JMeterPaletteItem item = itemFor(className);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    private static boolean isAddableClass(String className) {
        try {
            Class<?> type = JMeterPluginClasspath.loadClassLazy(className);
            return JMeterGUIComponent.class.isAssignableFrom(type) || TestBean.class.isAssignableFrom(type);
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isPaletteCandidate(String className) {
        return className.contains(".gui.")
                || className.endsWith("Gui")
                || className.endsWith("GUI")
                || className.endsWith("Panel")
                || className.contains(".visualizers.")
                || className.contains(".testbeans.");
    }

    private static JMeterPaletteItem itemFor(String className) {
        try {
            Class<?> componentClass = JMeterPluginClasspath.loadClass(className);
            if (TestBean.class.isAssignableFrom(componentClass)) {
                JMeterPaletteItem.Kind kind = kindForTestElement(componentClass);
                if (kind == null) {
                    return null;
                }
                return JMeterPaletteItem.discovered(
                        labelForTestBean(componentClass), kind,
                        "org.apache.jmeter.testbeans.gui.TestBeanGUI", className);
            }
            if (!JMeterGUIComponent.class.isAssignableFrom(componentClass)) {
                return null;
            }
            JMeterGUIComponent gui = (JMeterGUIComponent) componentClass.getDeclaredConstructor().newInstance();
            if (!gui.canBeAdded()) {
                return null;
            }
            JMeterPaletteItem.Kind kind = kindFor(gui.getMenuCategories());
            if (kind == null) {
                return null;
            }
            TestElement element = gui.createTestElement();
            String testClass = element == null ? null : element.getClass().getName();
            return JMeterPaletteItem.discovered(gui.getStaticLabel(), kind, className, testClass);
        } catch (Exception | LinkageError error) {
            return null;
        }
    }

    private static String labelForTestBean(Class<?> componentClass) {
        try {
            return new org.apache.jmeter.testbeans.gui.TestBeanGUI(componentClass).getStaticLabel();
        } catch (Exception exception) {
            return componentClass.getSimpleName();
        }
    }

    private static JMeterPaletteItem.Kind kindForTestElement(Class<?> componentClass) {
        if (org.apache.jmeter.samplers.Sampler.class.isAssignableFrom(componentClass)) {
            return JMeterPaletteItem.Kind.SAMPLER;
        }
        if (org.apache.jmeter.config.ConfigElement.class.isAssignableFrom(componentClass)) {
            return JMeterPaletteItem.Kind.CONFIG;
        }
        if (org.apache.jmeter.assertions.Assertion.class.isAssignableFrom(componentClass)) {
            return JMeterPaletteItem.Kind.ASSERTION;
        }
        if (org.apache.jmeter.timers.Timer.class.isAssignableFrom(componentClass)) {
            return JMeterPaletteItem.Kind.TIMER;
        }
        if (org.apache.jmeter.processor.PreProcessor.class.isAssignableFrom(componentClass)) {
            return JMeterPaletteItem.Kind.PRE_PROCESSOR;
        }
        if (org.apache.jmeter.processor.PostProcessor.class.isAssignableFrom(componentClass)) {
            return JMeterPaletteItem.Kind.POST_PROCESSOR;
        }
        if (org.apache.jmeter.samplers.SampleListener.class.isAssignableFrom(componentClass)
                || org.apache.jmeter.visualizers.Visualizer.class.isAssignableFrom(componentClass)) {
            return JMeterPaletteItem.Kind.LISTENER;
        }
        return null;
    }

    private static JMeterPaletteItem.Kind kindFor(Collection<String> categories) {
        if (categories == null) {
            return null;
        }
        for (String category : categories) {
            JMeterPaletteItem.Kind kind = kindFor(category);
            if (kind != null) {
                return kind;
            }
        }
        return null;
    }

    private static JMeterPaletteItem.Kind kindFor(String category) {
        if (MenuFactory.THREADS.equals(category)) {
            return JMeterPaletteItem.Kind.THREAD_GROUP;
        }
        if (MenuFactory.FRAGMENTS.equals(category)) {
            return JMeterPaletteItem.Kind.TEST_FRAGMENT;
        }
        if (MenuFactory.SAMPLERS.equals(category)) {
            return JMeterPaletteItem.Kind.SAMPLER;
        }
        if (MenuFactory.CONTROLLERS.equals(category)) {
            return JMeterPaletteItem.Kind.CONTROLLER;
        }
        if (MenuFactory.CONFIG_ELEMENTS.equals(category)) {
            return JMeterPaletteItem.Kind.CONFIG;
        }
        if (MenuFactory.ASSERTIONS.equals(category)) {
            return JMeterPaletteItem.Kind.ASSERTION;
        }
        if (MenuFactory.TIMERS.equals(category)) {
            return JMeterPaletteItem.Kind.TIMER;
        }
        if (MenuFactory.PRE_PROCESSORS.equals(category)) {
            return JMeterPaletteItem.Kind.PRE_PROCESSOR;
        }
        if (MenuFactory.POST_PROCESSORS.equals(category)) {
            return JMeterPaletteItem.Kind.POST_PROCESSOR;
        }
        if (MenuFactory.LISTENERS.equals(category)) {
            return JMeterPaletteItem.Kind.LISTENER;
        }
        if (MenuFactory.NON_TEST_ELEMENTS.equals(category)) {
            return JMeterPaletteItem.Kind.NON_TEST;
        }
        return null;
    }
}

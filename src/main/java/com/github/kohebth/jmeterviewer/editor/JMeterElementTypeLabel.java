package com.github.kohebth.jmeterviewer.editor;

import com.github.kohebth.jmeterviewer.palette.JMeterPaletteItem;

import org.apache.jmeter.testelement.TestElement;

public final class JMeterElementTypeLabel {
    private JMeterElementTypeLabel() {
    }

    public static String of(TestElement element) {
        JMeterPaletteItem paletteItem = paletteItem(element);
        if (paletteItem != null) {
            return kindLabel(paletteItem.kind());
        }
        String className = element.getPropertyAsString(TestElement.TEST_CLASS);
        if (className == null || className.isEmpty()) {
            className = element.getClass().getName();
        }
        return readable(simpleName(className));
    }

    private static JMeterPaletteItem paletteItem(TestElement element) {
        String guiClass = element.getPropertyAsString(TestElement.GUI_CLASS);
        if (guiClass != null && !guiClass.isEmpty()) {
            JMeterPaletteItem byGui = JMeterPaletteItem.findByKey(guiClass);
            if (byGui != null) {
                return byGui;
            }
        }
        String testClass = element.getPropertyAsString(TestElement.TEST_CLASS);
        if (testClass != null && !testClass.isEmpty()) {
            JMeterPaletteItem byTestClass = JMeterPaletteItem.findByTestClass(testClass);
            if (byTestClass != null) {
                return byTestClass;
            }
        }
        return JMeterPaletteItem.findByTestClass(element.getClass().getName());
    }

    private static String kindLabel(JMeterPaletteItem.Kind kind) {
        switch (kind) {
            case THREAD_GROUP:
                return "Thread Group";
            case TEST_FRAGMENT:
                return "Test Fragment";
            case SAMPLER:
                return "Sampler";
            case CONTROLLER:
                return "Controller";
            case CONFIG:
                return "Config";
            case ASSERTION:
                return "Assertion";
            case TIMER:
                return "Timer";
            case PRE_PROCESSOR:
                return "Pre Processor";
            case POST_PROCESSOR:
                return "Post Processor";
            case LISTENER:
                return "Listener";
            case NON_TEST:
                return "Non-Test";
            default:
                return readable(kind.name());
        }
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    private static String readable(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        char previous = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (i > 0 && shouldSeparate(previous, current)) {
                builder.append(' ');
            }
            builder.append(current == '_' ? ' ' : current);
            previous = current;
        }
        return builder.toString().trim();
    }

    private static boolean shouldSeparate(char previous, char current) {
        return Character.isLowerCase(previous) && Character.isUpperCase(current)
                || Character.isLetter(previous) && Character.isDigit(current);
    }
}

package com.github.kohebth.jmeterviewer.editor;

import org.apache.jmeter.gui.GuiPackage;

import java.lang.reflect.Field;

public final class JMeterGuiPackageScope implements AutoCloseable {
    private static final Field GUI_PACK = guiPackField();
    private final GuiPackage previous;
    private boolean closed;

    private JMeterGuiPackageScope(GuiPackage previous) {
        this.previous = previous;
    }

    public static JMeterGuiPackageScope detach() {
        synchronized (JMeterGuiPackageScope.class) {
            GuiPackage previous = current();
            set(null);
            return new JMeterGuiPackageScope(previous);
        }
    }

    @Override
    public void close() {
        synchronized (JMeterGuiPackageScope.class) {
            if (closed) {
                return;
            }
            closed = true;
            if (current() == null) {
                set(previous);
            }
        }
    }

    private static GuiPackage current() {
        try {
            return (GuiPackage) GUI_PACK.get(null);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read JMeter GuiPackage", exception);
        }
    }

    private static void set(GuiPackage guiPackage) {
        try {
            GUI_PACK.set(null, guiPackage);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to update JMeter GuiPackage", exception);
        }
    }

    private static Field guiPackField() {
        try {
            Field field = GuiPackage.class.getDeclaredField("guiPack");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("Unable to find JMeter GuiPackage singleton", exception);
        }
    }
}

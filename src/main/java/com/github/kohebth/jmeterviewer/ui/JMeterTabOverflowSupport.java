package com.github.kohebth.jmeterviewer.ui;

import javax.swing.*;
import javax.swing.plaf.TabbedPaneUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class JMeterTabOverflowSupport {
    private static final String APPLIED = "jmeter.tab.overflow.applied";

    private JMeterTabOverflowSupport() {
    }

    public static JTabbedPane createTabbedPane() {
        return apply(new JTabbedPane());
    }

    public static <T extends Component> T apply(T component) {
        apply(component, Collections.newSetFromMap(new IdentityHashMap<>()));
        return component;
    }

    private static void apply(Component component, Set<Component> visited) {
        if (component == null || !visited.add(component)) {
            return;
        }
        if (component instanceof JTabbedPane) {
            applyTabbedPane((JTabbedPane) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                apply(child, visited);
            }
        }
    }

    private static void applyTabbedPane(JTabbedPane tabs) {
        if (Boolean.TRUE.equals(tabs.getClientProperty(APPLIED))) {
            return;
        }
        TabbedPaneUI ui = tabs.getUI();
        if (!(ui instanceof BasicTabbedPaneUI) || ui.getClass() != BasicTabbedPaneUI.class) {
            tabs.setUI(new BasicTabbedPaneUI());
        }
        if (tabs.getTabLayoutPolicy() != JTabbedPane.SCROLL_TAB_LAYOUT) {
            tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        }
        tabs.putClientProperty(APPLIED, Boolean.TRUE);
    }
}

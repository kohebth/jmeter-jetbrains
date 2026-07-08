package com.github.kohebth.jmeterviewer.ui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class JMeterTabOverflowSupportTest {
    @Test
    void createsScrollLayoutTabbedPane() {
        JTabbedPane tabs = JMeterTabOverflowSupport.createTabbedPane();

        assertEquals(JTabbedPane.SCROLL_TAB_LAYOUT, tabs.getTabLayoutPolicy());
        assertSame(BasicTabbedPaneUI.class, tabs.getUI().getClass());
    }

    @Test
    void toleratesCyclicContainerGraphs() {
        CyclicPanel panel = new CyclicPanel();
        JTabbedPane tabs = new JTabbedPane();
        panel.add(tabs);

        JMeterTabOverflowSupport.apply(panel);

        assertEquals(JTabbedPane.SCROLL_TAB_LAYOUT, tabs.getTabLayoutPolicy());
    }

    private static final class CyclicPanel extends JPanel {
        @Override
        public Component[] getComponents() {
            Component[] realComponents = super.getComponents();
            Component[] components = new Component[realComponents.length + 1];
            components[0] = this;
            System.arraycopy(realComponents, 0, components, 1, realComponents.length);
            return components;
        }
    }
}

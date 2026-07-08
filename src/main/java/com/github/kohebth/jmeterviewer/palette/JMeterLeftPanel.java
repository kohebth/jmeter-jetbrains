package com.github.kohebth.jmeterviewer.palette;

import com.github.kohebth.jmeterviewer.runtime.JMeterPluginPanel;
import com.github.kohebth.jmeterviewer.runtime.JMeterPropertiesPanel;
import com.github.kohebth.jmeterviewer.ui.JMeterTabOverflowSupport;

import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.BorderLayout;
import java.util.function.Supplier;

public final class JMeterLeftPanel {
    private JMeterLeftPanel() {
    }

    public static JComponent create(Project project) {
        JTabbedPane tabs = JMeterTabOverflowSupport.createTabbedPane();
        addLazyTab(tabs, "Elements", JMeterPalettePanel::create);
        addLazyTab(tabs, "Functions", JMeterFunctionPanel::create);
        addLazyTab(tabs, "Properties", () -> JMeterPropertiesPanel.create(project));
        addLazyTab(tabs, "Plugins", () -> JMeterPluginPanel.create(project));
        loadSelected(tabs);
        tabs.addChangeListener(event -> loadSelected(tabs));
        return tabs;
    }

    private static void addLazyTab(JTabbedPane tabs, String title, Supplier<JComponent> factory) {
        tabs.addTab(title, new LazyTab(factory));
    }

    private static void loadSelected(JTabbedPane tabs) {
        JComponent component = (JComponent) tabs.getSelectedComponent();
        if (component instanceof LazyTab) {
            ((LazyTab) component).load();
        }
    }

    private static final class LazyTab extends JPanel {
        private final Supplier<JComponent> factory;
        private boolean loaded;

        private LazyTab(Supplier<JComponent> factory) {
            super(new BorderLayout());
            this.factory = factory;
            add(new JLabel("Loading..."), BorderLayout.CENTER);
        }

        private void load() {
            if (loaded) {
                return;
            }
            loaded = true;
            removeAll();
            add(JMeterTabOverflowSupport.apply(factory.get()), BorderLayout.CENTER);
            revalidate();
            repaint();
        }
    }
}

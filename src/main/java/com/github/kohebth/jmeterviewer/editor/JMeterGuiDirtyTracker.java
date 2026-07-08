package com.github.kohebth.jmeterviewer.editor;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.TableModel;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;

public final class JMeterGuiDirtyTracker {
    private static final String ATTACHED = JMeterGuiDirtyTracker.class.getName() + ".attached";
    private static final String TEXT_DOCUMENT = JMeterGuiDirtyTracker.class.getName() + ".textDocument";
    private static final String TABLE_MODEL = JMeterGuiDirtyTracker.class.getName() + ".tableModel";
    private final Runnable dirty;

    public JMeterGuiDirtyTracker(Runnable dirty) {
        this.dirty = dirty;
    }

    public void watch(Component component) {
        if (component == null) {
            return;
        }
        attach(component);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                watch(child);
            }
        }
    }

    private void attach(Component component) {
        if (component instanceof JTextComponent) {
            attachText((JTextComponent) component);
        }
        if (component instanceof JTable) {
            attachTable((JTable) component);
        }
        if (isAttached(component)) {
            return;
        }
        markAttached(component);
        if (component instanceof AbstractButton) {
            attachButton((AbstractButton) component);
        }
        if (component instanceof JComboBox<?>) {
            attachCombo((JComboBox<?>) component);
        }
        if (component instanceof JSpinner) {
            attachSpinner((JSpinner) component);
        }
        if (component instanceof JSlider) {
            attachSlider((JSlider) component);
        }
    }

    private boolean isAttached(Component component) {
        return component instanceof JComponent
                && Boolean.TRUE.equals(((JComponent) component).getClientProperty(ATTACHED));
    }

    private void markAttached(Component component) {
        if (component instanceof JComponent) {
            ((JComponent) component).putClientProperty(ATTACHED, Boolean.TRUE);
        }
    }

    private void attachText(JTextComponent component) {
        Document document = component.getDocument();
        if (document == null || document == component.getClientProperty(TEXT_DOCUMENT)) {
            return;
        }
        component.putClientProperty(TEXT_DOCUMENT, document);
        document.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                dirty.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                dirty.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                dirty.run();
            }
        });
    }

    private void attachButton(AbstractButton button) {
        button.addItemListener(event -> dirty.run());
        button.addActionListener(event -> dirty.run());
    }

    private void attachCombo(JComboBox<?> comboBox) {
        comboBox.addItemListener(event -> dirty.run());
        comboBox.addActionListener(event -> dirty.run());
    }

    private void attachSpinner(JSpinner spinner) {
        spinner.addChangeListener(event -> dirty.run());
    }

    private void attachSlider(JSlider slider) {
        slider.addChangeListener(event -> dirty.run());
    }

    private void attachTable(JTable table) {
        TableModel model = table.getModel();
        if (model != null && model != table.getClientProperty(TABLE_MODEL)) {
            table.putClientProperty(TABLE_MODEL, model);
            model.addTableModelListener(event -> dirty.run());
        }
    }
}

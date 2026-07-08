package com.github.kohebth.jmeterviewer.validation;

import com.github.kohebth.jmeterviewer.results.JMeterResultsPanel;

import org.apache.jmeter.gui.tree.JMeterTreeModel;

import javax.swing.JButton;
import java.util.function.Supplier;

public final class JMeterValidationAction {
    private final JButton button;

    public JMeterValidationAction(Supplier<JMeterTreeModel> modelSupplier, JMeterResultsPanel resultsPanel) {
        button = new JButton("Validate");
        button.addActionListener(event -> resultsPanel.appendDiagnostic(
                JMeterPlanValidator.validate(modelSupplier.get())
        ));
    }

    public JButton button() {
        return button;
    }

    public void validateNow() {
        button.doClick();
    }
}

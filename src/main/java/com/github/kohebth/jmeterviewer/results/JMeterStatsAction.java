package com.github.kohebth.jmeterviewer.results;

import com.github.kohebth.jmeterviewer.validation.JMeterPlanStats;

import org.apache.jmeter.gui.tree.JMeterTreeModel;

import javax.swing.JButton;
import java.util.function.Supplier;

public final class JMeterStatsAction {
    private final JButton button;

    public JMeterStatsAction(Supplier<JMeterTreeModel> modelSupplier, JMeterResultsPanel resultsPanel) {
        button = new JButton("Stats");
        button.addActionListener(event -> resultsPanel.appendDiagnostic(
                JMeterPlanStats.describe(modelSupplier.get())
        ));
    }

    public JButton button() {
        return button;
    }
}

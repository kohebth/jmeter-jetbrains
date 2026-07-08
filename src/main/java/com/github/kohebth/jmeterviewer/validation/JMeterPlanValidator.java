package com.github.kohebth.jmeterviewer.validation;

import org.apache.jmeter.gui.tree.JMeterTreeModel;

public final class JMeterPlanValidator {
    private JMeterPlanValidator() {
    }

    public static String validate(JMeterTreeModel model) {
        return JMeterPlanDiagnostics.inspect(model).format();
    }
}

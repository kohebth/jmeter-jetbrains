package com.github.kohebth.jmeterviewer.editor;

import com.github.kohebth.jmeterviewer.palette.JMeterPaletteItem;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.config.gui.ArgumentsPanel;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.control.gui.LoopControlPanel;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.testelement.TestElement;

public final class JMeterElementMetadata {
    private JMeterElementMetadata() {
    }

    public static void normalize(TestElement element) {
        if (element == null) {
            return;
        }
        ensureTestClass(element);
        ensureGuiClass(element);
        ensureTestPlanVariables(element);
        ensureThreadGroupController(element);
    }

    private static void ensureTestClass(TestElement element) {
        if (hasValue(element, TestElement.TEST_CLASS)) {
            return;
        }
        element.setProperty(TestElement.TEST_CLASS, element.getClass().getName());
    }

    private static void ensureGuiClass(TestElement element) {
        if (hasValue(element, TestElement.GUI_CLASS)) {
            return;
        }
        JMeterPaletteItem item = JMeterPaletteItem.findByTestClass(
                element.getPropertyAsString(TestElement.TEST_CLASS)
        );
        if (item != null && item.guiClassName() != null) {
            element.setProperty(TestElement.GUI_CLASS, item.guiClassName());
        }
    }

    private static boolean hasValue(TestElement element, String key) {
        String value = element.getPropertyAsString(key);
        return value != null && !value.trim().isEmpty();
    }

    private static void ensureTestPlanVariables(TestElement element) {
        if (!(element instanceof TestPlan)) {
            return;
        }

        TestPlan testPlan = (TestPlan) element;
        JMeterProperty property = testPlan.getUserDefinedVariablesAsProperty();
        Object value = property == null ? null : property.getObjectValue();
        if (value instanceof Arguments) {
            normalize((Arguments) value);
            return;
        }

        Arguments arguments = new Arguments();
        arguments.setName("User Defined Variables");
        assignClasses(arguments, ArgumentsPanel.class, Arguments.class);
        testPlan.setUserDefinedVariables(arguments);
    }

    private static void ensureThreadGroupController(TestElement element) {
        if (!(element instanceof AbstractThreadGroup)) {
            return;
        }

        AbstractThreadGroup threadGroup = (AbstractThreadGroup) element;
        JMeterProperty property = threadGroup.getProperty(AbstractThreadGroup.MAIN_CONTROLLER);
        Object value = property == null ? null : property.getObjectValue();
        if (value instanceof LoopController) {
            normalize((LoopController) value);
            return;
        }

        LoopController controller = new LoopController();
        controller.setName("Loop Controller");
        controller.setLoops(1);
        controller.setContinueForever(false);
        assignClasses(controller, LoopControlPanel.class, LoopController.class);
        threadGroup.setSamplerController(controller);
    }

    private static void assignClasses(TestElement element, Class<?> guiClass, Class<?> testClass) {
        element.setProperty(TestElement.GUI_CLASS, guiClass.getName());
        element.setProperty(TestElement.TEST_CLASS, testClass.getName());
    }
}

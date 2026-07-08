package com.github.kohebth.jmeterviewer.run;

import com.github.kohebth.jmeterviewer.editor.JMeterElementMetadata;

import org.apache.jmeter.control.Controller;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jorphan.collections.HashTree;

public final class JMeterRunTreePreparer {
    private JMeterRunTreePreparer() {
    }

    public static void prepare(HashTree tree) {
        if (tree == null) {
            return;
        }
        for (Object item : tree.getArray()) {
            if (item instanceof TestElement) {
                prepareElement((TestElement) item);
            }
            prepare(tree.getTree(item));
        }
    }

    private static void prepareElement(TestElement element) {
        JMeterElementMetadata.normalize(element);
        if (element instanceof AbstractThreadGroup) {
            prepareThreadGroup((AbstractThreadGroup) element);
        }
        if (element instanceof org.apache.jmeter.threads.ThreadGroup) {
            prepareClassicThreadGroup((org.apache.jmeter.threads.ThreadGroup) element);
        }
    }

    private static void prepareThreadGroup(AbstractThreadGroup threadGroup) {
        ensureMainController(threadGroup);
        if (!hasValue(threadGroup, AbstractThreadGroup.ON_SAMPLE_ERROR)) {
            threadGroup.setProperty(AbstractThreadGroup.ON_SAMPLE_ERROR, AbstractThreadGroup.ON_SAMPLE_ERROR_CONTINUE);
        }
        if (!hasValue(threadGroup, AbstractThreadGroup.NUM_THREADS)) {
            threadGroup.setNumThreads(1);
        }
    }

    private static void ensureMainController(AbstractThreadGroup threadGroup) {
        Controller controller;
        try {
            controller = threadGroup.getSamplerController();
        } catch (RuntimeException exception) {
            controller = null;
        }
        if (controller != null) {
            return;
        }

        LoopController loopController = new LoopController();
        loopController.setName("Loop Controller");
        loopController.setEnabled(true);
        loopController.setLoops(1);
        loopController.setContinueForever(false);
        loopController.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.control.gui.LoopControlPanel");
        loopController.setProperty(TestElement.TEST_CLASS, LoopController.class.getName());
        threadGroup.setSamplerController(loopController);
    }

    private static void prepareClassicThreadGroup(org.apache.jmeter.threads.ThreadGroup threadGroup) {
        if (!hasValue(threadGroup, org.apache.jmeter.threads.ThreadGroup.RAMP_TIME)) {
            threadGroup.setRampUp(1);
        }
    }

    private static boolean hasValue(TestElement element, String key) {
        String value = element.getPropertyAsString(key);
        return hasText(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

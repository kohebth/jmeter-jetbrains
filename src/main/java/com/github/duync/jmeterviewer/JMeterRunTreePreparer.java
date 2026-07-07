package com.github.duync.jmeterviewer;

import org.apache.jmeter.control.Controller;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jorphan.collections.HashTree;

final class JMeterRunTreePreparer {
    private JMeterRunTreePreparer() {
    }

    static void prepare(HashTree tree) {
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
        if (element instanceof LoopController) {
            prepareLoopController((LoopController) element);
        }
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
            if (controller instanceof LoopController) {
                prepareLoopController((LoopController) controller);
            }
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

    private static void prepareLoopController(LoopController loopController) {
        String loops = loopController.getLoopString();
        if (!hasText(loops)) {
            loopController.setLoops(1);
            loopController.setContinueForever(false);
            return;
        }
        if (!String.valueOf(LoopController.INFINITE_LOOP_COUNT).equals(loops.trim())) {
            loopController.setContinueForever(false);
        }
    }

    private static void prepareClassicThreadGroup(org.apache.jmeter.threads.ThreadGroup threadGroup) {
        if (!hasValue(threadGroup, org.apache.jmeter.threads.ThreadGroup.RAMP_TIME)) {
            threadGroup.setRampUp(1);
        }
        if (threadGroup.getDuration() > 0 && !threadGroup.getScheduler()) {
            threadGroup.setScheduler(true);
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

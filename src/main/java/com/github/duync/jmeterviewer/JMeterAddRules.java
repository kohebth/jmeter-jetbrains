package com.github.duync.jmeterviewer;

import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.assertions.Assertion;
import org.apache.jmeter.config.ConfigElement;
import org.apache.jmeter.control.Controller;
import org.apache.jmeter.control.TestFragmentController;
import org.apache.jmeter.processor.PostProcessor;
import org.apache.jmeter.processor.PreProcessor;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.NonTestElement;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.timers.Timer;

final class JMeterAddRules {
    private JMeterAddRules() {
    }

    static boolean canAdd(JMeterTreeNode parent, JMeterPaletteItem item) {
        if (parent == null || item == null) {
            return false;
        }
        return canAddKind(parent.getTestElement(), item.kind());
    }

    static TestElement createAddableElement(JMeterTreeNode parent, JMeterPaletteItem item) {
        if (parent == null || item == null) {
            return null;
        }
        try {
            TestElement element = item.createTestElement();
            return canAddElement(parent, element) ? element : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    static boolean canAddElement(JMeterTreeNode parent, TestElement element) {
        if (parent == null || element == null) {
            return false;
        }
        TestElement parentElement = parent.getTestElement();
        if (parentElement instanceof TestPlan) {
            return element instanceof AbstractThreadGroup
                    || element instanceof TestFragmentController
                    || element instanceof ConfigElement
                    || element instanceof SampleListener;
        }
        if (isWorkBench(parentElement)) {
            return element instanceof NonTestElement || element instanceof TestElement;
        }
        if (parentElement instanceof AbstractThreadGroup || parentElement instanceof Controller) {
            return isThreadChild(element);
        }
        if (parentElement instanceof Sampler) {
            return isSamplerChild(element);
        }
        return false;
    }

    private static boolean isWorkBench(TestElement element) {
        return "org.apache.jmeter.testelement.WorkBench".equals(element.getClass().getName());
    }

    private static boolean canAddKind(TestElement parentElement, JMeterPaletteItem.Kind kind) {
        if (parentElement instanceof TestPlan) {
            return kind == JMeterPaletteItem.Kind.THREAD_GROUP
                    || kind == JMeterPaletteItem.Kind.TEST_FRAGMENT
                    || kind == JMeterPaletteItem.Kind.CONFIG
                    || kind == JMeterPaletteItem.Kind.LISTENER;
        }
        if (isWorkBench(parentElement)) {
            return true;
        }
        if (parentElement instanceof AbstractThreadGroup || parentElement instanceof Controller) {
            return isThreadChildKind(kind);
        }
        if (parentElement instanceof Sampler) {
            return isSamplerChildKind(kind);
        }
        return false;
    }

    private static boolean isThreadChildKind(JMeterPaletteItem.Kind kind) {
        return kind == JMeterPaletteItem.Kind.SAMPLER
                || kind == JMeterPaletteItem.Kind.CONTROLLER
                || kind == JMeterPaletteItem.Kind.CONFIG
                || kind == JMeterPaletteItem.Kind.ASSERTION
                || kind == JMeterPaletteItem.Kind.TIMER
                || kind == JMeterPaletteItem.Kind.PRE_PROCESSOR
                || kind == JMeterPaletteItem.Kind.POST_PROCESSOR
                || kind == JMeterPaletteItem.Kind.LISTENER;
    }

    private static boolean isSamplerChildKind(JMeterPaletteItem.Kind kind) {
        return kind == JMeterPaletteItem.Kind.ASSERTION
                || kind == JMeterPaletteItem.Kind.TIMER
                || kind == JMeterPaletteItem.Kind.PRE_PROCESSOR
                || kind == JMeterPaletteItem.Kind.POST_PROCESSOR
                || kind == JMeterPaletteItem.Kind.LISTENER;
    }

    private static boolean isThreadChild(TestElement element) {
        return element instanceof Sampler
                || element instanceof Controller
                || element instanceof ConfigElement
                || element instanceof Assertion
                || element instanceof Timer
                || element instanceof PreProcessor
                || element instanceof PostProcessor
                || element instanceof SampleListener;
    }

    private static boolean isSamplerChild(TestElement element) {
        return element instanceof Assertion
                || element instanceof Timer
                || element instanceof PreProcessor
                || element instanceof PostProcessor
                || element instanceof SampleListener;
    }
}

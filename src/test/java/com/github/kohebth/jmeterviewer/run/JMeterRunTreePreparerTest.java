package com.github.kohebth.jmeterviewer.run;

import com.github.kohebth.jmeterviewer.io.JMeterTreeLoader;

import org.apache.jmeter.control.Controller;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jorphan.collections.HashTree;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JMeterRunTreePreparerTest {
    @Test
    void addsMissingLoopControllerBeforeRun() {
        ThreadGroup threadGroup = new ThreadGroup();
        threadGroup.setName("No Loop Controller");
        HashTree tree = new HashTree();
        tree.add(threadGroup);

        JMeterRunTreePreparer.prepare(tree);

        Controller controller = threadGroup.getSamplerController();
        LoopController loopController = assertInstanceOf(LoopController.class, controller);
        assertEquals(1, loopController.getLoops());
        assertEquals(1, threadGroup.getNumThreads());
        assertEquals(AbstractThreadGroup.ON_SAMPLE_ERROR_CONTINUE,
                threadGroup.getPropertyAsString(AbstractThreadGroup.ON_SAMPLE_ERROR));
    }

    @Test
    void preservesSchedulerWhenDurationIsConfigured() {
        ThreadGroup threadGroup = new ThreadGroup();
        threadGroup.setName("Duration Group");
        threadGroup.setScheduler(false);
        threadGroup.setDuration(1);
        HashTree tree = new HashTree();
        tree.add(threadGroup);

        JMeterRunTreePreparer.prepare(tree);

        assertFalse(threadGroup.getScheduler());
        assertEquals(1, threadGroup.getDuration());
    }

    @Test
    void preservesExistingLoopControllerBeforeRun() {
        ThreadGroup threadGroup = new ThreadGroup();
        LoopController loopController = new LoopController();
        loopController.setLoops(2);
        threadGroup.setSamplerController(loopController);
        LoopController installed = assertInstanceOf(LoopController.class, threadGroup.getSamplerController());
        installed.setProperty("LoopController.continue_forever", true);
        String before = installed.getPropertyAsString("LoopController.continue_forever");
        HashTree tree = new HashTree();
        tree.add(threadGroup);

        JMeterRunTreePreparer.prepare(tree);

        LoopController prepared = assertInstanceOf(LoopController.class, threadGroup.getSamplerController());
        assertEquals(2, prepared.getLoops());
        assertEquals(before, prepared.getPropertyAsString("LoopController.continue_forever"));
    }

    @Test
    void preservesExistingThreadGroupRuntimePropertiesBeforeRun() {
        ThreadGroup threadGroup = new ThreadGroup();
        threadGroup.setNumThreads(7);
        threadGroup.setRampUp(3);
        threadGroup.setScheduler(false);
        threadGroup.setDuration(12);
        threadGroup.setDelay(4);
        threadGroup.setProperty(AbstractThreadGroup.ON_SAMPLE_ERROR, AbstractThreadGroup.ON_SAMPLE_ERROR_STOPTEST);
        HashTree tree = new HashTree();
        tree.add(threadGroup);

        JMeterRunTreePreparer.prepare(tree);

        assertEquals(7, threadGroup.getNumThreads());
        assertEquals(3, threadGroup.getRampUp());
        assertFalse(threadGroup.getScheduler());
        assertEquals(12, threadGroup.getDuration());
        assertEquals(4, threadGroup.getDelay());
        assertEquals(AbstractThreadGroup.ON_SAMPLE_ERROR_STOPTEST,
                threadGroup.getPropertyAsString(AbstractThreadGroup.ON_SAMPLE_ERROR));
    }

    @Test
    void runPreparationDoesNotMutateEditorModel() {
        JMeterTreeModel model = new JMeterTreeModel(new TestPlan("Plan"));
        JMeterTreeNode testPlan = (JMeterTreeNode) ((JMeterTreeNode) model.getRoot()).getChildAt(0);
        ThreadGroup threadGroup = new ThreadGroup();
        LoopController loopController = new LoopController();
        loopController.setLoops(2);
        threadGroup.setSamplerController(loopController);
        loopController.setContinueForever(true);
        model.insertNodeInto(new JMeterTreeNode(threadGroup, model), testPlan, testPlan.getChildCount());

        HashTree runTree = JMeterTreeLoader.toRunHashTree(model);
        JMeterRunTreePreparer.prepare(runTree);

        LoopController editorLoop = assertInstanceOf(LoopController.class, threadGroup.getSamplerController());
        assertTrue(editorLoop.getPropertyAsBoolean("LoopController.continue_forever"));
    }
}

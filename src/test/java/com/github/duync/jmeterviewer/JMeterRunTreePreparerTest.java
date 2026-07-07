package com.github.duync.jmeterviewer;

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
    void enablesSchedulerWhenDurationIsConfigured() {
        ThreadGroup threadGroup = new ThreadGroup();
        threadGroup.setName("Duration Group");
        threadGroup.setDuration(1);
        HashTree tree = new HashTree();
        tree.add(threadGroup);

        JMeterRunTreePreparer.prepare(tree);

        assertTrue(threadGroup.getScheduler());
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

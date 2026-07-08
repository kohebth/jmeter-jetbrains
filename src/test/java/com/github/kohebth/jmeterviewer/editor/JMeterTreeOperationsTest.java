package com.github.kohebth.jmeterviewer.editor;

import com.github.kohebth.jmeterviewer.io.JMeterTreeLoader;
import com.github.kohebth.jmeterviewer.palette.JMeterPaletteItem;
import com.github.kohebth.jmeterviewer.runtime.EmbeddedJMeterRuntime;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JMeterTreeOperationsTest {
    @BeforeAll
    static void setUpJMeter() throws Exception {
        EmbeddedJMeterRuntime.ensureReady();
    }

    @Test
    void duplicateMoveAndRemoveNode() throws Exception {
        JMeterTreeModel model = JMeterTreeLoader.load(new File("src/test/jmx/multiple-thread-groups.jmx"));
        JMeterTreeNode testPlan = testPlanNode(model);
        JMeterTreeNode firstThreadGroup = (JMeterTreeNode) testPlan.getChildAt(0);

        JMeterTreeNode duplicated = JMeterTreeOperations.duplicate(model, firstThreadGroup);
        assertNotNull(duplicated);
        assertEquals(firstThreadGroup.getName(), duplicated.getName());
        assertEquals(3, testPlan.getChildCount());

        assertTrue(JMeterTreeOperations.moveDown(model, firstThreadGroup));
        assertTrue(JMeterTreeOperations.moveUp(model, firstThreadGroup));
        assertTrue(JMeterTreeOperations.remove(model, duplicated));
        assertEquals(2, testPlan.getChildCount());
    }

    @Test
    void copyPastePreservesChildElements() throws Exception {
        JMeterTreeModel model = JMeterTreeLoader.load(new File("src/test/jmx/minimal.jmx"));
        JMeterTreeNode testPlan = testPlanNode(model);
        TestElement threadGroup = JMeterPaletteItem.findByKey("org.apache.jmeter.threads.gui.ThreadGroupGui").createTestElement();
        JMeterTreeNode threadGroupNode = new JMeterTreeNode(threadGroup, model);
        model.insertNodeInto(threadGroupNode, testPlan, testPlan.getChildCount());
        TestElement sampler = JMeterPaletteItem.findByKey("org.apache.jmeter.protocol.http.control.gui.HttpTestSampleGui").createTestElement();
        model.insertNodeInto(new JMeterTreeNode(sampler, model), threadGroupNode, threadGroupNode.getChildCount());

        JMeterTreeNode pasted = JMeterTreeOperations.paste(model, testPlan, threadGroupNode);

        assertNotNull(pasted);
        assertEquals(1, pasted.getChildCount());
        assertEquals(2, testPlan.getChildCount());
    }

    @Test
    void toggleEnabledUpdatesNodeAndTestElement() throws Exception {
        JMeterTreeModel model = JMeterTreeLoader.load(new File("src/test/jmx/multiple-thread-groups.jmx"));
        JMeterTreeNode testPlan = testPlanNode(model);
        JMeterTreeNode threadGroup = (JMeterTreeNode) testPlan.getChildAt(0);

        assertTrue(threadGroup.isEnabled());
        assertTrue(threadGroup.getTestElement().isEnabled());

        assertTrue(JMeterTreeOperations.toggleEnabled(model, threadGroup));
        assertEquals(false, threadGroup.isEnabled());
        assertEquals(false, threadGroup.getTestElement().isEnabled());

        assertTrue(JMeterTreeOperations.setEnabled(model, threadGroup, true));
        assertTrue(threadGroup.isEnabled());
        assertTrue(threadGroup.getTestElement().isEnabled());
    }

    private static JMeterTreeNode testPlanNode(JMeterTreeModel model) {
        return (JMeterTreeNode) ((JMeterTreeNode) model.getRoot()).getChildAt(0);
    }
}

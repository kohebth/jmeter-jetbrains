package com.github.kohebth.jmeterviewer.io;

import com.github.kohebth.jmeterviewer.palette.JMeterPaletteItem;
import com.github.kohebth.jmeterviewer.runtime.EmbeddedJMeterRuntime;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeListener;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JMeterTreeLoaderTest {
    @BeforeAll
    static void setUpJMeter() throws Exception {
        EmbeddedJMeterRuntime.ensureReady();
    }

    @Test
    void loadsMultipleThreadGroupsFixture() throws Exception {
        JMeterTreeModel model = JMeterTreeLoader.load(new File("src/test/jmx/multiple-thread-groups.jmx"));

        assertEquals("Multi Thread Plan", testPlanNode(model).getName());
        assertNotNull(findNode(model, "Browse Users"));
        assertNotNull(findNode(model, "Checkout Users"));
    }

    @Test
    void loadsTaurusControllersFixtureWhenGuiPackageAlreadyExists() throws Exception {
        JMeterTreeModel existingModel = new JMeterTreeModel();
        GuiPackage.initInstance(new JMeterTreeListener(existingModel), existingModel);

        JMeterTreeModel model = JMeterTreeLoader.load(new File("src/test/jmx/taurus-all-controllers.jmx"));

        assertEquals("Test Plan", testPlanNode(model).getName());
        assertNotNull(findNode(model, "IF"));
        assertNotNull(findNode(model, "WC"));
        assertNotNull(findNode(model, "View Results Tree"));
        assertTrue(countNodes(model) > 40);
    }

    @Test
    void savesAndReloadsModifiedTree() throws Exception {
        JMeterTreeModel model = JMeterTreeLoader.load(new File("src/test/jmx/minimal.jmx"));
        JMeterTreeNode testPlan = testPlanNode(model);
        TestElement threadGroup = JMeterPaletteItem.findByKey("org.apache.jmeter.threads.gui.ThreadGroupGui").createTestElement();
        model.insertNodeInto(new JMeterTreeNode(threadGroup, model), testPlan, testPlan.getChildCount());

        Path output = Files.createTempFile("jmeter-viewer-save", ".jmx");
        try (FileOutputStream stream = new FileOutputStream(output.toFile())) {
            SaveService.saveTree(JMeterTreeLoader.toHashTree(model), stream);
        }

        JMeterTreeModel reloaded = JMeterTreeLoader.load(output.toFile());
        assertNotNull(findNode(reloaded, "Thread Group"));
    }

    private static JMeterTreeNode testPlanNode(JMeterTreeModel model) {
        JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
        return (JMeterTreeNode) root.getChildAt(0);
    }

    private static JMeterTreeNode findNode(JMeterTreeModel model, String name) {
        Queue<JMeterTreeNode> queue = new ArrayDeque<>();
        queue.add((JMeterTreeNode) model.getRoot());
        while (!queue.isEmpty()) {
            JMeterTreeNode node = queue.remove();
            if (name.equals(node.getName())) {
                return node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                queue.add((JMeterTreeNode) node.getChildAt(i));
            }
        }
        return null;
    }

    private static int countNodes(JMeterTreeModel model) {
        int count = 0;
        Queue<JMeterTreeNode> queue = new ArrayDeque<>();
        queue.add((JMeterTreeNode) model.getRoot());
        while (!queue.isEmpty()) {
            JMeterTreeNode node = queue.remove();
            count++;
            for (int i = 0; i < node.getChildCount(); i++) {
                queue.add((JMeterTreeNode) node.getChildAt(i));
            }
        }
        return count;
    }
}

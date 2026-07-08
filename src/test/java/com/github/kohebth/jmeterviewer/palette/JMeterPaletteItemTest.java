package com.github.kohebth.jmeterviewer.palette;

import com.github.kohebth.jmeterviewer.editor.JMeterTreeOperations;
import com.github.kohebth.jmeterviewer.io.JMeterTreeLoader;
import com.github.kohebth.jmeterviewer.runtime.EmbeddedJMeterRuntime;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JMeterPaletteItemTest {
    @BeforeAll
    static void setUpJMeter() throws Exception {
        EmbeddedJMeterRuntime.ensureReady();
    }

    @Test
    void createsEveryPaletteElement() throws Exception {
        for (JMeterPaletteItem item : JMeterPaletteCatalog.items()) {
            TestElement element = item.createTestElement();
            assertNotNull(element, item.toString());
            assertNotNull(element.getPropertyAsString(TestElement.GUI_CLASS), item.toString());
            assertNotNull(element.getPropertyAsString(TestElement.TEST_CLASS), item.toString());
        }
    }

    @Test
    void discoversRuntimePaletteItems() {
        assertTrue(JMeterPaletteCatalog.items().size() >= JMeterPaletteItem.DEFAULT_ITEMS.size());
    }

    @Test
    void discoversMenuItemsWithUsableTestElements() throws Exception {
        JMeterPaletteItem mirrorServer = null;
        java.util.List<JMeterPaletteItem> discovered = JMeterPaletteDiscovery.discover();
        for (JMeterPaletteItem item : discovered) {
            if ("HTTP Mirror Server".equals(item.label())) {
                mirrorServer = item;
                break;
            }
        }

        assertNotNull(mirrorServer, discovered.toString());
        TestElement element = mirrorServer.createTestElement();
        assertEquals("org.apache.jmeter.protocol.http.control.HttpMirrorControl", element.getClass().getName());
    }

    @Test
    void usesUniquePaletteKeysForSharedGuiClasses() {
        long distinctKeys = JMeterPaletteCatalog.items().stream().map(JMeterPaletteItem::key).distinct().count();
        assertEquals(JMeterPaletteCatalog.items().size(), distinctKeys);
    }

    @Test
    void acceptsThreadGroupUnderTestPlanAndHttpSamplerUnderThreadGroup() throws Exception {
        JMeterTreeModel model = JMeterTreeLoader.load(new File("src/test/jmx/minimal.jmx"));
        JMeterTreeNode testPlan = (JMeterTreeNode) ((JMeterTreeNode) model.getRoot()).getChildAt(0);

        JMeterPaletteItem testFragment = JMeterPaletteItem.findByKey("org.apache.jmeter.control.gui.TestFragmentControllerGui");
        assertNotNull(testFragment);
        assertTrue(JMeterTreeOperations.canAdd(testPlan, testFragment));

        TestElement threadGroup = JMeterPaletteItem.findByKey("org.apache.jmeter.threads.gui.ThreadGroupGui").createTestElement();
        JMeterTreeNode threadGroupNode = new JMeterTreeNode(threadGroup, model);
        model.insertNodeInto(threadGroupNode, testPlan, testPlan.getChildCount());

        TestElement httpSampler = JMeterPaletteItem.findByKey("org.apache.jmeter.protocol.http.control.gui.HttpTestSampleGui").createTestElement();
        JMeterTreeNode httpSamplerNode = new JMeterTreeNode(httpSampler, model);
        model.insertNodeInto(httpSamplerNode, threadGroupNode, threadGroupNode.getChildCount());

        assertTrue(testPlan.getChildCount() > 0);
        assertTrue(threadGroupNode.getChildCount() > 0);
    }

    @Test
    void delegatesInvalidPlacementToJMeterRules() throws Exception {
        JMeterTreeModel model = JMeterTreeLoader.load(new File("src/test/jmx/minimal.jmx"));
        JMeterTreeNode testPlan = (JMeterTreeNode) ((JMeterTreeNode) model.getRoot()).getChildAt(0);

        JMeterPaletteItem sampler = JMeterPaletteItem.findByKey("org.apache.jmeter.protocol.http.control.gui.HttpTestSampleGui");
        JMeterPaletteItem threadGroup = JMeterPaletteItem.findByKey("org.apache.jmeter.threads.gui.ThreadGroupGui");

        assertFalse(JMeterTreeOperations.canAdd(testPlan, sampler));

        TestElement samplerElement = sampler.createTestElement();
        JMeterTreeNode samplerNode = new JMeterTreeNode(samplerElement, model);
        model.insertNodeInto(samplerNode, testPlan, testPlan.getChildCount());

        assertFalse(JMeterTreeOperations.canAdd(samplerNode, threadGroup));
    }
}

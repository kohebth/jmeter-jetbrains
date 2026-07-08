package com.github.kohebth.jmeterviewer.run;

import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.sampler.DebugSampler;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.SearchByClass;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class JMeterRunTreeBuilderTest {
    @Test
    void selectedThreadGroupKeepsTestLevelConfigAndOnlySelectedGroup() {
        JMeterTreeModel model = new JMeterTreeModel(new TestPlan("Plan"));
        JMeterTreeNode plan = (JMeterTreeNode) ((JMeterTreeNode) model.getRoot()).getChildAt(0);
        JMeterTreeNode config = insert(model, plan, httpDefaults());
        JMeterTreeNode first = insert(model, plan, threadGroup("First Group"));
        JMeterTreeNode second = insert(model, plan, threadGroup("Second Group"));
        insert(model, first, debugSampler("First Debug"));
        insert(model, second, debugSampler("Second Debug"));

        HashTree tree = JMeterRunTreeBuilder.selectedThreadGroup(model, second);

        assertEquals(List.of("Second Group"), names(tree, ThreadGroup.class));
        assertEquals(List.of("HTTP Defaults"), names(tree, ConfigTestElement.class));
        assertEquals(List.of("Second Debug"), names(tree, DebugSampler.class));
        assertEquals("HTTP Defaults", config.getName());
    }

    private JMeterTreeNode insert(JMeterTreeModel model, JMeterTreeNode parent, TestElement element) {
        JMeterTreeNode node = new JMeterTreeNode(element, model);
        model.insertNodeInto(node, parent, parent.getChildCount());
        return node;
    }

    private ThreadGroup threadGroup(String name) {
        ThreadGroup group = new ThreadGroup();
        group.setName(name);
        group.setEnabled(true);
        group.setSamplerController(loopController());
        group.setNumThreads(1);
        return group;
    }

    private LoopController loopController() {
        LoopController controller = new LoopController();
        controller.setName("Loop Controller");
        controller.setLoops(1);
        controller.setContinueForever(false);
        return controller;
    }

    private ConfigTestElement httpDefaults() {
        ConfigTestElement config = new ConfigTestElement();
        config.setName("HTTP Defaults");
        config.setProperty(TestElement.TEST_CLASS, ConfigTestElement.class.getName());
        config.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.protocol.http.config.gui.HttpDefaultsGui");
        return config;
    }

    private DebugSampler debugSampler(String name) {
        DebugSampler sampler = new DebugSampler();
        sampler.setName(name);
        sampler.setEnabled(true);
        return sampler;
    }

    private <T extends TestElement> List<String> names(HashTree tree, Class<T> type) {
        SearchByClass<T> search = new SearchByClass<>(type);
        tree.traverse(search);
        return search.getSearchResults().stream().map(TestElement::getName).collect(Collectors.toList());
    }
}

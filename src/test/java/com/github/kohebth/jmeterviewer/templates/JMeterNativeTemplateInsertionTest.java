package com.github.kohebth.jmeterviewer.templates;

import com.github.kohebth.jmeterviewer.runtime.EmbeddedJMeterRuntime;

import org.apache.jmeter.gui.action.template.TemplateManager;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.threads.ThreadGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class JMeterNativeTemplateInsertionTest {
    @Test
    void insertsNativeFragmentTemplateIntoSelectedNode() throws Exception {
        EmbeddedJMeterRuntime.ensureReady();
        org.apache.jmeter.gui.action.template.Template nativeTemplate =
                TemplateManager.getInstance().reset().getTemplateByName("BeanShell Sampler");
        JMeterTreeModel model = new JMeterTreeModel(new TestPlan("Plan"));
        JMeterTreeNode plan = (JMeterTreeNode) ((JMeterTreeNode) model.getRoot()).getChildAt(0);
        JMeterTreeNode group = new JMeterTreeNode(new ThreadGroup(), model);
        model.insertNodeInto(group, plan, plan.getChildCount());

        JMeterTreeNode inserted = JMeterNativeTemplateInsertion.insert(
                model, group, JMeterTemplate.nativeTemplate(nativeTemplate));

        assertNotNull(inserted);
        assertEquals("BeanShell Sampler", inserted.getName());
        assertEquals(group, inserted.getParent());
    }

    @Test
    void insertsNativeTestPlanTemplateWithNestedChildren() throws Exception {
        EmbeddedJMeterRuntime.ensureReady();
        org.apache.jmeter.gui.action.template.Template nativeTemplate =
                TemplateManager.getInstance().reset().getTemplateByName("Simple HTTP request");
        JMeterTreeModel model = new JMeterTreeModel(new TestPlan("Plan"));
        JMeterTreeNode plan = (JMeterTreeNode) ((JMeterTreeNode) model.getRoot()).getChildAt(0);

        JMeterTreeNode inserted = JMeterNativeTemplateInsertion.insert(
                model, plan, JMeterTemplate.nativeTemplate(nativeTemplate));

        assertNotNull(inserted);
        assertEquals(1, count(model, ThreadGroup.class));
        assertEquals(1, count(model, HTTPSamplerProxy.class));
    }

    private int count(JMeterTreeModel model, Class<?> type) {
        return count((JMeterTreeNode) model.getRoot(), type);
    }

    private int count(JMeterTreeNode node, Class<?> type) {
        int total = type.isInstance(node.getTestElement()) ? 1 : 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            total += count((JMeterTreeNode) node.getChildAt(i), type);
        }
        return total;
    }
}

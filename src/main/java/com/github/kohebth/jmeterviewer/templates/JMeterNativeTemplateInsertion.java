package com.github.kohebth.jmeterviewer.templates;

import com.github.kohebth.jmeterviewer.editor.JMeterTreeOperations;

import freemarker.template.Configuration;
import org.apache.jmeter.gui.action.template.Template;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jmeter.util.TemplateUtil;
import org.apache.jorphan.collections.HashTree;

import java.io.File;
import java.util.*;

public final class JMeterNativeTemplateInsertion {
    private JMeterNativeTemplateInsertion() {
    }

    public static JMeterTreeNode insert(org.apache.jmeter.gui.tree.JMeterTreeModel model,
                                 JMeterTreeNode selected,
                                 JMeterTemplate template) throws Exception {
        File file = render(template.nativeDefinition());
        HashTree tree = SaveService.loadTree(file);
        JMeterTreeNode parent = parent(model, selected, template.nativeDefinition().isTestPlan());
        JMeterTreeNode lastAdded = null;
        for (Entry entry : entries(tree, template.nativeDefinition().isTestPlan())) {
            JMeterTreeNode added = JMeterTreeOperations.addSubtree(
                    model, parent, entry.element, entry.children);
            if (added != null) {
                lastAdded = added;
            }
        }
        return lastAdded;
    }

    private static File render(Template template) throws Exception {
        File source = templateFile(template);
        if (template.getParameters() == null || template.getParameters().isEmpty()) {
            return source;
        }
        File output = File.createTempFile("jmeter-template-", ".jmx");
        output.deleteOnExit();
        Configuration configuration = TemplateUtil.getTemplateConfig();
        TemplateUtil.processTemplate(source, output, configuration, template.getParameters());
        return output;
    }

    private static File templateFile(Template template) {
        File parent = template.getParent();
        if (parent != null) {
            return new File(parent, template.getFileName());
        }
        return new File(JMeterUtils.getJMeterHome(), template.getFileName());
    }

    private static java.util.List<Entry> entries(HashTree tree, boolean testPlanTemplate) {
        java.util.List<Entry> entries = new ArrayList<>();
        if (!testPlanTemplate) {
            addEntries(entries, tree);
            return entries;
        }
        for (Object root : tree.getArray()) {
            if (root instanceof TestPlan) {
                addEntries(entries, tree.getTree(root));
            }
        }
        return entries;
    }

    private static void addEntries(java.util.List<Entry> entries, HashTree tree) {
        for (Object item : tree.getArray()) {
            if (item instanceof TestElement) {
                entries.add(new Entry((TestElement) item, tree.getTree(item)));
            }
        }
    }

    private static JMeterTreeNode parent(org.apache.jmeter.gui.tree.JMeterTreeModel model,
                                         JMeterTreeNode selected,
                                         boolean testPlanTemplate) {
        if (!testPlanTemplate && selected != null) {
            return selected;
        }
        JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
        return root.getChildCount() == 0 ? root : (JMeterTreeNode) root.getChildAt(0);
    }

    private static final class Entry {
        private final TestElement element;
        private final HashTree children;

        private Entry(TestElement element, HashTree children) {
            this.element = element;
            this.children = children;
        }
    }
}

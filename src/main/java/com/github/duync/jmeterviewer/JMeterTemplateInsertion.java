package com.github.duync.jmeterviewer;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;

final class JMeterTemplateInsertion {
    private JMeterTemplateInsertion() {
    }

    static JMeterTreeNode parent(JMeterTreeModel model, JMeterTreeNode selected, JMeterTemplate.Node template) {
        if (canInsert(selected, template)) {
            return selected;
        }
        JMeterTreeNode testPlan = testPlanNode(model);
        if (canInsert(testPlan, template)) {
            return testPlan;
        }
        return findCompatible((JMeterTreeNode) model.getRoot(), template);
    }

    private static boolean canInsert(JMeterTreeNode parent, JMeterTemplate.Node template) {
        if (parent == null) {
            return false;
        }
        JMeterPaletteItem item = JMeterPaletteItem.findByLabel(template.element());
        return item != null && JMeterTreeOperations.canAdd(parent, item);
    }

    private static JMeterTreeNode testPlanNode(JMeterTreeModel model) {
        JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
        return root.getChildCount() == 0 ? null : (JMeterTreeNode) root.getChildAt(0);
    }

    private static JMeterTreeNode findCompatible(JMeterTreeNode node, JMeterTemplate.Node template) {
        if (canInsert(node, template)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            JMeterTreeNode found = findCompatible((JMeterTreeNode) node.getChildAt(i), template);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}

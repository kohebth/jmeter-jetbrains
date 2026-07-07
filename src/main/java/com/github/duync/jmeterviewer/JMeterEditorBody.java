package com.github.duync.jmeterviewer;

import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;

final class JMeterEditorBody {
    private JMeterEditorBody() {
    }

    static JComponent create(JTree tree, JComponent details, JMeterSourcePanel sourcePanel) {
        JBSplitter treeAndDetails = new JBSplitter(false, 0.34f);
        treeAndDetails.setFirstComponent(new JBScrollPane(tree));
        treeAndDetails.setSecondComponent(details);

        JTabbedPane tabs = new JTabbedPane();
        JMeterTabOverflowSupport.apply(tabs);
        tabs.addTab("Visual", treeAndDetails);
        tabs.addTab("JMX Source", sourcePanel.component());
        return tabs;
    }
}

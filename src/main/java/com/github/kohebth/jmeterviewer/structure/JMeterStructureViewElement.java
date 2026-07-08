package com.github.kohebth.jmeterviewer.structure;

import com.github.kohebth.jmeterviewer.ide.JMeterFileType;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.Icon;
import java.util.List;

public final class JMeterStructureViewElement implements StructureViewTreeElement, ItemPresentation {
    private final Project project;
    private final VirtualFile file;
    private final JMeterStructureNode structureNode;

    public JMeterStructureViewElement(Project project, VirtualFile file, JMeterStructureNode structureNode) {
        this.project = project;
        this.file = file;
        this.structureNode = structureNode;
    }

    @Override
    public Object getValue() {
        return structureNode.node();
    }

    @Override
    public ItemPresentation getPresentation() {
        return this;
    }

    @Override
    public TreeElement[] getChildren() {
        List<JMeterStructureNode> children = structureNode.children();
        StructureViewTreeElement[] elements = new StructureViewTreeElement[children.size()];
        for (int i = 0; i < children.size(); i++) {
            elements[i] = new JMeterStructureViewElement(project, file, children.get(i));
        }
        return elements;
    }

    @Override
    public String getPresentableText() {
        return structureNode.node().name();
    }

    @Override
    public String getLocationString() {
        return structureNode.node().type();
    }

    @Override
    public Icon getIcon(boolean unused) {
        return JMeterFileType.INSTANCE.getIcon();
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (file != null && file.isValid()) {
            new OpenFileDescriptor(project, file, structureNode.offset()).navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return file != null && file.isValid();
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }
}

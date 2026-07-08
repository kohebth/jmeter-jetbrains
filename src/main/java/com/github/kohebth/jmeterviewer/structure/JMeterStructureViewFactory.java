package com.github.kohebth.jmeterviewer.structure;

import com.github.kohebth.jmeterviewer.ide.JMeterFileType;
import com.github.kohebth.jmeterviewer.io.JMeterTestPlanParser;
import com.github.kohebth.jmeterviewer.model.JMeterNode;
import com.github.kohebth.jmeterviewer.model.JMeterParseException;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

import java.util.List;

public final class JMeterStructureViewFactory implements PsiStructureViewFactory {
    @Override
    public TreeBasedStructureViewBuilder getStructureViewBuilder(PsiFile psiFile) {
        if (psiFile == null || psiFile.getFileType() != JMeterFileType.INSTANCE) {
            return null;
        }
        return new JMeterStructureViewBuilder(psiFile);
    }

    private static final class JMeterStructureViewBuilder extends TreeBasedStructureViewBuilder {
        private final PsiFile psiFile;

        private JMeterStructureViewBuilder(PsiFile psiFile) {
            this.psiFile = psiFile;
        }

        @Override
        public StructureViewModel createStructureViewModel(Editor editor) {
            String xml = psiFile.getText();
            JMeterStructureNode root = JMeterStructureNode.from(parse(xml), xml);
            return new StructureViewModelBase(
                    psiFile,
                    editor,
                    new JMeterStructureViewElement(psiFile.getProject(), psiFile.getVirtualFile(), root)
            );
        }

        @Override
        public boolean isRootNodeShown() {
            return true;
        }

        private JMeterNode parse(String xml) {
            try {
                return new JMeterTestPlanParser().parse(xml);
            } catch (JMeterParseException exception) {
                return new JMeterNode("JMX", "Unable to parse JMX", List.of(), List.of());
            }
        }
    }
}

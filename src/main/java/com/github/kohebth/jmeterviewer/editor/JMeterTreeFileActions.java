package com.github.kohebth.jmeterviewer.editor;

import com.github.kohebth.jmeterviewer.ide.JMeterIdeNotifications;
import com.github.kohebth.jmeterviewer.io.JMeterTreeLoader;

import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.save.SaveService;

import java.awt.datatransfer.StringSelection;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class JMeterTreeFileActions {
    private final Project project;
    private final JMeterTreeModel model;
    private final Supplier<JMeterTreeNode> selected;
    private final Consumer<JMeterTreeNode> selector;
    private final Runnable modified;

    public JMeterTreeFileActions(Project project,
                          JMeterTreeModel model,
                          Supplier<JMeterTreeNode> selected,
                          Consumer<JMeterTreeNode> selector,
                          Runnable modified) {
        this.project = project;
        this.model = model;
        this.selected = selected;
        this.selector = selector;
        this.modified = modified;
    }

    public void importJmx() {
        VirtualFile file = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFileDescriptor("jmx"),
                project,
                null
        );
        if (file != null) {
            importFrom(file);
        }
    }

    public void exportSelected() {
        JMeterTreeNode node = selected.get();
        if (node == null || node.getParent() == null) {
            return;
        }
        VirtualFileWrapper target = FileChooserFactory.getInstance()
                .createSaveFileDialog(new FileSaverDescriptor("Export JMeter Element", "Export selected subtree", "jmx"), project)
                .save(defaultExportName(node));
        if (target != null) {
            exportTo(node, target);
        }
    }

    public void exportNames() {
        JMeterTreeNode node = selectedOrRootChild();
        VirtualFileWrapper target = FileChooserFactory.getInstance()
                .createSaveFileDialog(new FileSaverDescriptor("Export JMeter Names",
                        "Export sampler and controller names", "txt"), project)
                .save("jmeter-names.txt");
        if (target != null) {
            writeText(target, JMeterTreeTextExporter.names(node), "Exported sampler and controller names");
        }
    }

    public void copyNames() {
        CopyPasteManager.getInstance().setContents(new StringSelection(
                JMeterTreeTextExporter.names(selectedOrRootChild())
        ));
        JMeterIdeNotifications.info(project, "Copied sampler and controller names");
    }

    public void copyOutline() {
        CopyPasteManager.getInstance().setContents(new StringSelection(
                JMeterTreeTextExporter.outline(selectedOrRootChild())
        ));
        JMeterIdeNotifications.info(project, "Copied JMeter tree outline");
    }

    public void copyCodeOutline() {
        CopyPasteManager.getInstance().setContents(new StringSelection(
                JMeterTreeTextExporter.codeOutline(selectedOrRootChild())
        ));
        JMeterIdeNotifications.info(project, "Copied JMeter code outline");
    }

    private void importFrom(VirtualFile file) {
        try {
            JMeterTreeModel sourceModel = JMeterTreeLoader.load(new java.io.File(file.getPath()));
            JMeterTreeNode destination = selectedOrRootChild();
            JMeterTreeNode last = mergeImport(destination, (JMeterTreeNode) sourceModel.getRoot());
            if (last != null) {
                selector.accept(last);
                modified.run();
            } else {
                JMeterIdeNotifications.warn(project, "No compatible JMX elements to import at the selected node");
            }
        } catch (Exception exception) {
            JMeterIdeNotifications.error(project, "Unable to import JMX: " + exception.getMessage());
        }
    }

    private JMeterTreeNode mergeImport(JMeterTreeNode destination, JMeterTreeNode sourceRoot) {
        JMeterTreeNode last = null;
        for (int i = 0; i < sourceRoot.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) sourceRoot.getChildAt(i);
            last = mergeNode(destination, child, last);
        }
        return last;
    }

    private JMeterTreeNode mergeNode(JMeterTreeNode destination, JMeterTreeNode source, JMeterTreeNode last) {
        JMeterTreeNode inserted = JMeterTreeOperations.paste(model, destination, source);
        if (inserted != null) {
            return inserted;
        }
        for (int i = 0; i < source.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) source.getChildAt(i);
            JMeterTreeNode merged = JMeterTreeOperations.paste(model, destination, child);
            if (merged != null) {
                last = merged;
            }
        }
        return last;
    }

    private void exportTo(JMeterTreeNode node, VirtualFileWrapper target) {
        try (FileOutputStream output = new FileOutputStream(target.getFile())) {
            SaveService.saveTree(JMeterTreeLoader.toHashTree(node), output);
            VirtualFile virtualFile = target.getVirtualFile(true);
            if (virtualFile != null) {
                virtualFile.refresh(false, false);
            }
            JMeterIdeNotifications.info(project, "Exported " + node.getName());
        } catch (Exception exception) {
            JMeterIdeNotifications.error(project, "Unable to export JMX: " + exception.getMessage());
        }
    }

    private void writeText(VirtualFileWrapper target, String text, String message) {
        try {
            java.nio.file.Files.write(target.getFile().toPath(), text.getBytes(StandardCharsets.UTF_8));
            VirtualFile virtualFile = target.getVirtualFile(true);
            if (virtualFile != null) {
                virtualFile.refresh(false, false);
            }
            JMeterIdeNotifications.info(project, message);
        } catch (Exception exception) {
            JMeterIdeNotifications.error(project, "Unable to export text: " + exception.getMessage());
        }
    }

    private JMeterTreeNode selectedOrRootChild() {
        JMeterTreeNode node = selected.get();
        if (node != null) {
            return node;
        }
        JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
        return root.getChildCount() == 0 ? root : (JMeterTreeNode) root.getChildAt(0);
    }

    private String defaultExportName(JMeterTreeNode node) {
        return node.getName().replaceAll("[^A-Za-z0-9._-]+", "-") + ".jmx";
    }
}

package com.github.kohebth.jmeterviewer.editor;

import com.github.kohebth.jmeterviewer.io.JMeterTreeLoader;

import com.intellij.openapi.ide.CopyPasteManager;
import org.apache.jmeter.gui.tree.*;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.collections.HashTree;

import java.awt.datatransfer.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JMeterXmlClipboard {
    private JMeterXmlClipboard() {
    }

    public static void write(JMeterTreeNode node) {
        if (node == null) {
            return;
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SaveService.saveTree(JMeterTreeLoader.toHashTree(node), output);
            String xml = output.toString(StandardCharsets.UTF_8.name());
            CopyPasteManager.getInstance().setContents(new StringSelection(xml));
        } catch (Exception ignored) {
        }
    }

    public static JMeterTreeNode read(JMeterTreeModel model) {
        try {
            Transferable contents = CopyPasteManager.getInstance().getContents();
            if (contents == null || !contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return null;
            }
            String xml = (String) contents.getTransferData(DataFlavor.stringFlavor);
            Path file = Files.createTempFile("jmeter-clipboard-", ".jmx");
            Files.write(file, xml.getBytes(StandardCharsets.UTF_8));
            HashTree tree = SaveService.loadTree(file.toFile());
            Files.deleteIfExists(file);
            Object[] roots = tree.getArray();
            if (roots.length == 0 || !(roots[0] instanceof TestElement)) {
                return null;
            }
            return nodeFrom(model, (TestElement) roots[0], tree.getTree(roots[0]));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JMeterTreeNode nodeFrom(JMeterTreeModel model, TestElement element, HashTree tree) {
        JMeterTreeNode node = new JMeterTreeNode((TestElement) element.clone(), model);
        node.setEnabled(element.isEnabled());
        for (Object child : tree.getArray()) {
            if (child instanceof TestElement) {
                node.add(nodeFrom(model, (TestElement) child, tree.getTree(child)));
            }
        }
        return node;
    }
}

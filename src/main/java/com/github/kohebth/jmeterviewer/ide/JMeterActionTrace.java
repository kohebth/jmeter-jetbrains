package com.github.kohebth.jmeterviewer.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;

public final class JMeterActionTrace {
    private static final Logger LOG = Logger.getInstance(JMeterActionTrace.class);

    private JMeterActionTrace() {
    }

    public static void info(String action) {
        write("JMeter action: " + action);
    }

    public static void info(String action, String detail) {
        write("JMeter action: " + action + " " + detail);
    }

    public static void warn(String action, Throwable throwable) {
        System.out.println("JMeter action failed: " + action + " " + throwable);
        LOG.warn("JMeter action failed: " + action, throwable);
    }

    private static void write(String message) {
        System.out.println(message);
        LOG.info(message);
    }

    public static String file(VirtualFile file) {
        return file == null ? "<none>" : file.getPath();
    }

    public static String currentNode() {
        GuiPackage guiPackage = GuiPackage.getInstance();
        return guiPackage == null ? "<none>" : node(guiPackage.getCurrentNode());
    }

    public static String node(JMeterTreeNode node) {
        if (node == null) {
            return "<none>";
        }
        Object userObject = node.getUserObject();
        if (userObject instanceof TestElement) {
            TestElement element = (TestElement) userObject;
            return quote(element.getName()) + "/" + element.getClass().getSimpleName();
        }
        return quote(node.getName());
    }

    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}

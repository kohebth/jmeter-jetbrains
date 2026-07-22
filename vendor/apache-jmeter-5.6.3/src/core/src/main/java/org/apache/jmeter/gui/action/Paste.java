/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jmeter.gui.action;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.jmeter.exceptions.IllegalUserActionException;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeListener;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.gui.util.MenuFactory;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.JMeterTreeNodeTransferable;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.service.AutoService;

/**
 * Places a copied JMeterTreeNode under the selected node.
 */
@AutoService(Command.class)
public class Paste extends AbstractAction {

    private static final Logger log = LoggerFactory.getLogger(Paste.class);

    private static final Set<String> commands = new HashSet<>();

    static {
        commands.add(ActionNames.PASTE);
    }

    /**
     * @see Command#getActionNames()
     */
    @Override
    public Set<String> getActionNames() {
        return commands;
    }

    /**
     * @see Command#doAction(ActionEvent)
     */
    @Override
    public void doAction(ActionEvent e) {
        byte[] portable = getCopiedFragment();
        if (portable != null) {
            try {
                importNodes(portable);
            } catch (Exception failure) {
                log.error("Unable to paste a portable JMeter fragment", failure);
                JMeterUtils.reportErrorToUser(failure.getMessage());
                Toolkit.getDefaultToolkit().beep();
            }
            return;
        }
        JMeterTreeNode[] draggedNodes = Copy.getCopiedNodes();
        if (draggedNodes == null) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        JMeterTreeListener treeListener = GuiPackage.getInstance().getTreeListener();
        JMeterTreeNode currentNode = treeListener.getCurrentNode();
        if (MenuFactory.canAddTo(currentNode, draggedNodes)) {
            Arrays.stream(draggedNodes)
                    .filter(Objects::nonNull)
                    .forEach(draggedNode -> addNode(currentNode, draggedNode));
        } else {
            Toolkit.getDefaultToolkit().beep();
        }
        GuiPackage.getInstance().getMainFrame().repaint();
    }

    public static int importNodes(byte[] portableJmx)
            throws IOException, IllegalUserActionException {
        Objects.requireNonNull(portableJmx, "portableJmx");
        GuiPackage guiPackage = GuiPackage.getInstance();
        JMeterTreeNode currentNode = guiPackage.getTreeListener().getCurrentNode();
        HashTree fragment = SaveService.loadTree(new ByteArrayInputStream(portableJmx));
        Object[] roots = fragment.getArray();
        if (roots.length == 0) {
            return 0;
        }
        for (Object root : roots) {
            if (!(root instanceof TestElement)) {
                throw new IllegalUserActionException("The clipboard does not contain JMeter elements");
            }
            if (!MenuFactory.canAddTo(currentNode, (TestElement) root)) {
                throw new IllegalUserActionException(
                        "The copied JMeter elements cannot be added to " + currentNode.getName());
            }
        }
        validateComponents(guiPackage, fragment);

        byte[] rollback = Copy.serializeTree(guiPackage.getTreeModel().getTestPlan());
        try {
            guiPackage.getTreeModel().addSubTree(fragment, currentNode);
        } catch (RuntimeException | IllegalUserActionException failure) {
            try {
                HashTree previous = SaveService.loadTree(new ByteArrayInputStream(rollback));
                Load.insertLoadedTree(0x4a4d58, previous, false);
            } catch (RuntimeException | IOException | IllegalUserActionException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
        guiPackage.getMainFrame().repaint();
        return roots.length;
    }

    private static void validateComponents(GuiPackage guiPackage, HashTree tree)
            throws IllegalUserActionException {
        for (Object candidate : tree.list()) {
            if (!(candidate instanceof TestElement)) {
                throw new IllegalUserActionException("The clipboard contains an invalid JMeter element");
            }
            try {
                guiPackage.getGui((TestElement) candidate);
            } catch (RuntimeException | LinkageError failure) {
                throw new IllegalUserActionException(
                        "A copied JMeter element or plugin is unavailable: "
                                + ((TestElement) candidate).getName(),
                        failure);
            }
            validateComponents(guiPackage, tree.getTree(candidate));
        }
    }

    private static byte[] getCopiedFragment() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        if (!clipboard.isDataFlavorAvailable(
                JMeterTreeNodeTransferable.JMETER_JMX_FRAGMENT_DATA_FLAVOR)) {
            return null;
        }
        try (InputStream input = (InputStream) clipboard.getData(
                JMeterTreeNodeTransferable.JMETER_JMX_FRAGMENT_DATA_FLAVOR);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (Exception failure) {
            log.error("Clipboard JMX fragment read error", failure);
            JMeterUtils.reportErrorToUser("Unable to read the copied JMeter fragment: "
                    + failure.getLocalizedMessage());
            return null;
        }
    }

    private static void addNode(JMeterTreeNode parent, JMeterTreeNode node) {
        try {
            // Add this node
            JMeterTreeNode newNode = GuiPackage.getInstance().getTreeModel().addComponent(node.getTestElement(), parent);
            // Add all the child nodes of the node we are adding
            for (int i = 0; i < node.getChildCount(); i++) {
                addNode(newNode, (JMeterTreeNode)node.getChildAt(i));
            }
        } catch (IllegalUserActionException iuae) {
            log.error("Illegal user action while adding a tree node.", iuae); // $NON-NLS-1$
            JMeterUtils.reportErrorToUser(iuae.getMessage());
        }
    }
}

package com.github.duync.jmeterviewer;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.jmeter.gui.tree.JMeterTreeModel;

import java.util.function.Consumer;

final class JMeterIdeUndoSupport {
    private final Project project;
    private final VirtualFile file;
    private final Consumer<JMeterTreeModel> restorer;
    private JMeterTreeSnapshot current;

    JMeterIdeUndoSupport(Project project, VirtualFile file, Consumer<JMeterTreeModel> restorer) {
        this.project = project;
        this.file = file;
        this.restorer = restorer;
    }

    void reset(JMeterTreeModel model) {
        current = JMeterTreeSnapshot.capture(model);
    }

    void record(JMeterTreeModel model) {
        UndoManager manager = UndoManager.getInstance(project);
        JMeterTreeSnapshot next = JMeterTreeSnapshot.capture(model);
        if (next == null || next.sameAs(current) || manager.isUndoOrRedoInProgress()) {
            return;
        }
        TreeEditAction action = new TreeEditAction(this, file, current, next);
        if (CommandProcessor.getInstance().getCurrentCommand() != null) {
            manager.undoableActionPerformed(action);
        } else {
            CommandProcessor.getInstance().executeCommand(project,
                    () -> manager.undoableActionPerformed(action),
                    "Edit JMeter Test Plan",
                    file);
        }
        current = next;
    }

    private void restore(JMeterTreeSnapshot snapshot) {
        JMeterTreeModel restored = snapshot == null ? null : snapshot.restore();
        if (restored != null) {
            current = snapshot;
            restorer.accept(restored);
        }
    }

    private static final class TreeEditAction extends BasicUndoableAction {
        private final JMeterIdeUndoSupport support;
        private final JMeterTreeSnapshot before;
        private final JMeterTreeSnapshot after;

        private TreeEditAction(JMeterIdeUndoSupport support,
                               VirtualFile file,
                               JMeterTreeSnapshot before,
                               JMeterTreeSnapshot after) {
            super(file);
            this.support = support;
            this.before = before;
            this.after = after;
        }

        @Override
        public void undo() {
            support.restore(before);
        }

        @Override
        public void redo() {
            support.restore(after);
        }
    }
}

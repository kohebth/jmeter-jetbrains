package com.github.duync.jmeterviewer;

import com.intellij.openapi.project.Project;

import javax.swing.JComponent;
import java.util.function.Supplier;
import org.apache.jmeter.gui.tree.JMeterTreeModel;

final class JMeterToolbarFactory {
    private JMeterToolbarFactory() {
    }

    static JComponent create(Project project,
                             Supplier<JMeterTreeModel> model,
                             JMeterEditorToolbarState state,
                             JMeterTreeActions treeActions,
                             JMeterTreeFileActions fileActions,
                             JMeterAddElementDialog addDialog,
                             JMeterTemplateDialog templates,
                             JMeterCommandPalette commands,
                             JMeterSearchController search) {
        return JMeterEditorToolbar.create(
                state.saveButton,
                new JMeterSaveAsAction(project, model).button(),
                state.reloadButton,
                state.runButton,
                state.stopButton,
                state.shutdownButton,
                state.resetEnginesButton,
                state.exitEnginesButton,
                state.resultFileLoader.button(),
                state.exportActions.samplesButton(),
                state.exportActions.logButton(),
                state.reportAction.button(),
                state.validationAction.button(),
                state.statsAction.button(),
                state.runStatusLabel,
                state.runOptions,
                state.threadControl,
                state.resultsPanel,
                treeActions,
                fileActions,
                addDialog,
                templates,
                commands,
                search
        );
    }
}

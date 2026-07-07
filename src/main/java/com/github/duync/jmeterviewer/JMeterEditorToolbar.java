package com.github.duync.jmeterviewer;

import com.intellij.ui.components.JBPanel;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class JMeterEditorToolbar {
    private JMeterEditorToolbar() {
    }

    static JComponent create(JButton saveButton,
                             JButton saveAsButton,
                             JButton reloadButton,
                             JButton runButton,
                             JButton runLocalButton,
                             JButton runRemoteButton,
                             JButton runAllButton,
                             JButton stopButton,
                             JButton shutdownButton,
                             JButton resetEnginesButton,
                             JButton exitEnginesButton,
                             JButton loadJtlButton,
                             JButton exportSamplesButton,
                             JButton exportJtlXmlButton,
                             JButton exportJtlCsvButton,
                             JButton exportLogButton,
                             JButton htmlReportButton,
                             JButton validateButton,
                             JButton statsButton,
                             JLabel runStatusLabel,
                             JMeterRunOptions runOptions,
                             JMeterThreadControlPanel threadControl,
                             JMeterResultsPanel resultsPanel,
                             JMeterTreeActions actions,
                             JMeterTreeFileActions fileActions,
                             JMeterAddElementDialog addDialog,
                             JMeterTemplateDialog templates,
                             JMeterCommandPalette commandPalette,
                             JMeterSearchController search) {
        JPanel toolbar = new JBPanel<>();
        toolbar.add(saveButton);
        toolbar.add(saveAsButton);
        toolbar.add(reloadButton);
        toolbar.add(runButton);
        toolbar.add(runLocalButton);
        toolbar.add(runRemoteButton);
        toolbar.add(runAllButton);
        toolbar.add(stopButton);
        toolbar.add(shutdownButton);
        toolbar.add(resetEnginesButton);
        toolbar.add(exitEnginesButton);
        toolbar.add(runOptions.component());
        toolbar.add(threadControl.component());
        toolbar.add(button("Clear Results", resultsPanel::clear));
        toolbar.add(button("Clear Samples", resultsPanel::clearResults));
        toolbar.add(button("Clear Log", resultsPanel::clearLog));
        toolbar.add(loadJtlButton);
        toolbar.add(exportSamplesButton);
        toolbar.add(exportJtlXmlButton);
        toolbar.add(exportJtlCsvButton);
        toolbar.add(exportLogButton);
        toolbar.add(htmlReportButton);
        toolbar.add(button("Next Fail", resultsPanel::selectNextFailure));
        toolbar.add(button("Prev Fail", resultsPanel::selectPreviousFailure));
        toolbar.add(validateButton);
        toolbar.add(statsButton);
        toolbar.add(runStatusLabel);
        toolbar.add(addDialog.button());
        toolbar.add(templates.button());
        toolbar.add(commandPalette.button());
        toolbar.add(button("Delete", actions::deleteSelected));
        toolbar.add(button("Duplicate", actions::duplicateSelected));
        toolbar.add(button("Dup Off", actions::duplicateSelectedDisabled));
        toolbar.add(button("Copy", actions::copySelected));
        toolbar.add(button("Cut", actions::cutSelected));
        toolbar.add(button("Paste", actions::pasteIntoSelected));
        toolbar.add(button("Import JMX", fileActions::importJmx));
        toolbar.add(button("Export Node", fileActions::exportSelected));
        toolbar.add(button("Export Names", fileActions::exportNames));
        toolbar.add(button("Copy Names", fileActions::copyNames));
        toolbar.add(button("Copy Outline", fileActions::copyOutline));
        toolbar.add(button("Copy Code", fileActions::copyCodeOutline));
        toolbar.add(button("Enable/Disable", actions::toggleSelectedEnabled));
        toolbar.add(button("Enable", actions::enableSelected));
        toolbar.add(button("Disable", actions::disableSelected));
        toolbar.add(button("Enable Tree", actions::enableSelectedTree));
        toolbar.add(button("Disable Tree", actions::disableSelectedTree));
        toolbar.add(button("Up", actions::moveSelectedUp));
        toolbar.add(button("Down", actions::moveSelectedDown));
        toolbar.add(button("Wrap", actions::insertSimpleControllerParent));
        toolbar.add(button("Parent Ctrl", actions::changeSelectedParentToSimpleController));
        toolbar.add(button("Think Times", actions::addThinkTimes));
        toolbar.add(button("Expand", actions::expandSelected));
        toolbar.add(button("Collapse", actions::collapseSelected));
        toolbar.add(new JLabel("Find"));
        toolbar.add(search.field());
        toolbar.add(button("Next", search::findNext));
        toolbar.add(button("Prev", search::findPrevious));
        toolbar.add(button("Search Plan", search::showDialog));
        toolbar.add(search.statusLabel());
        return toolbar;
    }

    private static JButton button(String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        return button;
    }
}

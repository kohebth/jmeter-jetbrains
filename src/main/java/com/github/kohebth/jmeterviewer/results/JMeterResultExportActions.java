package com.github.kohebth.jmeterviewer.results;

import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.JButton;
import java.io.File;

public final class JMeterResultExportActions {
    private final JButton samplesButton;
    private final JButton jtlXmlButton;
    private final JButton jtlCsvButton;
    private final JButton logButton;

    public JMeterResultExportActions(Project project, VirtualFile file, JMeterResultsPanel resultsPanel) {
        samplesButton = new JButton("Export Samples");
        jtlXmlButton = new JButton("Export JTL XML");
        jtlCsvButton = new JButton("Export JTL CSV");
        logButton = new JButton("Export Log");
        samplesButton.addActionListener(event -> {
            File folder = chooseFolder(project, file);
            if (folder != null) {
                resultsPanel.exportSamples(new File(folder, file.getName() + ".samples.csv"));
            }
        });
        jtlXmlButton.addActionListener(event -> {
            File folder = chooseFolder(project, file);
            if (folder != null) {
                resultsPanel.exportJtlXml(new File(folder, file.getName() + ".results.jtl"));
            }
        });
        jtlCsvButton.addActionListener(event -> {
            File folder = chooseFolder(project, file);
            if (folder != null) {
                resultsPanel.exportJtlCsv(new File(folder, file.getName() + ".results.csv"));
            }
        });
        logButton.addActionListener(event -> {
            File folder = chooseFolder(project, file);
            if (folder != null) {
                resultsPanel.exportLog(new File(folder, file.getName() + ".run.log"));
            }
        });
    }

    public JButton samplesButton() {
        return samplesButton;
    }

    public JButton jtlXmlButton() {
        return jtlXmlButton;
    }

    public JButton jtlCsvButton() {
        return jtlCsvButton;
    }

    public JButton logButton() {
        return logButton;
    }

    private File chooseFolder(Project project, VirtualFile file) {
        VirtualFile initial = file.getParent();
        VirtualFile folder = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                project,
                initial
        );
        return folder == null ? null : new File(folder.getPath());
    }
}

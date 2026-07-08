package com.github.kohebth.jmeterviewer.results;

import com.github.kohebth.jmeterviewer.ide.JMeterIdeNotifications;
import com.github.kohebth.jmeterviewer.runtime.EmbeddedJMeterRuntime;

import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.jmeter.reporters.*;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.save.SaveService;

import javax.swing.JButton;
import java.io.FileInputStream;

public final class JMeterResultFileLoader {
    private final Project project;
    private final JMeterResultsPanel resultsPanel;
    private final JButton button;

    public JMeterResultFileLoader(Project project, JMeterResultsPanel resultsPanel) {
        this.project = project;
        this.resultsPanel = resultsPanel;
        button = new JButton("Load JTL");
        button.addActionListener(event -> load());
    }

    public JButton button() {
        return button;
    }

    private void load() {
        VirtualFile file = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFileDescriptor(),
                project,
                null
        );
        if (file == null) {
            return;
        }
        try (FileInputStream input = new FileInputStream(file.getPath())) {
            EmbeddedJMeterRuntime.ensureReady();
            resultsPanel.clear();
            SaveService.loadTestResults(input, new PanelCollectorHelper(resultsPanel));
            resultsPanel.appendDiagnostic("Loaded results from " + file.getPath());
            JMeterResultsWorkspace.get(project).show();
        } catch (Exception exception) {
            JMeterIdeNotifications.error(project, "Unable to load JMeter results: " + exception.getMessage());
            resultsPanel.appendDiagnostic("Unable to load results: " + exception.getMessage());
        }
    }

    private static final class PanelCollectorHelper extends ResultCollectorHelper {
        private final JMeterResultsPanel panel;

        private PanelCollectorHelper(JMeterResultsPanel panel) {
            super(new ResultCollector(), null);
            this.panel = panel;
        }

        @Override
        public void add(SampleResult result) {
            panel.appendSample(result);
        }
    }
}

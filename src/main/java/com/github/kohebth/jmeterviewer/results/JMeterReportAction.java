package com.github.kohebth.jmeterviewer.results;

import com.github.kohebth.jmeterviewer.ide.JMeterIdeNotifications;
import com.github.kohebth.jmeterviewer.runtime.EmbeddedJMeterRuntime;

import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.jmeter.report.dashboard.ReportGenerator;
import org.apache.jmeter.util.JMeterUtils;

import javax.swing.JButton;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class JMeterReportAction {
    private final Project project;
    private final JMeterResultsPanel resultsPanel;
    private final JButton button;

    public JMeterReportAction(Project project, JMeterResultsPanel resultsPanel) {
        this.project = project;
        this.resultsPanel = resultsPanel;
        button = new JButton("HTML Report");
        button.addActionListener(event -> generate());
    }

    public JButton button() {
        return button;
    }

    private void generate() {
        try {
            EmbeddedJMeterRuntime.ensureReady();
            VirtualFile jtl = chooseJtl();
            if (jtl == null) {
                return;
            }
            File output = chooseOutputFolder();
            if (output == null) {
                return;
            }
            generateReport(new File(jtl.getPath()), output);
        } catch (Exception exception) {
            JMeterIdeNotifications.error(project, "Unable to generate JMeter report: " + exception.getMessage());
            resultsPanel.appendDiagnostic("Unable to generate report: " + exception.getMessage());
        }
    }

    private VirtualFile chooseJtl() {
        return FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFileDescriptor(),
                project,
                null
        );
    }

    private File chooseOutputFolder() {
        VirtualFile folder = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                project,
                null
        );
        if (folder == null) {
            return null;
        }
        return new File(folder.getPath(), "jmeter-report-" + timestamp());
    }

    private void generateReport(File jtl, File output) throws Exception {
        JMeterUtils.setProperty("jmeter.reportgenerator.outputdir", output.getAbsolutePath());
        JMeterUtils.setProperty("jmeter.reportgenerator.exporter.html.property.output_dir", output.getAbsolutePath());
        new ReportGenerator(jtl.getAbsolutePath(), null).generate();
        resultsPanel.appendDiagnostic("Generated HTML report at " + output.getAbsolutePath());
        JMeterIdeNotifications.info(project, "Generated JMeter HTML report");
    }

    private String timestamp() {
        return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
    }
}

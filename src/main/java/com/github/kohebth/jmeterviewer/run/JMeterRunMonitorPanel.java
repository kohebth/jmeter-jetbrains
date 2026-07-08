package com.github.kohebth.jmeterviewer.run;

import com.intellij.ui.components.JBPanel;
import org.apache.jmeter.samplers.SampleResult;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public final class JMeterRunMonitorPanel {
    private final JPanel panel;
    private final JLabel status;
    private final JLabel elapsed;
    private final JLabel samples;
    private final JLabel failures;
    private final JLabel errorRate;
    private final JLabel threads;
    private final Timer timer;
    private Instant startedAt;
    private int sampleCount;
    private int failureCount;
    private int activeThreads;

    public JMeterRunMonitorPanel() {
        status = value("Idle");
        elapsed = value("00:00");
        samples = value("0");
        failures = value("0");
        errorRate = value("0.00%");
        threads = value("0");
        panel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.add(label("Status", status));
        panel.add(label("Elapsed", elapsed));
        panel.add(label("Samples", samples));
        panel.add(label("Failures", failures));
        panel.add(label("Error", errorRate));
        panel.add(label("Threads", threads));
        timer = new Timer(1000, event -> refreshElapsed());
    }

    public JComponent component() {
        return panel;
    }

    public void reset() {
        timer.stop();
        startedAt = null;
        sampleCount = 0;
        failureCount = 0;
        activeThreads = 0;
        status.setText("Idle");
        refreshValues();
        elapsed.setText("00:00");
    }

    public void start() {
        sampleCount = 0;
        failureCount = 0;
        activeThreads = 0;
        startedAt = Instant.now();
        status.setText("Running");
        refreshValues();
        refreshElapsed();
        timer.start();
    }

    public void status(String value) {
        status.setText(value == null || value.isEmpty() ? "Idle" : value);
        if (isTerminal(value)) {
            timer.stop();
            refreshElapsed();
        }
    }

    public void sample(SampleResult result) {
        sampleCount++;
        if (result != null) {
            failureCount += result.isSuccessful() ? 0 : 1;
            activeThreads = Math.max(result.getAllThreads(), result.getGroupThreads());
        }
        refreshValues();
    }

    private JComponent label(String name, JLabel value) {
        JLabel label = new JLabel(name + ": ");
        JPanel pair = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        pair.setOpaque(false);
        pair.add(label);
        pair.add(value);
        return pair;
    }

    private JLabel value(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private void refreshValues() {
        samples.setText(String.valueOf(sampleCount));
        failures.setText(String.valueOf(failureCount));
        errorRate.setText(String.format(Locale.ROOT, "%.2f%%", sampleCount == 0
                ? 0.0d
                : 100.0d * failureCount / sampleCount));
        threads.setText(String.valueOf(activeThreads));
    }

    private void refreshElapsed() {
        if (startedAt == null) {
            elapsed.setText("00:00");
            return;
        }
        Duration duration = Duration.between(startedAt, Instant.now());
        long seconds = duration.getSeconds();
        elapsed.setText(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
    }

    private boolean isTerminal(String value) {
        if (value == null) {
            return false;
        }
        return value.startsWith("Finished")
                || value.startsWith("Run failed")
                || value.startsWith("Idle")
                || value.startsWith("Stopped");
    }

}

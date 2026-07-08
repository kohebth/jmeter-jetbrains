package com.github.kohebth.jmeterviewer.run;

import com.github.kohebth.jmeterviewer.results.JMeterResultsPanel;

import org.apache.jmeter.samplers.SampleResult;

import java.util.function.Consumer;

public final class JMeterEditorRunListener implements JMeterRunController.Listener {
    private final Consumer<String> statusConsumer;
    private final JMeterResultsPanel resultsPanel;
    private final JMeterThreadGroupActivity activity;

    public JMeterEditorRunListener(Consumer<String> statusConsumer,
                            JMeterResultsPanel resultsPanel,
                            JMeterThreadGroupActivity activity) {
        this.statusConsumer = statusConsumer;
        this.resultsPanel = resultsPanel;
        this.activity = activity;
    }

    @Override
    public void statusChanged(String status) {
        statusConsumer.accept(status);
        activity.status(status);
        resultsPanel.runStatusChanged(status);
    }

    @Override
    public void log(String message) {
        resultsPanel.appendDiagnostic(message);
    }

    @Override
    public void sampleOccurred(SampleResult result) {
        activity.sample(result);
        resultsPanel.appendSample(result);
    }
}

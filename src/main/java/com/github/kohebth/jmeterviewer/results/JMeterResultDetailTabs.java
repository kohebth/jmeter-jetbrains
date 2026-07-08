package com.github.kohebth.jmeterviewer.results;

import com.github.kohebth.jmeterviewer.ui.JMeterTabOverflowSupport;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.components.JBScrollPane;
import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.samplers.SampleResult;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

public final class JMeterResultDetailTabs {
    private final JTabbedPane tabs = JMeterTabOverflowSupport.createTabbedPane();
    private final JPanel panel = new JPanel(new BorderLayout());
    private final JTextArea sampler = area();
    private final JTextArea request = area();
    private final JTextArea response = area();
    private final JTextArea rendered = area();
    private final JTextArea assertions = area();
    private String requestText = "";
    private String responseText = "";
    private String renderedText = "";

    public JMeterResultDetailTabs() {
        panel.add(toolbar(), BorderLayout.NORTH);
        tabs.addTab("Sampler Result", new JBScrollPane(sampler));
        tabs.addTab("Request", new JBScrollPane(request));
        tabs.addTab("Response Data", new JBScrollPane(response));
        tabs.addTab("Rendered", new JBScrollPane(rendered));
        tabs.addTab("Assertions", new JBScrollPane(assertions));
        panel.add(tabs, BorderLayout.CENTER);
    }

    public JComponent component() {
        return panel;
    }

    public void clear() {
        set("", "", "", "", "");
    }

    public void showSample(SampleResult result) {
        set(
                sampler(result),
                request(result),
                response(result),
                JMeterResponseRenderer.render(result),
                assertions(result.getAssertionResults())
        );
    }

    public void showAssertion(AssertionResult result) {
        set(JMeterResultDetails.assertion(result), "", "", "", JMeterResultDetails.assertion(result));
        tabs.setSelectedIndex(4);
    }

    public void showText(String text) {
        set(text, "", "", "", "");
    }

    private void set(String samplerText, String requestText, String responseText, String renderedText, String assertionText) {
        this.requestText = requestText;
        this.responseText = responseText;
        this.renderedText = renderedText;
        setText(sampler, samplerText);
        setText(request, requestText);
        setText(response, responseText);
        setText(rendered, renderedText);
        setText(assertions, assertionText);
    }

    private String sampler(SampleResult result) {
        return "Label: " + safe(result.getSampleLabel()) + "\n"
                + "Status: " + (result.isSuccessful() ? "OK" : "FAIL") + "\n"
                + "Thread: " + safe(result.getThreadName()) + "\n"
                + "Time: " + result.getTime() + " ms\n"
                + "Latency: " + result.getLatency() + " ms\n"
                + "Connect: " + result.getConnectTime() + " ms\n"
                + "Idle: " + result.getIdleTime() + " ms\n"
                + "Response Code: " + safe(result.getResponseCode()) + "\n"
                + "Response Message: " + safe(result.getResponseMessage()) + "\n"
                + "Content Type: " + safe(result.getContentType()) + "\n"
                + "Data Encoding: " + safe(result.getDataEncodingNoDefault()) + "\n"
                + "URL: " + safe(result.getUrlAsString()) + "\n"
                + "Bytes: " + result.getBytesAsLong() + "\n"
                + "Sent Bytes: " + result.getSentBytes() + "\n"
                + "Headers Size: " + result.getHeadersSize() + "\n"
                + "Body Size: " + result.getBodySizeAsLong() + "\n";
    }

    private String request(SampleResult result) {
        return "Request Headers:\n" + safe(result.getRequestHeaders()) + "\n"
                + "\nSampler Data:\n" + safe(result.getSamplerData());
    }

    private String response(SampleResult result) {
        return "Response Headers:\n" + safe(result.getResponseHeaders()) + "\n"
                + "\nResponse Data:\n" + safe(result.getResponseDataAsString());
    }

    private String assertions(AssertionResult[] results) {
        if (results == null || results.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (AssertionResult result : results) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(JMeterResultDetails.assertion(result));
        }
        return builder.toString();
    }

    private JTextArea area() {
        JTextArea area = new JTextArea(8, 80);
        area.setEditable(false);
        return area;
    }

    private JComponent toolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        toolbar.add(button("Copy Request", () -> copy(requestText)));
        toolbar.add(button("Copy Response", () -> copy(responseText)));
        toolbar.add(button("Copy Rendered", () -> copy(renderedText)));
        return toolbar;
    }

    private JButton button(String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void copy(String text) {
        CopyPasteManager.getInstance().setContents(new StringSelection(text == null ? "" : text));
    }

    private void setText(JTextArea area, String text) {
        area.setText(text == null ? "" : text);
        area.setCaretPosition(0);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

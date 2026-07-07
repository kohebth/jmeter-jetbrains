package com.github.duync.jmeterviewer;

import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.assertions.AssertionResult;

import java.io.*;
import java.nio.charset.StandardCharsets;

final class JMeterSampleResultExporter {
    private JMeterSampleResultExporter() {
    }

    static void csv(JMeterSampleResultTableModel model, File file) throws IOException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write("label,success,thread,time,latency,connect,responseCode,responseMessage,url,bytes\n");
            writeEach(model, result -> writeLine(writer, JMeterResultDetails.csv(result)));
        }
    }

    static void jtlXml(JMeterSampleResultTableModel model, File file) throws IOException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testResults version=\"1.2\">\n");
            for (int i = 0; i < model.getRowCount(); i++) {
                SampleResult result = model.get(i);
                if (result != null) {
                    writeSample(writer, result, "  ");
                }
            }
            writer.write("</testResults>\n");
        }
    }

    static void jtlCsv(JMeterSampleResultTableModel model, File file) throws IOException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write("timeStamp,elapsed,label,responseCode,responseMessage,threadName,success,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect\n");
            writeEach(model, result -> writeLine(writer, jtlCsvLine(result)));
        }
    }

    private static void writeSample(Writer writer, SampleResult result, String indent) throws IOException {
        writer.write(indent);
        writer.write(sampleTag(result));
        writer.write(" t=\"");
        writer.write(String.valueOf(result.getTime()));
        writer.write("\" lt=\"");
        writer.write(String.valueOf(result.getLatency()));
        writer.write("\" ct=\"");
        writer.write(String.valueOf(result.getConnectTime()));
        writer.write("\" ts=\"");
        writer.write(String.valueOf(result.getStartTime()));
        writer.write("\" s=\"");
        writer.write(String.valueOf(result.isSuccessful()));
        writer.write("\" lb=\"");
        writer.write(xml(result.getSampleLabel()));
        writer.write("\" rc=\"");
        writer.write(xml(result.getResponseCode()));
        writer.write("\" rm=\"");
        writer.write(xml(result.getResponseMessage()));
        writer.write("\" tn=\"");
        writer.write(xml(result.getThreadName()));
        writer.write("\" by=\"");
        writer.write(String.valueOf(result.getBytesAsLong()));
        writer.write("\" sby=\"");
        writer.write(String.valueOf(result.getSentBytes()));
        writer.write("\" ng=\"");
        writer.write(String.valueOf(result.getGroupThreads()));
        writer.write("\" na=\"");
        writer.write(String.valueOf(result.getAllThreads()));
        writer.write("\" dt=\"");
        writer.write(xml(result.getDataType()));
        writer.write("\" de=\"");
        writer.write(xml(result.getDataEncodingNoDefault()));
        writer.write("\">\n");
        writeTextElement(writer, indent + "  ", "requestHeader", result.getRequestHeaders());
        writeTextElement(writer, indent + "  ", "responseHeader", result.getResponseHeaders());
        writeTextElement(writer, indent + "  ", "samplerData", result.getSamplerData());
        writeTextElement(writer, indent + "  ", "responseData", result.getResponseDataAsString());
        writeAssertions(writer, result, indent + "  ");
        writeSubResults(writer, result, indent + "  ");
        writer.write(indent);
        writer.write("</");
        writer.write(sampleTagName(result));
        writer.write(">\n");
    }

    private static void writeAssertions(Writer writer, SampleResult result, String indent) throws IOException {
        AssertionResult[] assertions = result.getAssertionResults();
        if (assertions == null) {
            return;
        }
        for (AssertionResult assertion : assertions) {
            writer.write(indent);
            writer.write("<assertionResult name=\"");
            writer.write(xml(assertion.getName()));
            writer.write("\" failure=\"");
            writer.write(String.valueOf(assertion.isFailure()));
            writer.write("\" error=\"");
            writer.write(String.valueOf(assertion.isError()));
            writer.write("\">");
            writer.write(xml(assertion.getFailureMessage()));
            writer.write("</assertionResult>\n");
        }
    }

    private static void writeSubResults(Writer writer, SampleResult result, String indent) throws IOException {
        SampleResult[] subResults = result.getSubResults();
        if (subResults == null) {
            return;
        }
        for (SampleResult subResult : subResults) {
            writeSample(writer, subResult, indent);
        }
    }

    private static void writeTextElement(Writer writer, String indent, String tag, String value) throws IOException {
        if (value == null || value.isEmpty()) {
            return;
        }
        writer.write(indent);
        writer.write("<");
        writer.write(tag);
        writer.write(">");
        writer.write(xml(value));
        writer.write("</");
        writer.write(tag);
        writer.write(">\n");
    }

    private static void writeEach(JMeterSampleResultTableModel model, SampleWriter writer) throws IOException {
        for (int i = 0; i < model.getRowCount(); i++) {
            writeRecursive(model.get(i), writer);
        }
    }

    private static void writeRecursive(SampleResult result, SampleWriter writer) throws IOException {
        if (result == null) {
            return;
        }
        writer.write(result);
        SampleResult[] subResults = result.getSubResults();
        if (subResults == null) {
            return;
        }
        for (SampleResult subResult : subResults) {
            writeRecursive(subResult, writer);
        }
    }

    private static void writeLine(Writer writer, String line) throws IOException {
        writer.write(line);
        writer.write("\n");
    }

    private static String sampleTag(SampleResult result) {
        return "<" + sampleTagName(result);
    }

    private static String sampleTagName(SampleResult result) {
        return result.getUrlAsString() == null || result.getUrlAsString().isEmpty() ? "sample" : "httpSample";
    }

    private static String jtlCsvLine(SampleResult result) {
        return result.getStartTime()
                + "," + result.getTime()
                + "," + quote(result.getSampleLabel())
                + "," + quote(result.getResponseCode())
                + "," + quote(result.getResponseMessage())
                + "," + quote(result.getThreadName())
                + "," + result.isSuccessful()
                + "," + result.getBytesAsLong()
                + "," + result.getSentBytes()
                + "," + result.getGroupThreads()
                + "," + result.getAllThreads()
                + "," + quote(result.getUrlAsString())
                + "," + result.getLatency()
                + "," + result.getIdleTime()
                + "," + result.getConnectTime();
    }

    private static String quote(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String xml(String value) {
        String text = value == null ? "" : value;
        return text.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private interface SampleWriter {
        void write(SampleResult result) throws IOException;
    }
}

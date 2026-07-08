package com.github.kohebth.jmeterviewer.results;

import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.samplers.SampleResult;

public final class JMeterResultDetails {
    private JMeterResultDetails() {
    }

    public static String sample(SampleResult result) {
        return "Label: " + result.getSampleLabel() + "\n"
                + "Status: " + (result.isSuccessful() ? "OK" : "FAIL") + "\n"
                + "Thread: " + result.getThreadName() + "\n"
                + "Time: " + result.getTime() + " ms\n"
                + "Latency: " + result.getLatency() + " ms\n"
                + "Connect: " + result.getConnectTime() + " ms\n"
                + "Response Code: " + result.getResponseCode() + "\n"
                + "Response Message: " + result.getResponseMessage() + "\n"
                + "URL: " + result.getUrlAsString() + "\n"
                + "Bytes: " + result.getBytesAsLong() + "\n"
                + "\nRequest Headers:\n" + nullToEmpty(result.getRequestHeaders()) + "\n"
                + "\nResponse Headers:\n" + nullToEmpty(result.getResponseHeaders()) + "\n"
                + "\nSampler Data:\n" + nullToEmpty(result.getSamplerData()) + "\n"
                + "\nResponse Data:\n" + nullToEmpty(result.getResponseDataAsString()) + "\n";
    }

    public static String assertion(AssertionResult result) {
        return "Assertion: " + nullToEmpty(result.getName()) + "\n"
                + "Status: " + assertionStatus(result) + "\n"
                + "\nFailure Message:\n" + nullToEmpty(result.getFailureMessage()) + "\n";
    }

    public static String csv(SampleResult result) {
        return quote(result.getSampleLabel())
                + "," + result.isSuccessful()
                + "," + quote(result.getThreadName())
                + "," + result.getTime()
                + "," + result.getLatency()
                + "," + result.getConnectTime()
                + "," + quote(result.getResponseCode())
                + "," + quote(result.getResponseMessage())
                + "," + quote(result.getUrlAsString())
                + "," + result.getBytesAsLong();
    }

    private static String assertionStatus(AssertionResult result) {
        if (result.isError()) {
            return "ERROR";
        }
        if (result.isFailure()) {
            return "FAIL";
        }
        return "OK";
    }

    private static String quote(String value) {
        String text = nullToEmpty(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

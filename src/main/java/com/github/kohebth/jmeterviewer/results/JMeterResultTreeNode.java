package com.github.kohebth.jmeterviewer.results;

import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.samplers.SampleResult;

import javax.swing.tree.DefaultMutableTreeNode;

public final class JMeterResultTreeNode {
    private final SampleResult sample;
    private final AssertionResult assertion;
    private final String groupLabel;

    private JMeterResultTreeNode(SampleResult sample, AssertionResult assertion, String groupLabel) {
        this.sample = sample;
        this.assertion = assertion;
        this.groupLabel = groupLabel;
    }

    public static DefaultMutableTreeNode sampleNode(SampleResult result) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new JMeterResultTreeNode(result, null, null));
        addAssertions(node, result);
        addSubResults(node, result);
        return node;
    }

    public void showDetails(JMeterResultDetailTabs tabs) {
        if (sample != null) {
            tabs.showSample(sample);
        } else if (assertion != null) {
            tabs.showAssertion(assertion);
        } else {
            tabs.showText(groupLabel == null ? "" : groupLabel);
        }
    }

    public boolean failed() {
        if (sample != null) {
            return !sample.isSuccessful();
        }
        return assertion != null && (assertion.isFailure() || assertion.isError());
    }

    private static void addAssertions(DefaultMutableTreeNode node, SampleResult result) {
        AssertionResult[] assertions = result.getAssertionResults();
        if (assertions == null || assertions.length == 0) {
            return;
        }

        DefaultMutableTreeNode group = new DefaultMutableTreeNode(new JMeterResultTreeNode(null, null, "Assertions"));
        for (AssertionResult assertion : assertions) {
            group.add(new DefaultMutableTreeNode(new JMeterResultTreeNode(null, assertion, null)));
        }
        node.add(group);
    }

    private static void addSubResults(DefaultMutableTreeNode node, SampleResult result) {
        SampleResult[] subResults = result.getSubResults();
        if (subResults == null) {
            return;
        }
        for (SampleResult subResult : subResults) {
            node.add(sampleNode(subResult));
        }
    }

    @Override
    public String toString() {
        if (sample != null) {
            return (sample.isSuccessful() ? "OK " : "FAIL ") + sample.getSampleLabel();
        }
        if (assertion != null) {
            return assertionPrefix(assertion) + assertion.getName();
        }
        return groupLabel == null ? "" : groupLabel;
    }

    private String assertionPrefix(AssertionResult result) {
        if (result.isError()) {
            return "ERROR ";
        }
        if (result.isFailure()) {
            return "FAIL ";
        }
        return "OK ";
    }
}

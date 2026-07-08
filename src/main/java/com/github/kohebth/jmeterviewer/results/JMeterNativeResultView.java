package com.github.kohebth.jmeterviewer.results;

public enum JMeterNativeResultView {
    VIEW_RESULTS_TREE("View Results Tree",
            "org.apache.jmeter.visualizers.ViewResultsFullVisualizer",
            "ViewResultsFullVisualizer", "org.apache.jmeter.visualizers.ViewResultsFullVisualizer"),
    VIEW_RESULTS_TABLE("View Results in Table",
            "org.apache.jmeter.visualizers.TableVisualizer",
            "TableVisualizer", "org.apache.jmeter.visualizers.TableVisualizer"),
    SUMMARY_REPORT("Summary Report",
            "org.apache.jmeter.visualizers.SummaryReport",
            "SummaryReport", "org.apache.jmeter.visualizers.SummaryReport"),
    AGGREGATE_REPORT("Aggregate Report",
            "org.apache.jmeter.visualizers.StatVisualizer",
            "StatVisualizer", "org.apache.jmeter.visualizers.StatVisualizer"),
    AGGREGATE_GRAPH("Aggregate Graph",
            "org.apache.jmeter.visualizers.StatGraphVisualizer",
            "StatGraphVisualizer", "org.apache.jmeter.visualizers.StatGraphVisualizer"),
    RESPONSE_TIME_GRAPH("Response Time Graph",
            "org.apache.jmeter.visualizers.RespTimeGraphVisualizer",
            "RespTimeGraphVisualizer", "org.apache.jmeter.visualizers.RespTimeGraphVisualizer"),
    GRAPH_RESULTS("Graph Results",
            "org.apache.jmeter.visualizers.GraphVisualizer",
            "GraphVisualizer", "org.apache.jmeter.visualizers.GraphVisualizer"),
    ASSERTION_RESULTS("Assertion Results",
            "org.apache.jmeter.visualizers.AssertionVisualizer",
            "AssertionVisualizer", "org.apache.jmeter.visualizers.AssertionVisualizer"),
    COMPARISON_ASSERTION("Comparison Assertion Visualizer",
            "org.apache.jmeter.visualizers.ComparisonVisualizer",
            "ComparisonVisualizer", "org.apache.jmeter.visualizers.ComparisonVisualizer");

    private final String title;
    private final String visualizerClass;
    private final String[] guiClasses;

    JMeterNativeResultView(String title, String visualizerClass, String... guiClasses) {
        this.title = title;
        this.visualizerClass = visualizerClass;
        this.guiClasses = guiClasses;
    }

    public String title() {
        return title;
    }

    public String visualizerClass() {
        return visualizerClass;
    }

    public String[] guiClasses() {
        return guiClasses;
    }
}

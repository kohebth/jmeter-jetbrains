package com.github.duync.jmeterviewer;

import java.util.*;

final class JMeterTemplate {
    private final String name;
    private final String description;
    private final java.util.List<Node> roots;
    private final boolean custom;

    private JMeterTemplate(String name, String description, Node... roots) {
        this(name, description, false, Arrays.asList(roots));
    }

    private JMeterTemplate(String name, String description, boolean custom, java.util.List<Node> roots) {
        this.name = name;
        this.description = description;
        this.custom = custom;
        this.roots = roots;
    }

    static JMeterTemplate custom(String name, String description, java.util.List<Node> roots) {
        return new JMeterTemplate(name, description, true, roots);
    }

    static java.util.List<JMeterTemplate> defaults() {
        return Arrays.asList(
                new JMeterTemplate(
                        "Basic HTTP Test Plan",
                        "Thread group with HTTP defaults, cookie/header managers, request, assertion and listeners.",
                        node("Thread Group",
                                node("HTTP Request Defaults"),
                                node("HTTP Cookie Manager"),
                                node("HTTP Header Manager"),
                                node("HTTP Request", node("Response Assertion")),
                                node("View Results Tree"),
                                node("Summary Report"))
                ),
                new JMeterTemplate(
                        "REST API Smoke Test",
                        "CSV-driven API skeleton with loop, JSON assertion, extractor and summary listener.",
                        node("Thread Group",
                                node("User Defined Variables"),
                                node("CSV Data Set Config"),
                                node("HTTP Request Defaults"),
                                node("HTTP Header Manager"),
                                node("Loop Controller",
                                        node("HTTP Request",
                                                node("JSON Assertion"),
                                                node("JSON Extractor"))),
                                node("Aggregate Report"))
                ),
                new JMeterTemplate(
                        "Transaction Flow",
                        "Transaction controller with timers, multiple HTTP requests and aggregate reporting.",
                        node("Thread Group",
                                node("HTTP Request Defaults"),
                                node("Constant Timer"),
                                node("Transaction Controller",
                                        node("HTTP Request"),
                                        node("HTTP Request")),
                                node("Duration Assertion"),
                                node("Aggregate Report"),
                                node("Response Time Graph"))
                ),
                new JMeterTemplate(
                        "Scripted Debug Plan",
                        "Debug sampler with user variables, JSR223 sampler/listener and result inspection.",
                        node("Thread Group",
                                node("User Defined Variables"),
                                node("Debug Sampler"),
                                node("JSR223 Sampler"),
                                node("JSR223 Listener"),
                                node("View Results Tree"))
                ),
                new JMeterTemplate(
                        "HTTP Recording Workbench",
                        "Recording controller plus WorkBench HTTP(S) Test Script Recorder for proxy capture.",
                        node("Thread Group",
                                node("Recording Controller"),
                                node("View Results Tree")),
                        node("WorkBench",
                                node("HTTP(S) Test Script Recorder"))
                ),
                new JMeterTemplate(
                        "JDBC Test Plan",
                        "JDBC connection config with request sampler, assertion and aggregate report.",
                        node("Thread Group",
                                node("JDBC Connection Configuration"),
                                node("JDBC Request",
                                        node("Response Assertion")),
                                node("Aggregate Report"))
                ),
                new JMeterTemplate(
                        "Messaging Test Plan",
                        "JMS point-to-point, publisher and subscriber skeleton with result inspection.",
                        node("Thread Group",
                                node("JMS Point-to-Point"),
                                node("JMS Publisher"),
                                node("JMS Subscriber"),
                                node("View Results Tree"))
                ),
                new JMeterTemplate(
                        "Directory And Socket Plan",
                        "LDAP and TCP samplers with matching defaults/config and summary listener.",
                        node("Thread Group",
                                node("LDAP Request Defaults"),
                                node("TCP Sampler Config"),
                                node("LDAP Request"),
                                node("TCP Sampler"),
                                node("Summary Report"))
                ),
                new JMeterTemplate(
                        "Mail And Process Plan",
                        "SMTP, mail reader and OS process samplers for integration smoke tests.",
                        node("Thread Group",
                                node("SMTP Sampler"),
                                node("Mail Reader Sampler"),
                                node("OS Process Sampler"),
                                node("View Results in Table"))
                ),
                new JMeterTemplate(
                        "Data Stores Plan",
                        "Bolt and MongoDB configuration/samplers with aggregate reporting.",
                        node("Thread Group",
                                node("Bolt Connection Configuration"),
                                node("MongoDB Source Config"),
                                node("Bolt Request"),
                                node("MongoDB Script"),
                                node("Aggregate Report"))
                ),
                new JMeterTemplate(
                        "Diagnostics Plan",
                        "Debug sampler, compare assertion, mailer visualizer and property display.",
                        node("Thread Group",
                                node("Debug Sampler",
                                        node("Compare Assertion")),
                                node("Mailer Visualizer"),
                                node("View Results Tree")),
                        node("Property Display")
                )
        );
    }

    String description() {
        return description;
    }

    java.util.List<Node> roots() {
        return roots;
    }

    boolean custom() {
        return custom;
    }

    String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    private static Node node(String element, Node... children) {
        return new Node(element, Arrays.asList(children));
    }

    static Node node(String element, java.util.List<Node> children) {
        return new Node(element, children);
    }

    static final class Node {
        private final String element;
        private final java.util.List<Node> children;

        private Node(String element, java.util.List<Node> children) {
            this.element = element;
            this.children = children;
        }

        String element() {
            return element;
        }

        java.util.List<Node> children() {
            return children;
        }
    }
}

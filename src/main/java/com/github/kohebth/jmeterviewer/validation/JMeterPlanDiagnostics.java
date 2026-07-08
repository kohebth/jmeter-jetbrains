package com.github.kohebth.jmeterviewer.validation;

import com.github.kohebth.jmeterviewer.runtime.JMeterPluginClasspath;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.PropertyIterator;

import java.io.File;
import java.util.*;
import java.util.regex.*;

public final class JMeterPlanDiagnostics {
    private static final Pattern TOKEN = Pattern.compile("\\$\\{([^}]+)}");

    private JMeterPlanDiagnostics() {
    }

    public static Report inspect(JMeterTreeModel model) {
        Report report = new Report();
        inspectEnvironment(report);
        if (model == null) {
            report.errors.add("No JMeter model loaded.");
            return report;
        }
        collect((JMeterTreeNode) model.getRoot(), report);
        return report;
    }

    private static void inspectEnvironment(Report report) {
        File home = JMeterPluginClasspath.jmeterHome();
        report.environment.add(home == null
                ? "Runtime: embedded JMeter"
                : "Runtime: " + home.getAbsolutePath());
        java.util.List<File> paths = JMeterPluginClasspath.paths();
        report.environment.add("Plugin/runtime paths: " + paths.size());
        for (File path : paths) {
            if (!path.exists()) {
                report.warnings.add("Configured JMeter path is missing: " + path.getAbsolutePath());
            }
        }
    }

    private static void collect(JMeterTreeNode node, Report report) {
        Object userObject = node.getUserObject();
        if (userObject instanceof TestElement) {
            inspectElement(node, (TestElement) userObject, report);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect((JMeterTreeNode) node.getChildAt(i), report);
        }
    }

    private static void inspectElement(JMeterTreeNode node, TestElement element, Report report) {
        report.elements++;
        if (node.isEnabled()) {
            report.enabled++;
        } else {
            report.disabled++;
            report.warnings.add(path(node) + " is disabled");
        }
        requireClass(node, element, TestElement.GUI_CLASS, report);
        requireClass(node, element, TestElement.TEST_CLASS, report);
        if (empty(element.getName())) {
            report.errors.add("Unnamed element at " + Arrays.toString(node.getPath()));
        }
        inspectProperties(node, element, report);
    }

    private static void inspectProperties(JMeterTreeNode node, TestElement element, Report report) {
        PropertyIterator iterator = element.propertyIterator();
        while (iterator.hasNext()) {
            org.apache.jmeter.testelement.property.JMeterProperty property = iterator.next();
            String value = property.getStringValue();
            if (empty(value)) {
                continue;
            }
            inspectTokens(node, property.getName(), value, report);
            inspectFileReference(node, property.getName(), value, report);
        }
    }

    private static void inspectTokens(JMeterTreeNode node, String property, String value, Report report) {
        Matcher matcher = TOKEN.matcher(value);
        while (matcher.find()) {
            String token = matcher.group(1).trim();
            if (token.isEmpty()) {
                report.warnings.add(path(node) + " has empty ${} token in " + property);
            } else if (token.startsWith("__")) {
                report.functions.add(token);
            } else {
                report.variables.add(token);
            }
        }
    }

    private static void inspectFileReference(JMeterTreeNode node, String property, String value, Report report) {
        String lower = property.toLowerCase(Locale.ROOT);
        if (!(lower.contains("file") || lower.contains("filename") || lower.contains("path"))) {
            return;
        }
        if (value.contains("${") || value.trim().isEmpty()) {
            return;
        }
        File file = new File(value);
        if (!file.isAbsolute() || file.exists()) {
            return;
        }
        report.warnings.add(path(node) + " references missing file in " + property + ": " + value);
    }

    private static void requireClass(JMeterTreeNode node, TestElement element, String property, Report report) {
        String className = element.getPropertyAsString(property);
        if (empty(className)) {
            report.errors.add(path(node) + " missing " + property);
            return;
        }
        try {
            JMeterPluginClasspath.loadClass(className);
        } catch (ClassNotFoundException exception) {
            report.errors.add(path(node) + " cannot load " + className);
        }
    }

    private static String path(JMeterTreeNode node) {
        return node.getName();
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class Report {
        private final java.util.List<String> errors = new ArrayList<>();
        private final java.util.List<String> warnings = new ArrayList<>();
        private final java.util.List<String> environment = new ArrayList<>();
        private final Set<String> variables = new TreeSet<>();
        private final Set<String> functions = new TreeSet<>();
        private int elements;
        private int enabled;
        private int disabled;

        public String format() {
            StringBuilder output = new StringBuilder();
            output.append(errors.isEmpty() ? "Validation OK" : "Validation issues").append('\n');
            output.append("Elements: ").append(elements)
                    .append(", enabled: ").append(enabled)
                    .append(", disabled: ").append(disabled).append('\n');
            append(output, "Environment", environment);
            append(output, "Errors", errors);
            append(output, "Warnings", warnings);
            append(output, "Variables", variables);
            append(output, "Functions", functions);
            return output.toString();
        }

        private void append(StringBuilder output, String title, Collection<String> values) {
            if (values.isEmpty()) {
                return;
            }
            output.append(title).append(':').append('\n');
            for (String value : values) {
                output.append("- ").append(value).append('\n');
            }
        }
    }
}

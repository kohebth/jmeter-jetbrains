package com.github.kohebth.jmeterviewer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class JMeterNode {
    private final String type;
    private final String name;
    private final List<JMeterProperty> properties;
    private final List<JMeterNode> children;

    public JMeterNode(String type, String name, List<JMeterProperty> properties, List<JMeterNode> children) {
        this.type = Objects.requireNonNull(type, "type");
        this.name = Objects.requireNonNull(name, "name");
        this.properties = Collections.unmodifiableList(new ArrayList<>(properties));
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
    }

    public String type() {
        return type;
    }

    public String name() {
        return name;
    }

    public List<JMeterProperty> properties() {
        return properties;
    }

    public List<JMeterNode> children() {
        return children;
    }

    @Override
    public String toString() {
        if (name.isBlank() || name.equals(type)) {
            return type;
        }
        return name + " (" + type + ")";
    }
}

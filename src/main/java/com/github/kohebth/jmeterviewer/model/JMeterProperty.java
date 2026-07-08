package com.github.kohebth.jmeterviewer.model;

public final class JMeterProperty {
    private final String name;
    private final String value;

    public JMeterProperty(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }
}

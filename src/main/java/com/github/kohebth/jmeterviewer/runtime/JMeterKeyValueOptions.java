package com.github.kohebth.jmeterviewer.runtime;

import java.util.*;

public final class JMeterKeyValueOptions {
    private JMeterKeyValueOptions() {
    }

    public static Map<String, String> parse(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return values;
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator < 0) {
                values.put(trimmed, "");
            } else {
                values.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
            }
        }
        values.remove("");
        return values;
    }
}

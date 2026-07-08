package com.github.kohebth.jmeterviewer.templates;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class JMeterNativeTemplateCatalogTest {
    @Test
    void loadsBundledJMeterTemplates() {
        java.util.List<JMeterTemplate> templates = JMeterNativeTemplateCatalog.templates();

        assertTrue(templates.stream().anyMatch(template -> template.name().contains("BeanShell Sampler")));
        assertTrue(templates.stream().anyMatch(template -> template.name().contains("Recording")));
        assertTrue(templates.stream().allMatch(JMeterTemplate::nativeTemplate));
    }
}

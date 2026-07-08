package com.github.kohebth.jmeterviewer.templates;

import com.github.kohebth.jmeterviewer.runtime.EmbeddedJMeterRuntime;
import com.github.kohebth.jmeterviewer.runtime.JMeterPluginClasspath;

import org.apache.jmeter.gui.action.template.Template;
import org.apache.jmeter.gui.action.template.TemplateManager;

import java.util.*;

public final class JMeterNativeTemplateCatalog {
    private JMeterNativeTemplateCatalog() {
    }

    public static java.util.List<JMeterTemplate> templates() {
        java.util.List<JMeterTemplate> templates = new ArrayList<>();
        try {
            EmbeddedJMeterRuntime.ensureReady();
            JMeterPluginClasspath.activate();
            TemplateManager manager = TemplateManager.getInstance().reset();
            for (String name : manager.getTemplateNames()) {
                Template template = manager.getTemplateByName(name);
                if (template != null) {
                    templates.add(JMeterTemplate.nativeTemplate(template));
                }
            }
        } catch (Exception ignored) {
        }
        return templates;
    }

    public static String description(Template template) {
        String description = template.getDescription();
        if (description == null || description.trim().isEmpty()) {
            return "Native JMeter template from " + template.getFileName();
        }
        return stripHtml(description).trim();
    }

    private static String stripHtml(String value) {
        return value.replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p>|</h\\d>|</li>", "\n")
                .replaceAll("(?is)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s+", "\n")
                .replaceAll("\\n{3,}", "\n\n");
    }
}

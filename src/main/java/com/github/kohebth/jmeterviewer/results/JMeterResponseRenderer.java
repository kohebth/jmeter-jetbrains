package com.github.kohebth.jmeterviewer.results;

import org.apache.jmeter.samplers.SampleResult;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;

public final class JMeterResponseRenderer {
    private JMeterResponseRenderer() {
    }

    public static String render(SampleResult result) {
        String contentType = safe(result.getContentType()).toLowerCase();
        String body = safe(result.getResponseDataAsString());
        if (body.trim().isEmpty()) {
            return "";
        }
        if (contentType.contains("json") || looksLikeJson(body)) {
            return "JSON Renderer\n\n" + prettyJson(body);
        }
        if (contentType.contains("xml") || looksLikeXml(body)) {
            return "XML Renderer\n\n" + prettyXml(body);
        }
        if (contentType.contains("html") || looksLikeHtml(body)) {
            return "HTML Renderer\n\n" + readableHtml(body);
        }
        return "Text Renderer\n\n" + body;
    }

    private static boolean looksLikeJson(String body) {
        String text = body.trim();
        return text.startsWith("{") || text.startsWith("[");
    }

    private static boolean looksLikeXml(String body) {
        return body.trim().startsWith("<?xml") || body.trim().startsWith("<");
    }

    private static boolean looksLikeHtml(String body) {
        String text = body.trim().toLowerCase();
        return text.startsWith("<!doctype html") || text.startsWith("<html") || text.contains("<body");
    }

    private static String prettyJson(String json) {
        StringBuilder builder = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaping) {
                builder.append(c);
                escaping = false;
                continue;
            }
            if (c == '\\' && inString) {
                builder.append(c);
                escaping = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                builder.append(c);
            } else if (!inString && (c == '{' || c == '[')) {
                builder.append(c).append('\n');
                appendIndent(builder, ++indent);
            } else if (!inString && (c == '}' || c == ']')) {
                builder.append('\n');
                appendIndent(builder, --indent);
                builder.append(c);
            } else if (!inString && c == ',') {
                builder.append(c).append('\n');
                appendIndent(builder, indent);
            } else if (!inString && c == ':') {
                builder.append(": ");
            } else if (inString || !Character.isWhitespace(c)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String prettyXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception exception) {
            return xml;
        }
    }

    private static String readableHtml(String html) {
        return html.replaceAll("(?i)>\\s*<", ">\n<");
    }

    private static void appendIndent(StringBuilder builder, int indent) {
        for (int i = 0; i < Math.max(0, indent); i++) {
            builder.append("  ");
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

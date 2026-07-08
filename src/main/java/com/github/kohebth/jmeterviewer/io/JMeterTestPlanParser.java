package com.github.kohebth.jmeterviewer.io;

import com.github.kohebth.jmeterviewer.model.JMeterNode;
import com.github.kohebth.jmeterviewer.model.JMeterParseException;
import com.github.kohebth.jmeterviewer.model.JMeterProperty;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JMeterTestPlanParser {
    public JMeterNode parse(String xml) throws JMeterParseException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);

            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();
            if (root == null) {
                return new JMeterNode("Empty JMX", "Empty JMX", List.of(), List.of());
            }
            return parseRoot(root);
        } catch (Exception exception) {
            throw new JMeterParseException("Unable to parse JMeter test plan", exception);
        }
    }

    private JMeterNode parseRoot(Element root) {
        List<JMeterNode> children = new ArrayList<>();
        NodeList childNodes = root.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element element = (Element) node;
            if (isHashTree(element)) {
                children.addAll(parseHashTree(element));
            } else if (isJMeterElement(element)) {
                children.add(parseElement(element, List.of()));
            }
        }
        return new JMeterNode(root.getTagName(), readableName(root), attributes(root), children);
    }

    private JMeterNode parseElement(Element element, List<JMeterNode> siblingHashTreeChildren) {
        List<JMeterProperty> properties = new ArrayList<>(attributes(element));
        List<JMeterNode> children = new ArrayList<>(siblingHashTreeChildren);

        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (!(child instanceof Element)) {
                continue;
            }

            Element childElement = (Element) child;
            if (isHashTree(childElement)) {
                children.addAll(parseHashTree(childElement));
            } else if (isPropertyElement(childElement)) {
                properties.add(propertyFrom(childElement));
            } else if (isJMeterElement(childElement)) {
                children.add(parseElement(childElement, List.of()));
            }
        }

        return new JMeterNode(element.getTagName(), readableName(element), properties, children);
    }

    private List<JMeterNode> parseHashTree(Element hashTree) {
        List<JMeterNode> children = new ArrayList<>();
        NodeList nodes = hashTree.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node child = nodes.item(i);
            if (!(child instanceof Element) || !isJMeterElement((Element) child)) {
                continue;
            }

            Element childElement = (Element) child;
            List<JMeterNode> nestedChildren = List.of();
            Element siblingHashTree = nextElementSibling(childElement);
            if (siblingHashTree != null && isHashTree(siblingHashTree)) {
                nestedChildren = parseHashTree(siblingHashTree);
            }
            children.add(parseElement(childElement, nestedChildren));
        }
        return children;
    }

    private static Element nextElementSibling(Element element) {
        Node sibling = element.getNextSibling();
        while (sibling != null) {
            if (sibling instanceof Element) {
                return (Element) sibling;
            }
            sibling = sibling.getNextSibling();
        }
        return null;
    }

    private static boolean isJMeterElement(Element element) {
        return !isHashTree(element) && !isPropertyElement(element);
    }

    private static boolean isHashTree(Element element) {
        return "hashTree".equals(element.getTagName());
    }

    private static boolean isPropertyElement(Element element) {
        String tag = element.getTagName().toLowerCase(Locale.ROOT);
        return tag.endsWith("prop") || tag.endsWith("property") || "elementProp".equals(element.getTagName());
    }

    private static JMeterProperty propertyFrom(Element element) {
        String name = attr(element, "name");
        if (name.isBlank()) {
            name = element.getTagName();
        }
        String value = attr(element, "value");
        if (value.isBlank()) {
            value = compactText(element);
        }
        return new JMeterProperty(name, value);
    }

    private static String readableName(Element element) {
        String explicitName = attr(element, "testname");
        if (!explicitName.isBlank()) {
            return explicitName;
        }
        String name = attr(element, "name");
        if (!name.isBlank()) {
            return name;
        }
        return element.getTagName();
    }

    private static List<JMeterProperty> attributes(Element element) {
        List<JMeterProperty> properties = new ArrayList<>();
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node item = attributes.item(i);
            properties.add(new JMeterProperty(item.getNodeName(), item.getNodeValue()));
        }
        return properties;
    }

    private static String attr(Element element, String name) {
        return element.hasAttribute(name) ? element.getAttribute(name) : "";
    }

    private static String compactText(Element element) {
        return element.getTextContent().replaceAll("\\s+", " ").trim();
    }
}

package com.github.kohebth.jmeterviewer.structure;

import com.github.kohebth.jmeterviewer.model.JMeterNode;

import java.util.ArrayList;
import java.util.List;

public final class JMeterStructureNode {
    private final JMeterNode node;
    private final int offset;
    private final List<JMeterStructureNode> children;

    private JMeterStructureNode(JMeterNode node, int offset, List<JMeterStructureNode> children) {
        this.node = node;
        this.offset = Math.max(offset, 0);
        this.children = children;
    }

    public static JMeterStructureNode from(JMeterNode root, String xml) {
        Cursor cursor = new Cursor();
        return from(root, xml, cursor);
    }

    public JMeterNode node() {
        return node;
    }

    public int offset() {
        return offset;
    }

    public List<JMeterStructureNode> children() {
        return children;
    }

    private static JMeterStructureNode from(JMeterNode node, String xml, Cursor cursor) {
        int offset = locate(node, xml, cursor);
        List<JMeterStructureNode> childNodes = new ArrayList<>();
        for (JMeterNode child : node.children()) {
            childNodes.add(from(child, xml, cursor));
        }
        return new JMeterStructureNode(node, offset, childNodes);
    }

    private static int locate(JMeterNode node, String xml, Cursor cursor) {
        int typeOffset = xml.indexOf("<" + node.type(), cursor.offset);
        if (typeOffset < 0) {
            typeOffset = xml.indexOf(node.type(), cursor.offset);
        }
        if (typeOffset >= 0) {
            cursor.offset = typeOffset + node.type().length();
            return typeOffset;
        }

        int nameOffset = xml.indexOf(node.name(), cursor.offset);
        if (nameOffset >= 0) {
            cursor.offset = nameOffset + node.name().length();
            return nameOffset;
        }
        return cursor.offset;
    }

    private static final class Cursor {
        private int offset;
    }
}

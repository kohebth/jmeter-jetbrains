package com.github.kohebth.jmeterviewer.ui;

import com.intellij.ide.highlighter.XmlFileType;

import javax.swing.ImageIcon;
import javax.swing.Icon;
import java.net.URL;

public final class JMeterIcons {
    public static final Icon FILE = icon();
    public static final Icon TOOL_WINDOW = XmlFileType.INSTANCE.getIcon();

    private JMeterIcons() {
    }

    private static Icon icon() {
        URL resource = JMeterIcons.class.getClassLoader().getResource("org/apache/jmeter/images/meter.png");
        return resource == null ? XmlFileType.INSTANCE.getIcon() : new ImageIcon(resource);
    }

}

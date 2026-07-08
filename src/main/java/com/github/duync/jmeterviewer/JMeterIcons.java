package com.github.duync.jmeterviewer;

import com.intellij.ide.highlighter.XmlFileType;

import javax.swing.ImageIcon;
import javax.swing.Icon;
import java.net.URL;

final class JMeterIcons {
    static final Icon FILE = icon();
    static final Icon TOOL_WINDOW = XmlFileType.INSTANCE.getIcon();

    private JMeterIcons() {
    }

    private static Icon icon() {
        URL resource = JMeterIcons.class.getClassLoader().getResource("org/apache/jmeter/images/meter.png");
        return resource == null ? XmlFileType.INSTANCE.getIcon() : new ImageIcon(resource);
    }

}

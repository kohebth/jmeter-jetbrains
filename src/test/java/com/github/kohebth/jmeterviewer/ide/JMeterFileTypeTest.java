package com.github.kohebth.jmeterviewer.ide;

import com.intellij.lang.xml.XMLLanguage;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class JMeterFileTypeTest {
    @Test
    void keepsJmxBackedByXmlLanguage() {
        assertEquals(XMLLanguage.INSTANCE, JMeterFileType.INSTANCE.getLanguage());
    }

    @Test
    void usesVisibleProjectTreeIcon() {
        Icon icon = JMeterFileType.INSTANCE.getIcon();

        assertNotNull(icon);
        assertEquals(19, icon.getIconWidth());
        assertEquals(19, icon.getIconHeight());
    }
}

package com.github.duync.jmeterviewer;

import javax.swing.*;
import java.awt.*;

final class JMeterTabOverflowSupport {
    private JMeterTabOverflowSupport() {
    }

    static <T extends Component> T apply(T component) {
        if (component instanceof JTabbedPane) {
            ((JTabbedPane) component).setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                apply(child);
            }
        }
        return component;
    }
}

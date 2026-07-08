package com.github.kohebth.jmeterviewer.ui;

import com.intellij.util.ui.JBUI;

import javax.swing.Icon;
import javax.swing.JButton;

public final class JMeterIconButtons {
    private JMeterIconButtons() {
    }

    public static JButton create(String tooltip, Icon icon, Runnable action) {
        JButton button = compact(new JButton(), tooltip, icon);
        button.addActionListener(event -> action.run());
        return button;
    }

    public static JButton compact(JButton button, String tooltip, Icon icon) {
        button.setText(null);
        button.setIcon(icon);
        button.setToolTipText(tooltip);
        button.setFocusable(true);
        button.setMargin(JBUI.emptyInsets());
        button.setOpaque(false);
        button.setRolloverEnabled(true);
        button.putClientProperty("JButton.buttonType", "toolbar");
        return button;
    }
}

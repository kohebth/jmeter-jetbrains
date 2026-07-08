package com.github.kohebth.jmeterviewer.palette;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.TransferHandler;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

public final class JMeterPaletteTransferHandler extends TransferHandler {
    @Override
    protected Transferable createTransferable(JComponent component) {
        if (!(component instanceof JList<?>)) {
            return null;
        }

        Object value = ((JList<?>) component).getSelectedValue();
        if (!(value instanceof JMeterPaletteItem)) {
            return null;
        }
        return new StringSelection(((JMeterPaletteItem) value).key());
    }

    @Override
    public int getSourceActions(JComponent component) {
        return COPY;
    }
}

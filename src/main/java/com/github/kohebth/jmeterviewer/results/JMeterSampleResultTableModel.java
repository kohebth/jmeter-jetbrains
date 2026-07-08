package com.github.kohebth.jmeterviewer.results;

import org.apache.jmeter.samplers.SampleResult;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public final class JMeterSampleResultTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Status", "Label", "Time", "Code", "Bytes", "Thread"};
    private final List<SampleResult> results = new ArrayList<>();

    public void clear() {
        int size = results.size();
        results.clear();
        if (size > 0) {
            fireTableRowsDeleted(0, size - 1);
        }
    }

    public void add(SampleResult result) {
        results.add(result);
        int row = results.size() - 1;
        fireTableRowsInserted(row, row);
    }

    public SampleResult get(int row) {
        if (row < 0 || row >= results.size()) {
            return null;
        }
        return results.get(row);
    }

    @Override
    public int getRowCount() {
        return results.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        SampleResult result = results.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return result.isSuccessful() ? "OK" : "FAIL";
            case 1:
                return result.getSampleLabel();
            case 2:
                return result.getTime();
            case 3:
                return result.getResponseCode();
            case 4:
                return result.getBytesAsLong();
            case 5:
                return result.getThreadName();
            default:
                return "";
        }
    }
}

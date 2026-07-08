package com.github.kohebth.jmeterviewer.results;

import org.apache.jmeter.samplers.SampleResult;

import javax.swing.table.AbstractTableModel;
import java.util.*;

public final class JMeterAggregateStatsModel extends AbstractTableModel {
    private static final String[] COLUMNS = {
            "Label", "Samples", "Failures", "Error %", "Average", "Min", "Max", "P95", "Throughput/s", "Bytes"
    };
    private final Map<String, Stats> statsByLabel = new LinkedHashMap<>();

    @Override
    public int getRowCount() {
        return statsByLabel.size();
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
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 1:
            case 2:
            case 4:
            case 5:
            case 6:
            case 7:
            case 9:
                return Number.class;
            default:
                return String.class;
        }
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Stats stats = row(rowIndex);
        if (stats == null) {
            return "";
        }
        switch (columnIndex) {
            case 0:
                return stats.label;
            case 1:
                return stats.samples;
            case 2:
                return stats.failures;
            case 3:
                return percent(stats.errorRate());
            case 4:
                return stats.average();
            case 5:
                return stats.min == Long.MAX_VALUE ? 0L : stats.min;
            case 6:
                return stats.max;
            case 7:
                return stats.percentile(95);
            case 8:
                return String.format(Locale.ROOT, "%.2f", stats.throughput());
            case 9:
                return stats.bytes;
            default:
                return "";
        }
    }

    public void add(SampleResult result) {
        String label = result.getSampleLabel() == null ? "" : result.getSampleLabel();
        int existingRow = rowIndex(label);
        statsByLabel.computeIfAbsent(label, Stats::new).add(result);
        if (existingRow >= 0) {
            fireTableRowsUpdated(existingRow, existingRow);
        } else {
            int newRow = statsByLabel.size() - 1;
            fireTableRowsInserted(newRow, newRow);
        }
    }

    public void clear() {
        statsByLabel.clear();
        fireTableDataChanged();
    }

    private Stats row(int index) {
        if (index < 0 || index >= statsByLabel.size()) {
            return null;
        }
        int i = 0;
        for (Stats stats : statsByLabel.values()) {
            if (i++ == index) {
                return stats;
            }
        }
        return null;
    }

    private int rowIndex(String label) {
        int row = 0;
        for (String key : statsByLabel.keySet()) {
            if (Objects.equals(key, label)) {
                return row;
            }
            row++;
        }
        return -1;
    }

    private String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0d);
    }

    private static final class Stats {
        private final String label;
        private final java.util.List<Long> times = new ArrayList<>();
        private int samples;
        private int failures;
        private long totalTime;
        private long min = Long.MAX_VALUE;
        private long max;
        private long bytes;
        private long firstStart = Long.MAX_VALUE;
        private long lastEnd;

        private Stats(String label) {
            this.label = label;
        }

        private void add(SampleResult result) {
            long time = result.getTime();
            samples++;
            failures += result.isSuccessful() ? 0 : 1;
            totalTime += time;
            min = Math.min(min, time);
            max = Math.max(max, time);
            bytes += result.getBytesAsLong();
            times.add(time);
            firstStart = Math.min(firstStart, result.getStartTime());
            lastEnd = Math.max(lastEnd, result.getEndTime());
        }

        private long average() {
            return samples == 0 ? 0L : Math.round((double) totalTime / samples);
        }

        private double errorRate() {
            return samples == 0 ? 0.0d : (double) failures / samples;
        }

        private long percentile(int percentile) {
            if (times.isEmpty()) {
                return 0L;
            }
            java.util.List<Long> sorted = new ArrayList<>(times);
            Collections.sort(sorted);
            int index = (int) Math.ceil(percentile / 100.0d * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }

        private double throughput() {
            if (firstStart == Long.MAX_VALUE || lastEnd <= firstStart) {
                return 0.0d;
            }
            return samples / ((lastEnd - firstStart) / 1000.0d);
        }
    }
}

package com.github.duync.jmeterviewer;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.AbstractThreadGroup;

import java.util.*;
import java.util.regex.Pattern;

final class JMeterThreadGroupActivity {
    private static final Pattern THREAD_SUFFIX = Pattern.compile("\\s+\\d+-\\d+$");
    private final Map<String, State> states = new LinkedHashMap<>();
    private Runnable changeListener = () -> { };

    void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener == null ? () -> { } : changeListener;
    }

    void prepare(JMeterTreeModel model) {
        states.clear();
        collect((JMeterTreeNode) model.getRoot());
        changed();
    }

    void start() {
        states.replaceAll((name, state) -> state == State.DISABLED ? State.DISABLED : State.WAITING);
        changed();
    }

    void sample(SampleResult result) {
        String group = groupName(result);
        if (group.isEmpty()) {
            return;
        }
        states.put(group, State.RUNNING);
        changed();
    }

    void status(String status) {
        if (isTerminal(status)) {
            states.replaceAll((name, state) -> state == State.DISABLED ? State.DISABLED : State.DONE);
            changed();
        }
    }

    void clear() {
        states.clear();
        changed();
    }

    String label(TestElement element) {
        if (!(element instanceof AbstractThreadGroup)) {
            return "";
        }
        State state = states.get(element.getName());
        if (state == null) {
            return "";
        }
        return state.label;
    }

    private void collect(JMeterTreeNode node) {
        Object value = node.getUserObject();
        TestElement element = value instanceof TestElement ? (TestElement) value : null;
        if (element instanceof AbstractThreadGroup) {
            states.put(element.getName(), node.isEnabled() ? State.WAITING : State.DISABLED);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect((JMeterTreeNode) node.getChildAt(i));
        }
    }

    private String groupName(SampleResult result) {
        if (result == null || result.getThreadName() == null) {
            return "";
        }
        return THREAD_SUFFIX.matcher(result.getThreadName().trim()).replaceFirst("");
    }

    private boolean isTerminal(String value) {
        if (value == null) {
            return false;
        }
        return value.startsWith("Finished")
                || value.startsWith("Run failed")
                || value.startsWith("Idle")
                || value.startsWith("Stopped");
    }

    private void changed() {
        changeListener.run();
    }

    private enum State {
        WAITING("[waiting]"),
        RUNNING("[running]"),
        DONE("[done]"),
        DISABLED("[disabled]");

        private final String label;

        State(String label) {
            this.label = label;
        }
    }
}

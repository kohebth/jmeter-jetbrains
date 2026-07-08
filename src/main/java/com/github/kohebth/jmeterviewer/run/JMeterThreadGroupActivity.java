package com.github.kohebth.jmeterviewer.run;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.AbstractThreadGroup;

import java.util.*;
import java.util.regex.Pattern;

public final class JMeterThreadGroupActivity {
    private static final Pattern THREAD_SUFFIX = Pattern.compile("\\s+\\d+-\\d+$");
    private final Map<String, State> states = new LinkedHashMap<>();
    private final Set<String> touchedGroups = new HashSet<>();
    private Runnable changeListener = () -> { };

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener == null ? () -> { } : changeListener;
    }

    public void prepare(JMeterTreeModel model) {
        states.clear();
        touchedGroups.clear();
        collect((JMeterTreeNode) model.getRoot());
        changed();
    }

    public void start() {
        touchedGroups.clear();
        states.replaceAll((name, state) -> state == State.DISABLED ? State.DISABLED : State.WAITING);
        changed();
    }

    public void startSelected(JMeterTreeNode selectedNode) {
        touchedGroups.clear();
        String selectedGroup = selectedThreadGroupName(selectedNode);
        states.replaceAll((name, state) -> selectedState(name, state, selectedGroup));
        changed();
    }

    public void sample(SampleResult result) {
        String group = groupName(result);
        if (group.isEmpty()) {
            return;
        }
        touchedGroups.add(group);
        states.put(group, State.RUNNING);
        changed();
    }

    public void status(String status) {
        if (isTerminal(status)) {
            states.replaceAll((name, state) -> terminalState(name, state));
            changed();
        }
    }

    public void clear() {
        states.clear();
        touchedGroups.clear();
        changed();
    }

    public String label(TestElement element) {
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
            states.put(element.getName(), node.isEnabled() ? State.IDLE : State.DISABLED);
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

    private State terminalState(String name, State state) {
        if (state == State.DISABLED) {
            return State.DISABLED;
        }
        return touchedGroups.contains(name) ? State.DONE : state;
    }

    private State selectedState(String name, State state, String selectedGroup) {
        if (state == State.DISABLED) {
            return State.DISABLED;
        }
        return name.equals(selectedGroup) ? State.WAITING : State.IDLE;
    }

    private String selectedThreadGroupName(JMeterTreeNode node) {
        JMeterTreeNode current = node;
        while (current != null) {
            Object value = current.getUserObject();
            if (value instanceof AbstractThreadGroup) {
                return ((AbstractThreadGroup) value).getName();
            }
            Object parent = current.getParent();
            current = parent instanceof JMeterTreeNode ? (JMeterTreeNode) parent : null;
        }
        return "";
    }

    private void changed() {
        changeListener.run();
    }

    private enum State {
        IDLE(""),
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

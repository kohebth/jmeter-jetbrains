package com.github.kohebth.jmeterviewer.templates;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@State(name = "JMeterTemplateStore", storages = @Storage("jmeterTemplates.xml"))
public final class JMeterTemplateStore implements PersistentStateComponent<JMeterTemplateStore.State> {
    private State state = new State();

    public static JMeterTemplateStore get(Project project) {
        return project.getService(JMeterTemplateStore.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public java.util.List<JMeterTemplate> templates() {
        java.util.List<JMeterTemplate> templates = new ArrayList<>();
        for (TemplateState template : state.templates) {
            templates.add(JMeterTemplate.custom(template.name, template.description, nodes(template.roots)));
        }
        return templates;
    }

    public void save(String name, String description, java.util.List<JMeterTemplate.Node> roots) {
        delete(name);
        TemplateState template = new TemplateState();
        template.name = name;
        template.description = description == null ? "" : description;
        template.roots = states(roots);
        state.templates.add(template);
    }

    public void delete(String name) {
        state.templates.removeIf(template -> Objects.equals(template.name, name));
    }

    private java.util.List<JMeterTemplate.Node> nodes(java.util.List<NodeState> states) {
        java.util.List<JMeterTemplate.Node> nodes = new ArrayList<>();
        for (NodeState state : states) {
            nodes.add(JMeterTemplate.node(state.element, nodes(state.children)));
        }
        return nodes;
    }

    private java.util.List<NodeState> states(java.util.List<JMeterTemplate.Node> nodes) {
        java.util.List<NodeState> states = new ArrayList<>();
        for (JMeterTemplate.Node node : nodes) {
            NodeState state = new NodeState();
            state.element = node.element();
            state.children = states(node.children());
            states.add(state);
        }
        return states;
    }

    public static final class State {
        public java.util.List<TemplateState> templates = new ArrayList<>();
    }

    public static final class TemplateState {
        public String name = "";
        public String description = "";
        public java.util.List<NodeState> roots = new ArrayList<>();
    }

    public static final class NodeState {
        public String element = "";
        public java.util.List<NodeState> children = new ArrayList<>();
    }
}

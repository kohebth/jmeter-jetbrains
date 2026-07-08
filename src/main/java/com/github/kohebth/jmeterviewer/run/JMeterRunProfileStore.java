package com.github.kohebth.jmeterviewer.run;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@State(name = "JMeterRunProfileStore", storages = @Storage("jmeterRunProfiles.xml"))
public final class JMeterRunProfileStore implements PersistentStateComponent<JMeterRunProfileStore.State> {
    private State state = new State();

    public static JMeterRunProfileStore get(Project project) {
        return project.getService(JMeterRunProfileStore.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public boolean hasProfile() {
        return !load().isEmpty();
    }

    public JMeterRunProfile load() {
        return new JMeterRunProfile(
                state.remoteHosts,
                state.resultFile,
                state.logLevel,
                new JMeterRunProfile.Advanced(
                        state.userPropertiesFile,
                        state.jmeterProperties,
                        state.systemProperties
                )
        );
    }

    public void save(JMeterRunProfile profile) {
        state.remoteHosts = profile.remoteHosts();
        state.resultFile = profile.resultFile();
        state.logLevel = profile.logLevel();
        state.userPropertiesFile = profile.advanced().userPropertiesFile();
        state.jmeterProperties = profile.advanced().jmeterProperties();
        state.systemProperties = profile.advanced().systemProperties();
    }

    public static final class State {
        public String remoteHosts = "";
        public String resultFile = "";
        public String logLevel = "";
        public String userPropertiesFile = "";
        public String jmeterProperties = "";
        public String systemProperties = "";
    }
}

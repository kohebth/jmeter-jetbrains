package com.github.duync.jmeterviewer;

import com.intellij.openapi.application.ApplicationManager;
import org.apache.jmeter.engine.*;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jorphan.collections.HashTree;

import javax.swing.SwingUtilities;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class JMeterRunController {
    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final java.util.List<JMeterEngine> engines = new ArrayList<>();
    private volatile JMeterRunLifecycle lifecycle;

    JMeterRunController(Listener listener) {
        this.listener = listener;
    }

    boolean isRunning() {
        return running.get();
    }

    void start(JMeterTreeModel model, JMeterRunOptions options) {
        start(model, options, RunTarget.AUTO);
    }

    void start(JMeterTreeModel model, JMeterRunOptions options, RunTarget target) {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            JMeterPluginClasspath.activate();
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage != null) {
                guiPackage.updateCurrentNode();
            }
            if (options != null) {
                options.apply();
            }

            HashTree testTree = JMeterTreeLoader.toRunHashTree(model);
            JMeterRunTreePreparer.prepare(testTree);
            engines.clear();
            engines.addAll(createEngines(options, target));
            if (engines.isEmpty()) {
                running.set(false);
                notifyStatus("Idle");
                SwingUtilities.invokeLater(() -> listener.log("No JMeter engines configured"));
                return;
            }
            lifecycle = new JMeterRunLifecycle(listener, running, engines.size());
            EditorResultCollector collector = new EditorResultCollector(listener, lifecycle);
            if (options != null && options.resultFile() != null) {
                collector.setFilename(options.resultFile());
            }
            testTree.add(collector);
            for (JMeterEngine engine : engines) {
                engine.configure(testTree);
            }
            notifyStatus("Starting " + (target == null ? RunTarget.AUTO : target).label());
            for (JMeterEngine engine : engines) {
                engine.runTest();
            }
            monitorEngines(lifecycle, new ArrayList<>(engines));
        } catch (Exception exception) {
            running.set(false);
            lifecycle = null;
            notifyStatus("Run failed: " + exception.getMessage());
            SwingUtilities.invokeLater(() -> listener.log("Run failed: " + exception));
        }
    }

    void stop() {
        if (running.get()) {
            stopAll(true, "Stopping");
            JMeterRunLifecycle current = lifecycle;
            if (current != null) {
                current.forceFinish("Stopped", "Stop requested");
            }
        }
    }

    void shutdown() {
        if (!running.get()) {
            return;
        }
        notifyStatus("Shutting down");
        for (JMeterEngine engine : engines) {
            if (engine instanceof StandardJMeterEngine) {
                ((StandardJMeterEngine) engine).askThreadsToStop();
            } else {
                engine.stopTest(false);
            }
        }
    }

    void stopThread(String threadName, boolean now) {
        if (threadName == null || threadName.trim().isEmpty()) {
            return;
        }
        boolean stopped = now
                ? StandardJMeterEngine.stopThreadNow(threadName.trim())
                : StandardJMeterEngine.stopThread(threadName.trim());
        SwingUtilities.invokeLater(() -> listener.log((stopped ? "Stopped " : "No thread named ") + threadName));
    }

    void resetEngines() {
        for (JMeterEngine engine : engines) {
            engine.reset();
        }
        SwingUtilities.invokeLater(() -> listener.log("Reset JMeter engines"));
    }

    void exitEngines() {
        for (JMeterEngine engine : engines) {
            engine.exit();
        }
        engines.clear();
        running.set(false);
        lifecycle = null;
        notifyStatus("Idle");
        SwingUtilities.invokeLater(() -> listener.log("Exited JMeter engines"));
    }

    private java.util.List<JMeterEngine> createEngines(JMeterRunOptions options, RunTarget target) throws Exception {
        java.util.List<String> hosts = options == null ? Collections.emptyList() : options.remoteHosts();
        RunTarget resolved = target == null ? RunTarget.AUTO : target;
        java.util.List<JMeterEngine> selected = new ArrayList<>();
        if (resolved.includesLocal(hosts)) {
            selected.add(new StandardJMeterEngine());
            SwingUtilities.invokeLater(() -> listener.log("Configured local engine"));
        }
        if (resolved.includesRemote(hosts)) {
            for (String host : hosts) {
                selected.add(new ClientJMeterEngine(host));
                SwingUtilities.invokeLater(() -> listener.log("Configured remote engine " + host));
            }
        } else if (resolved.requiresRemote()) {
            SwingUtilities.invokeLater(() -> listener.log("No remote hosts configured"));
        }
        return selected;
    }

    private void stopAll(boolean now, String status) {
        notifyStatus(status);
        for (JMeterEngine engine : engines) {
            try {
                engine.stopTest(now);
            } catch (RuntimeException exception) {
                SwingUtilities.invokeLater(() -> listener.log("Unable to stop engine: " + exception.getMessage()));
            }
        }
    }

    private void monitorEngines(JMeterRunLifecycle lifecycle, java.util.List<JMeterEngine> monitoredEngines) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            long started = System.currentTimeMillis();
            boolean sawActive = false;
            while (running.get() && lifecycle == this.lifecycle) {
                boolean anyActive = false;
                for (JMeterEngine engine : monitoredEngines) {
                    try {
                        anyActive |= engine.isActive();
                    } catch (RuntimeException ignored) {
                    }
                }
                sawActive |= anyActive;
                if (!anyActive && (sawActive || System.currentTimeMillis() - started > 1000L)) {
                    lifecycle.forceFinish("Finished", "All JMeter engines are idle");
                    return;
                }
                sleep();
            }
        });
    }

    private void sleep() {
        try {
            Thread.sleep(250L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void notifyStatus(String status) {
        SwingUtilities.invokeLater(() -> listener.statusChanged(status));
    }

    interface Listener {
        void statusChanged(String status);

        void log(String message);

        void sampleOccurred(SampleResult result);
    }

    enum RunTarget {
        AUTO("test"),
        LOCAL("local test"),
        REMOTE("remote test"),
        LOCAL_AND_REMOTE("local and remote test");

        private final String label;

        RunTarget(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        private boolean includesLocal(java.util.List<String> hosts) {
            return this == LOCAL || this == LOCAL_AND_REMOTE || (this == AUTO && hosts.isEmpty());
        }

        private boolean includesRemote(java.util.List<String> hosts) {
            return !hosts.isEmpty() && (this == REMOTE || this == LOCAL_AND_REMOTE || this == AUTO);
        }

        private boolean requiresRemote() {
            return this == REMOTE || this == LOCAL_AND_REMOTE;
        }
    }

    private static final class EditorResultCollector extends ResultCollector {
        private final Listener listener;
        private final JMeterRunLifecycle lifecycle;

        private EditorResultCollector(Listener listener, JMeterRunLifecycle lifecycle) {
            this.listener = listener;
            this.lifecycle = lifecycle;
        }

        @Override
        public void testStarted() {
            lifecycle.started(null);
        }

        @Override
        public void testStarted(String host) {
            lifecycle.started(host);
        }

        @Override
        public void testEnded() {
            lifecycle.ended(null);
        }

        @Override
        public void testEnded(String host) {
            lifecycle.ended(host);
        }

        @Override
        public void sampleOccurred(SampleEvent event) {
            SampleResult result = event.getResult();
            SwingUtilities.invokeLater(() -> listener.sampleOccurred(result));
        }
    }
}

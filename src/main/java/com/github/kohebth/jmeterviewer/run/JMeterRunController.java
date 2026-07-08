package com.github.kohebth.jmeterviewer.run;

import com.github.kohebth.jmeterviewer.editor.JMeterGuiPackageScope;
import com.github.kohebth.jmeterviewer.ide.JMeterActionTrace;
import com.github.kohebth.jmeterviewer.runtime.JMeterPluginClasspath;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Application;
import org.apache.jmeter.engine.*;
import org.apache.jmeter.engine.util.NoThreadClone;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jorphan.collections.HashTree;

import javax.swing.SwingUtilities;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JMeterRunController {
    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final java.util.List<JMeterEngine> engines = new ArrayList<>();
    private volatile JMeterRunLifecycle lifecycle;
    private volatile JMeterGuiPackageScope guiPackageScope;

    public JMeterRunController(Listener listener) {
        this.listener = listener;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void start(JMeterTreeModel model, JMeterRunOptions options) {
        start(model, options, RunTarget.AUTO);
    }

    public void start(JMeterTreeModel model, JMeterRunOptions options, RunTarget target) {
        start(JMeterRunTreeBuilder.fullPlan(model), options, target);
    }

    public void startSelectedThreadGroup(JMeterTreeModel model,
                                  JMeterTreeNode selected,
                                  JMeterRunOptions options,
                                  RunTarget target) {
        start(JMeterRunTreeBuilder.selectedThreadGroup(model, selected), options, target);
    }

    private void start(HashTree testTree, JMeterRunOptions options, RunTarget target) {
        if (!running.compareAndSet(false, true)) {
            JMeterActionTrace.info("run.start.ignored", "reason=already-running");
            return;
        }

        try {
            JMeterActionTrace.info("run.start.request", "target=" + (target == null ? RunTarget.AUTO : target));
            JMeterPluginClasspath.activate();
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage != null) {
                guiPackage.updateCurrentNode();
            }
            if (options != null) {
                options.apply();
            }

            JMeterRunTreePreparer.prepare(testTree);
            engines.clear();
            engines.addAll(createEngines(options, target));
            if (engines.isEmpty()) {
                running.set(false);
                notifyStatus("Idle");
                JMeterActionTrace.info("run.start.no-engines");
                SwingUtilities.invokeLater(() -> listener.log("No JMeter engines configured"));
                return;
            }
            lifecycle = new JMeterRunLifecycle(listener, running, engines.size());
            EditorRunListener collector = new EditorRunListener(listener, lifecycle);
            JMeterRunListenerAttacher.attach(testTree, collector);
            if (options != null && options.resultFile() != null) {
                ResultCollector fileCollector = new ResultCollector();
                fileCollector.setFilename(options.resultFile());
                JMeterRunListenerAttacher.attach(testTree, fileCollector);
            }
            for (JMeterEngine engine : engines) {
                engine.configure(testTree);
            }
            notifyStatus("Starting " + (target == null ? RunTarget.AUTO : target).label());
            guiPackageScope = JMeterGuiPackageScope.detach();
            for (JMeterEngine engine : engines) {
                engine.runTest();
            }
            JMeterActionTrace.info("run.start.dispatched", "engines=" + engines.size());
            monitorEngines(lifecycle, new ArrayList<>(engines));
        } catch (Exception exception) {
            JMeterActionTrace.warn("run.start.failed", exception);
            restoreGuiPackage();
            running.set(false);
            lifecycle = null;
            notifyStatus("Run failed: " + exception.getMessage());
            SwingUtilities.invokeLater(() -> listener.log("Run failed: " + exception));
        }
    }

    public void stop() {
        JMeterActionTrace.info("run.stop.request", "running=" + running.get());
        if (running.get()) {
            stopAll(true, "Stopping");
            JMeterRunLifecycle current = lifecycle;
            if (current != null) {
                restoreGuiPackage();
                current.forceFinish("Stopped", "Stop requested");
            }
        }
    }

    public void shutdown() {
        JMeterActionTrace.info("run.shutdown.request", "running=" + running.get());
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

    public void stopThread(String threadName, boolean now) {
        JMeterActionTrace.info("run.thread.stop.request", "thread=\"" + threadName + "\" now=" + now);
        if (threadName == null || threadName.trim().isEmpty()) {
            return;
        }
        boolean stopped = now
                ? StandardJMeterEngine.stopThreadNow(threadName.trim())
                : StandardJMeterEngine.stopThread(threadName.trim());
        SwingUtilities.invokeLater(() -> listener.log((stopped ? "Stopped " : "No thread named ") + threadName));
    }

    public void resetEngines() {
        JMeterActionTrace.info("run.engines.reset", "engines=" + engines.size());
        for (JMeterEngine engine : engines) {
            engine.reset();
        }
        SwingUtilities.invokeLater(() -> listener.log("Reset JMeter engines"));
    }

    public void exitEngines() {
        JMeterActionTrace.info("run.engines.exit", "engines=" + engines.size());
        for (JMeterEngine engine : engines) {
            engine.exit();
        }
        engines.clear();
        running.set(false);
        lifecycle = null;
        restoreGuiPackage();
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
        Runnable monitor = () -> {
            long started = System.currentTimeMillis();
            boolean sawActive = false;
            while (running.get() && lifecycle == this.lifecycle) {
                boolean anyActive = false;
                for (JMeterEngine engine : monitoredEngines) {
                    String failure = completedFailure(engine);
                    if (failure != null) {
                        JMeterActionTrace.info("run.monitor.failed", failure);
                        restoreGuiPackage();
                        lifecycle.forceFinish("Run failed", failure);
                        return;
                    }
                    try {
                        anyActive |= engine.isActive();
                    } catch (RuntimeException ignored) {
                    }
                }
                sawActive |= anyActive;
                if (!anyActive && (sawActive || System.currentTimeMillis() - started > 1000L)) {
                    JMeterActionTrace.info("run.monitor.finished",
                            "sawActive=" + sawActive + " elapsedMs=" + (System.currentTimeMillis() - started));
                    restoreGuiPackage();
                    lifecycle.forceFinish("Finished", "All JMeter engines are idle");
                    return;
                }
                sleep();
            }
            restoreGuiPackage();
        };
        Application application = ApplicationManager.getApplication();
        if (application != null) {
            application.executeOnPooledThread(monitor);
            return;
        }
        Thread thread = new Thread(monitor, "JMeter run monitor");
        thread.setDaemon(true);
        thread.start();
    }

    private String completedFailure(JMeterEngine engine) {
        if (!(engine instanceof StandardJMeterEngine)) {
            return null;
        }
        try {
            ((StandardJMeterEngine) engine).awaitTermination(Duration.ofMillis(1));
            return null;
        } catch (TimeoutException ignored) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "Run monitor interrupted";
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return "Run failed: " + cause.getMessage();
        }
    }

    private void restoreGuiPackage() {
        JMeterGuiPackageScope scope = guiPackageScope;
        if (scope != null) {
            guiPackageScope = null;
            scope.close();
        }
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

    public interface Listener {
        public void statusChanged(String status);

        public void log(String message);

        public void sampleOccurred(SampleResult result);
    }

    public enum RunTarget {
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

    private static final class EditorRunListener extends AbstractTestElement
            implements SampleListener, TestStateListener, NoThreadClone {
        private final Listener listener;
        private final JMeterRunLifecycle lifecycle;

        private EditorRunListener(Listener listener, JMeterRunLifecycle lifecycle) {
            this.listener = listener;
            this.lifecycle = lifecycle;
            setName("IDE Results");
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

        @Override
        public void sampleStarted(SampleEvent event) {
        }

        @Override
        public void sampleStopped(SampleEvent event) {
        }
    }
}

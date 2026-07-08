package com.github.kohebth.jmeterviewer.run;

import javax.swing.SwingUtilities;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class JMeterRunLifecycle {
    private final JMeterRunController.Listener listener;
    private final AtomicBoolean running;
    private final AtomicInteger remaining;
    private final Set<String> endedEngines = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean finished = new AtomicBoolean(false);

    public JMeterRunLifecycle(JMeterRunController.Listener listener, AtomicBoolean running, int engines) {
        this.listener = listener;
        this.running = running;
        this.remaining = new AtomicInteger(engines);
    }

    public void started(String host) {
        SwingUtilities.invokeLater(() -> {
            listener.statusChanged("Running");
            listener.log("Test started" + suffix(host));
        });
    }

    public void ended(String host) {
        if (!endedEngines.add(hostKey(host))) {
            return;
        }
        int left = remaining.updateAndGet(value -> Math.max(0, value - 1));
        if (left == 0) {
            finish("Finished", "Test finished" + suffix(host));
            return;
        }
        SwingUtilities.invokeLater(() -> {
            listener.log("Test finished" + suffix(host));
            listener.statusChanged("Running (" + left + " engines)");
        });
    }

    public void forceFinish(String status, String log) {
        finish(status, log);
    }

    private void finish(String status, String log) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        running.set(false);
        SwingUtilities.invokeLater(() -> {
            if (log != null && !log.isEmpty()) {
                listener.log(log);
            }
            listener.statusChanged(status);
        });
    }

    private String suffix(String host) {
        return host == null || host.trim().isEmpty() ? "" : " on " + host;
    }

    private String hostKey(String host) {
        return host == null || host.trim().isEmpty() ? "local" : host.trim();
    }
}

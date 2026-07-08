package com.github.kohebth.jmeterviewer.run;

import com.github.kohebth.jmeterviewer.io.JMeterTreeLoader;
import com.github.kohebth.jmeterviewer.runtime.EmbeddedJMeterRuntime;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.engine.util.NoThreadClone;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeListener;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jorphan.collections.SearchByClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.jorphan.collections.HashTree;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JMeterRunControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void singleThreadGroupWithOneLoopFinishesAfterOneSample() throws Exception {
        EmbeddedJMeterRuntime.ensureReady();
        RecordingListener listener = new RecordingListener();
        JMeterRunController controller = new JMeterRunController(listener);

        try {
            controller.start(singleLoopModel(), null, JMeterRunController.RunTarget.LOCAL);

            assertTrue(listener.sampled.await(10, TimeUnit.SECONDS),
                    "Expected one Debug Sampler result; " + listener.diagnostics());
            assertTrue(listener.finished.await(10, TimeUnit.SECONDS),
                    "Expected finite loop run to finish; " + listener.diagnostics());
            assertEquals(1, listener.samples.get());
            assertFalse(controller.isRunning());
        } finally {
            controller.stop();
            controller.exitEngines();
        }
    }

    @Test
    void sameLoadedTreeProducesOneSampleWithStandardEngine() throws Exception {
        EmbeddedJMeterRuntime.ensureReady();
        HashTree tree = JMeterTreeLoader.toRunHashTree(singleLoopModel());
        JMeterRunTreePreparer.prepare(tree);
        assertLoadedThreadGroupHasOneThread(tree);
        RecordingCollector collector = new RecordingCollector();
        JMeterRunListenerAttacher.attach(tree, collector);
        StandardJMeterEngine engine = new StandardJMeterEngine();

        try {
            engine.configure(tree);
            engine.runTest();

            engine.awaitTermination(java.time.Duration.ofSeconds(10));
            assertTrue(collector.sampled.await(10, TimeUnit.SECONDS),
                    "Expected direct engine sample, samples=" + collector.samples.get());
            assertEquals(1, collector.samples.get());
        } finally {
            engine.stopTest(true);
        }
    }

    @Test
    void selectedThreadGroupWithOneLoopRunsOnlySelectedGroup() throws Exception {
        EmbeddedJMeterRuntime.ensureReady();
        RecordingListener listener = new RecordingListener();
        JMeterRunController controller = new JMeterRunController(listener);
        JMeterTreeModel model = twoGroupModel();

        try {
            controller.startSelectedThreadGroup(model, threadGroup(model, "Second Group"), null,
                    JMeterRunController.RunTarget.LOCAL);

            assertTrue(listener.finished.await(10, TimeUnit.SECONDS),
                    "Expected selected group run to finish; " + listener.diagnostics());
            assertEquals(1, listener.samples.get());
            assertTrue(listener.threadNames.stream().allMatch(name -> name.startsWith("Second Group ")),
                    "Expected only Second Group samples, got " + listener.threadNames);
            assertFalse(controller.isRunning());
        } finally {
            controller.stop();
            controller.exitEngines();
        }
    }

    @Test
    void localRunFinishesWhenEmbeddedGuiPackageHasNoMainFrame() throws Exception {
        EmbeddedJMeterRuntime.ensureReady();
        RecordingListener listener = new RecordingListener();
        JMeterRunController controller = new JMeterRunController(listener);
        JMeterTreeModel model = singleLoopModel();
        GuiPackage.initInstance(new JMeterTreeListener(model), model);

        try {
            controller.start(model, null, JMeterRunController.RunTarget.LOCAL);

            assertTrue(listener.finished.await(10, TimeUnit.SECONDS),
                    "Expected run to finish without touching JMeter MainFrame; " + listener.diagnostics());
            assertEquals(1, listener.samples.get());
            assertFalse(controller.isRunning());
        } finally {
            controller.stop();
            controller.exitEngines();
        }
    }

    private JMeterTreeModel singleLoopModel() {
        try {
            return JMeterTreeLoader.load(writeSingleLoopJmx().toFile());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private JMeterTreeModel twoGroupModel() {
        try {
            Path jmx = tempDir.resolve("two-groups-" + System.nanoTime() + ".jmx");
            Files.write(jmx, twoGroupJmx().getBytes(StandardCharsets.UTF_8));
            return JMeterTreeLoader.load(jmx.toFile());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private JMeterTreeNode threadGroup(JMeterTreeModel model, String name) {
        JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
        return findThreadGroup(root, name);
    }

    private JMeterTreeNode findThreadGroup(JMeterTreeNode node, String name) {
        if (node.getUserObject() instanceof ThreadGroup && name.equals(node.getName())) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            JMeterTreeNode found = findThreadGroup((JMeterTreeNode) node.getChildAt(i), name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private Path writeSingleLoopJmx() throws java.io.IOException {
        Path jmx = tempDir.resolve("single-loop-" + System.nanoTime() + ".jmx");
        Files.write(jmx, singleLoopJmx().getBytes(StandardCharsets.UTF_8));
        return jmx;
    }

    private void assertLoadedThreadGroupHasOneThread(HashTree tree) {
        SearchByClass<ThreadGroup> search = new SearchByClass<>(ThreadGroup.class);
        tree.traverse(search);
        assertEquals(1, search.getSearchResults().size(), "Expected exactly one ThreadGroup");
        ThreadGroup group = search.getSearchResults().iterator().next();
        assertEquals(1, group.getNumThreads(), "Expected one configured thread");
    }

    private String singleLoopJmx() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.6.3\">\n"
                + "  <org.apache.jorphan.collections.HashTree>\n"
                + "    <TestPlan guiclass=\"TestPlanGui\" testclass=\"TestPlan\" testname=\"Plan\" enabled=\"true\">\n"
                + "      <stringProp name=\"TestPlan.comments\"></stringProp>\n"
                + "      <boolProp name=\"TestPlan.functional_mode\">false</boolProp>\n"
                + "      <boolProp name=\"TestPlan.tearDown_on_shutdown\">true</boolProp>\n"
                + "      <boolProp name=\"TestPlan.serialize_threadgroups\">false</boolProp>\n"
                + "      <elementProp name=\"TestPlan.user_defined_variables\" elementType=\"Arguments\" "
                + "guiclass=\"ArgumentsPanel\" testclass=\"Arguments\" testname=\"User Defined Variables\" enabled=\"true\">\n"
                + "        <collectionProp name=\"Arguments.arguments\"/>\n"
                + "      </elementProp>\n"
                + "    </TestPlan>\n"
                + "    <org.apache.jorphan.collections.HashTree>\n"
                + "      <ThreadGroup guiclass=\"ThreadGroupGui\" testclass=\"ThreadGroup\" "
                + "testname=\"Single Loop Group\" enabled=\"true\">\n"
                + "        <stringProp name=\"ThreadGroup.on_sample_error\">continue</stringProp>\n"
                + "        <elementProp name=\"ThreadGroup.main_controller\" elementType=\"LoopController\" "
                + "guiclass=\"LoopControlPanel\" testclass=\"LoopController\" testname=\"Loop Controller\" enabled=\"true\">\n"
                + "          <boolProp name=\"LoopController.continue_forever\">false</boolProp>\n"
                + "          <stringProp name=\"LoopController.loops\">1</stringProp>\n"
                + "        </elementProp>\n"
                + "        <stringProp name=\"ThreadGroup.num_threads\">1</stringProp>\n"
                + "        <stringProp name=\"ThreadGroup.ramp_time\">1</stringProp>\n"
                + "        <boolProp name=\"ThreadGroup.scheduler\">false</boolProp>\n"
                + "      </ThreadGroup>\n"
                + "      <org.apache.jorphan.collections.HashTree>\n"
                + "        <DebugSampler guiclass=\"TestBeanGUI\" testclass=\"org.apache.jmeter.sampler.DebugSampler\" "
                + "testname=\"Debug Sampler\" enabled=\"true\"/>\n"
                + "        <org.apache.jorphan.collections.HashTree/>\n"
                + "      </org.apache.jorphan.collections.HashTree>\n"
                + "    </org.apache.jorphan.collections.HashTree>\n"
                + "  </org.apache.jorphan.collections.HashTree>\n"
                + "</jmeterTestPlan>\n";
    }

    private String twoGroupJmx() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.6.3\">\n"
                + "  <hashTree>\n"
                + "    <TestPlan guiclass=\"TestPlanGui\" testclass=\"TestPlan\" testname=\"Plan\" enabled=\"true\">\n"
                + "      <boolProp name=\"TestPlan.functional_mode\">false</boolProp>\n"
                + "      <boolProp name=\"TestPlan.tearDown_on_shutdown\">true</boolProp>\n"
                + "      <boolProp name=\"TestPlan.serialize_threadgroups\">false</boolProp>\n"
                + "    </TestPlan>\n"
                + "    <hashTree>\n"
                + threadGroupJmx("First Group")
                + threadGroupJmx("Second Group")
                + "    </hashTree>\n"
                + "  </hashTree>\n"
                + "</jmeterTestPlan>\n";
    }

    private String threadGroupJmx(String name) {
        return "      <ThreadGroup guiclass=\"ThreadGroupGui\" testclass=\"ThreadGroup\" "
                + "testname=\"" + name + "\" enabled=\"true\">\n"
                + "        <stringProp name=\"ThreadGroup.on_sample_error\">continue</stringProp>\n"
                + "        <elementProp name=\"ThreadGroup.main_controller\" elementType=\"LoopController\" "
                + "guiclass=\"LoopControlPanel\" testclass=\"LoopController\" testname=\"Loop Controller\" enabled=\"true\">\n"
                + "          <boolProp name=\"LoopController.continue_forever\">false</boolProp>\n"
                + "          <stringProp name=\"LoopController.loops\">1</stringProp>\n"
                + "        </elementProp>\n"
                + "        <stringProp name=\"ThreadGroup.num_threads\">1</stringProp>\n"
                + "        <stringProp name=\"ThreadGroup.ramp_time\">1</stringProp>\n"
                + "        <boolProp name=\"ThreadGroup.scheduler\">false</boolProp>\n"
                + "      </ThreadGroup>\n"
                + "      <hashTree>\n"
                + "        <DebugSampler guiclass=\"TestBeanGUI\" testclass=\"org.apache.jmeter.sampler.DebugSampler\" "
                + "testname=\"" + name + " Debug\" enabled=\"true\"/>\n"
                + "        <hashTree/>\n"
                + "      </hashTree>\n";
    }

    private static final class RecordingListener implements JMeterRunController.Listener {
        private final CountDownLatch sampled = new CountDownLatch(1);
        private final CountDownLatch finished = new CountDownLatch(1);
        private final AtomicInteger samples = new AtomicInteger();
        private final java.util.List<String> statuses = new java.util.ArrayList<>();
        private final java.util.List<String> logs = new java.util.ArrayList<>();
        private final java.util.List<String> threadNames = new java.util.ArrayList<>();

        @Override
        public void statusChanged(String status) {
            statuses.add(status);
            if (status != null && status.startsWith("Finished")) {
                finished.countDown();
            }
        }

        @Override
        public void log(String message) {
            logs.add(message);
        }

        @Override
        public void sampleOccurred(SampleResult result) {
            samples.incrementAndGet();
            threadNames.add(result.getThreadName());
            sampled.countDown();
        }

        private String diagnostics() {
            return "statuses=" + statuses + ", logs=" + logs + ", samples=" + samples.get();
        }
    }

    private static final class RecordingCollector extends AbstractTestElement implements SampleListener, NoThreadClone {
        private final CountDownLatch sampled = new CountDownLatch(1);
        private final AtomicInteger samples = new AtomicInteger();

        @Override
        public void sampleOccurred(SampleEvent event) {
            samples.incrementAndGet();
            sampled.countDown();
        }

        @Override
        public void sampleStarted(SampleEvent event) {
        }

        @Override
        public void sampleStopped(SampleEvent event) {
        }
    }
}

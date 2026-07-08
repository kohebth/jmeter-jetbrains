package com.github.kohebth.jmeterviewer.io;

import com.github.kohebth.jmeterviewer.model.JMeterNode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class JMeterTestPlanParserTest {
    @Test
    void parsesJMeterHashTreeHierarchy() throws Exception {
        String xml = String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.6.3\">",
                "  <hashTree>",
                "    <TestPlan guiclass=\"TestPlanGui\" testclass=\"TestPlan\" testname=\"Smoke Plan\" enabled=\"true\">",
                "      <stringProp name=\"TestPlan.comments\">Basic smoke test</stringProp>",
                "    </TestPlan>",
                "    <hashTree>",
                "      <ThreadGroup guiclass=\"ThreadGroupGui\" testclass=\"ThreadGroup\" testname=\"Users\" enabled=\"true\">",
                "        <stringProp name=\"ThreadGroup.num_threads\">10</stringProp>",
                "      </ThreadGroup>",
                "      <hashTree>",
                "        <HTTPSamplerProxy guiclass=\"HttpTestSampleGui\" testclass=\"HTTPSamplerProxy\" testname=\"GET /health\" enabled=\"true\"/>",
                "        <hashTree/>",
                "      </hashTree>",
                "    </hashTree>",
                "  </hashTree>",
                "</jmeterTestPlan>");

        JMeterNode root = new JMeterTestPlanParser().parse(xml);

        assertEquals("jmeterTestPlan", root.type());
        assertEquals(1, root.children().size());

        JMeterNode testPlan = root.children().get(0);
        assertEquals("Smoke Plan", testPlan.name());
        assertFalse(testPlan.properties().isEmpty());
        assertEquals(1, testPlan.children().size());

        JMeterNode threadGroup = testPlan.children().get(0);
        assertEquals("Users", threadGroup.name());
        assertEquals("GET /health", threadGroup.children().get(0).name());
    }
}

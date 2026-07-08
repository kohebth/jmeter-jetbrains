package com.github.kohebth.jmeterviewer.palette;

import com.github.kohebth.jmeterviewer.runtime.JMeterPluginClasspath;

import org.apache.jmeter.gui.JMeterGUIComponent;
import org.apache.jmeter.testelement.TestElement;

import java.util.List;

public final class JMeterPaletteItem {
    static final List<JMeterPaletteItem> DEFAULT_ITEMS = List.of(
            new JMeterPaletteItem("Thread Group", Kind.THREAD_GROUP, "org.apache.jmeter.threads.gui.ThreadGroupGui", "org.apache.jmeter.threads.ThreadGroup"),
            new JMeterPaletteItem("setUp Thread Group", Kind.THREAD_GROUP, "org.apache.jmeter.threads.gui.SetupThreadGroupGui", "org.apache.jmeter.threads.SetupThreadGroup"),
            new JMeterPaletteItem("tearDown Thread Group", Kind.THREAD_GROUP, "org.apache.jmeter.threads.gui.PostThreadGroupGui", "org.apache.jmeter.threads.PostThreadGroup"),
            new JMeterPaletteItem("Open Model Thread Group", Kind.THREAD_GROUP, "org.apache.jmeter.threads.openmodel.gui.OpenModelThreadGroupGui", "org.apache.jmeter.threads.openmodel.OpenModelThreadGroup"),
            new JMeterPaletteItem("Test Fragment", Kind.TEST_FRAGMENT, "org.apache.jmeter.control.gui.TestFragmentControllerGui", "org.apache.jmeter.control.TestFragmentController"),

            new JMeterPaletteItem("HTTP Request", Kind.SAMPLER, "org.apache.jmeter.protocol.http.control.gui.HttpTestSampleGui", "org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy"),
            new JMeterPaletteItem("GraphQL HTTP Request", Kind.SAMPLER, "org.apache.jmeter.protocol.http.control.gui.GraphQLHTTPSamplerGui", "org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy"),
            new JMeterPaletteItem("AJP/1.3 Sampler", Kind.SAMPLER, "org.apache.jmeter.protocol.http.control.gui.AjpSamplerGui", "org.apache.jmeter.protocol.http.sampler.AjpSampler"),
            new JMeterPaletteItem("Access Log Sampler", Kind.SAMPLER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.protocol.http.sampler.AccessLogSampler"),
            new JMeterPaletteItem("Java Request", Kind.SAMPLER, "org.apache.jmeter.protocol.java.control.gui.JavaTestSamplerGui", "org.apache.jmeter.protocol.java.sampler.JavaSampler"),
            new JMeterPaletteItem("BeanShell Sampler", Kind.SAMPLER, "org.apache.jmeter.protocol.java.control.gui.BeanShellSamplerGui", "org.apache.jmeter.protocol.java.sampler.BeanShellSampler"),
            new JMeterPaletteItem("BSF Sampler", Kind.SAMPLER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.protocol.java.sampler.BSFSampler"),
            new JMeterPaletteItem("JSR223 Sampler", Kind.SAMPLER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.protocol.java.sampler.JSR223Sampler"),
            new JMeterPaletteItem("Debug Sampler", Kind.SAMPLER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.sampler.DebugSampler"),
            new JMeterPaletteItem("Flow Control Action", Kind.SAMPLER, "org.apache.jmeter.sampler.gui.TestActionGui", "org.apache.jmeter.sampler.TestAction"),
            new JMeterPaletteItem("FTP Request", Kind.SAMPLER, "org.apache.jmeter.protocol.ftp.control.gui.FtpTestSamplerGui", "org.apache.jmeter.protocol.ftp.sampler.FTPSampler"),
            new JMeterPaletteItem("JDBC Request", Kind.SAMPLER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.protocol.jdbc.sampler.JDBCSampler"),
            new JMeterPaletteItem("JMS Point-to-Point", Kind.SAMPLER, "org.apache.jmeter.protocol.jms.control.gui.JMSSamplerGui", "org.apache.jmeter.protocol.jms.sampler.JMSSampler"),
            new JMeterPaletteItem("JMS Publisher", Kind.SAMPLER, "org.apache.jmeter.protocol.jms.control.gui.JMSPublisherGui", "org.apache.jmeter.protocol.jms.sampler.PublisherSampler"),
            new JMeterPaletteItem("JMS Subscriber", Kind.SAMPLER, "org.apache.jmeter.protocol.jms.control.gui.JMSSubscriberGui", "org.apache.jmeter.protocol.jms.sampler.SubscriberSampler"),
            new JMeterPaletteItem("JUnit Request", Kind.SAMPLER, "org.apache.jmeter.protocol.java.control.gui.JUnitTestSamplerGui", "org.apache.jmeter.protocol.java.sampler.JUnitSampler"),
            new JMeterPaletteItem("LDAP Request", Kind.SAMPLER, "org.apache.jmeter.protocol.ldap.control.gui.LdapTestSamplerGui", "org.apache.jmeter.protocol.ldap.sampler.LDAPSampler"),
            new JMeterPaletteItem("LDAP Extended Request", Kind.SAMPLER, "org.apache.jmeter.protocol.ldap.control.gui.LdapExtTestSamplerGui", "org.apache.jmeter.protocol.ldap.sampler.LDAPExtSampler"),
            new JMeterPaletteItem("Mail Reader Sampler", Kind.SAMPLER, "org.apache.jmeter.protocol.mail.sampler.gui.MailReaderSamplerGui", "org.apache.jmeter.protocol.mail.sampler.MailReaderSampler"),
            new JMeterPaletteItem("SMTP Sampler", Kind.SAMPLER, "org.apache.jmeter.protocol.smtp.sampler.gui.SmtpSamplerGui", "org.apache.jmeter.protocol.smtp.sampler.SmtpSampler"),
            new JMeterPaletteItem("OS Process Sampler", Kind.SAMPLER, "org.apache.jmeter.protocol.system.gui.SystemSamplerGui", "org.apache.jmeter.protocol.system.SystemSampler"),
            new JMeterPaletteItem("TCP Sampler", Kind.SAMPLER, "org.apache.jmeter.protocol.tcp.control.gui.TCPSamplerGui", "org.apache.jmeter.protocol.tcp.sampler.TCPSampler"),
            new JMeterPaletteItem("Bolt Request", Kind.SAMPLER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.protocol.bolt.sampler.BoltSampler"),
            new JMeterPaletteItem("MongoDB Script", Kind.SAMPLER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.protocol.mongodb.sampler.MongoScriptSampler"),

            new JMeterPaletteItem("Loop Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.LoopControlPanel", "org.apache.jmeter.control.LoopController"),
            new JMeterPaletteItem("If Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.IfControllerPanel", "org.apache.jmeter.control.IfController"),
            new JMeterPaletteItem("While Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.WhileControllerGui", "org.apache.jmeter.control.WhileController"),
            new JMeterPaletteItem("Runtime Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.RunTimeGui", "org.apache.jmeter.control.RunTime"),
            new JMeterPaletteItem("Transaction Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.TransactionControllerGui", "org.apache.jmeter.control.TransactionController"),
            new JMeterPaletteItem("Once Only Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.OnceOnlyControllerGui", "org.apache.jmeter.control.OnceOnlyController"),
            new JMeterPaletteItem("Throughput Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.ThroughputControllerGui", "org.apache.jmeter.control.ThroughputController"),
            new JMeterPaletteItem("ForEach Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.ForeachControlPanel", "org.apache.jmeter.control.ForeachController"),
            new JMeterPaletteItem("Include Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.IncludeControllerGui", "org.apache.jmeter.control.IncludeController"),
            new JMeterPaletteItem("Module Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.ModuleControllerGui", "org.apache.jmeter.control.ModuleController"),
            new JMeterPaletteItem("Critical Section Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.CriticalSectionControllerGui", "org.apache.jmeter.control.CriticalSectionController"),
            new JMeterPaletteItem("Recording Controller", Kind.CONTROLLER, "org.apache.jmeter.protocol.http.control.gui.RecordController", "org.apache.jmeter.protocol.http.control.RecordingController"),
            new JMeterPaletteItem("Random Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.RandomControlGui", "org.apache.jmeter.control.RandomController"),
            new JMeterPaletteItem("Random Order Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.RandomOrderControllerGui", "org.apache.jmeter.control.RandomOrderController"),
            new JMeterPaletteItem("Interleave Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.InterleaveControlGui", "org.apache.jmeter.control.InterleaveControl"),
            new JMeterPaletteItem("Switch Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.SwitchControllerGui", "org.apache.jmeter.control.SwitchController"),
            new JMeterPaletteItem("Simple Controller", Kind.CONTROLLER, "org.apache.jmeter.control.gui.LogicControllerGui", "org.apache.jmeter.control.GenericController"),

            new JMeterPaletteItem("User Defined Variables", Kind.CONFIG, "org.apache.jmeter.config.gui.ArgumentsPanel", "org.apache.jmeter.config.Arguments"),
            new JMeterPaletteItem("HTTP Request Defaults", Kind.CONFIG, "org.apache.jmeter.protocol.http.config.gui.HttpDefaultsGui", "org.apache.jmeter.config.ConfigTestElement"),
            new JMeterPaletteItem("GraphQL HTTP Request Defaults", Kind.CONFIG, "org.apache.jmeter.protocol.http.config.gui.GraphQLUrlConfigGui", "org.apache.jmeter.config.ConfigTestElement"),
            new JMeterPaletteItem("HTTP Header Manager", Kind.CONFIG, "org.apache.jmeter.protocol.http.gui.HeaderPanel", "org.apache.jmeter.protocol.http.control.HeaderManager"),
            new JMeterPaletteItem("HTTP Cookie Manager", Kind.CONFIG, "org.apache.jmeter.protocol.http.gui.CookiePanel", "org.apache.jmeter.protocol.http.control.CookieManager"),
            new JMeterPaletteItem("HTTP Cache Manager", Kind.CONFIG, "org.apache.jmeter.protocol.http.gui.CacheManagerGui", "org.apache.jmeter.protocol.http.control.CacheManager"),
            new JMeterPaletteItem("HTTP Authorization Manager", Kind.CONFIG, "org.apache.jmeter.protocol.http.gui.AuthPanel", "org.apache.jmeter.protocol.http.control.AuthManager"),
            new JMeterPaletteItem("DNS Cache Manager", Kind.CONFIG, "org.apache.jmeter.protocol.http.gui.DNSCachePanel", "org.apache.jmeter.protocol.http.control.DNSCacheManager"),
            new JMeterPaletteItem("Java Request Defaults", Kind.CONFIG, "org.apache.jmeter.protocol.java.config.gui.JavaConfigGui", "org.apache.jmeter.protocol.java.config.JavaConfig"),
            new JMeterPaletteItem("FTP Request Defaults", Kind.CONFIG, "org.apache.jmeter.protocol.ftp.config.gui.FtpConfigGui", "org.apache.jmeter.config.ConfigTestElement"),
            new JMeterPaletteItem("JDBC Connection Configuration", Kind.CONFIG, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.protocol.jdbc.config.DataSourceElement"),
            new JMeterPaletteItem("LDAP Request Defaults", Kind.CONFIG, "org.apache.jmeter.protocol.ldap.config.gui.LdapConfigGui", "org.apache.jmeter.config.ConfigTestElement"),
            new JMeterPaletteItem("LDAP Extended Request Defaults", Kind.CONFIG, "org.apache.jmeter.protocol.ldap.config.gui.LdapExtConfigGui", "org.apache.jmeter.config.ConfigTestElement"),
            new JMeterPaletteItem("TCP Sampler Config", Kind.CONFIG, "org.apache.jmeter.protocol.tcp.config.gui.TCPConfigGui", "org.apache.jmeter.config.ConfigTestElement"),
            new JMeterPaletteItem("Bolt Connection Configuration", Kind.CONFIG, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.protocol.bolt.config.BoltConnectionElement"),
            new JMeterPaletteItem("MongoDB Source Config", Kind.CONFIG, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.protocol.mongodb.config.MongoSourceElement"),
            new JMeterPaletteItem("CSV Data Set Config", Kind.CONFIG, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.config.CSVDataSet"),
            new JMeterPaletteItem("Random Variable", Kind.CONFIG, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.config.RandomVariableConfig"),
            new JMeterPaletteItem("Keystore Configuration", Kind.CONFIG, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.config.KeystoreConfig"),
            new JMeterPaletteItem("Simple Config Element", Kind.CONFIG, "org.apache.jmeter.config.gui.SimpleConfigGui", "org.apache.jmeter.config.ConfigTestElement"),

            new JMeterPaletteItem("Response Assertion", Kind.ASSERTION, "org.apache.jmeter.assertions.gui.AssertionGui", "org.apache.jmeter.assertions.ResponseAssertion"),
            new JMeterPaletteItem("Duration Assertion", Kind.ASSERTION, "org.apache.jmeter.assertions.gui.DurationAssertionGui", "org.apache.jmeter.assertions.DurationAssertion"),
            new JMeterPaletteItem("Size Assertion", Kind.ASSERTION, "org.apache.jmeter.assertions.gui.SizeAssertionGui", "org.apache.jmeter.assertions.SizeAssertion"),
            new JMeterPaletteItem("HTML Assertion", Kind.ASSERTION, "org.apache.jmeter.assertions.gui.HTMLAssertionGui", "org.apache.jmeter.assertions.HTMLAssertion"),
            new JMeterPaletteItem("XML Assertion", Kind.ASSERTION, "org.apache.jmeter.assertions.gui.XMLAssertionGui", "org.apache.jmeter.assertions.XMLAssertion"),
            new JMeterPaletteItem("XML Schema Assertion", Kind.ASSERTION, "org.apache.jmeter.assertions.gui.XMLSchemaAssertionGUI", "org.apache.jmeter.assertions.XMLSchemaAssertion"),
            new JMeterPaletteItem("XPath Assertion", Kind.ASSERTION, "org.apache.jmeter.assertions.gui.XPathAssertionGui", "org.apache.jmeter.assertions.XPathAssertion"),
            new JMeterPaletteItem("JSON Assertion", Kind.ASSERTION, "org.apache.jmeter.assertions.gui.JSONPathAssertionGui", "org.apache.jmeter.assertions.JSONPathAssertion"),
            new JMeterPaletteItem("JMESPath Assertion", Kind.ASSERTION, "org.apache.jmeter.assertions.jmespath.gui.JMESPathAssertionGui", "org.apache.jmeter.assertions.jmespath.JMESPathAssertion"),
            new JMeterPaletteItem("XPath2 Assertion", Kind.ASSERTION, "org.apache.jmeter.assertions.gui.XPath2AssertionGui", "org.apache.jmeter.assertions.XPath2Assertion"),
            new JMeterPaletteItem("MD5Hex Assertion", Kind.ASSERTION, "org.apache.jmeter.assertions.gui.MD5HexAssertionGUI", "org.apache.jmeter.assertions.MD5HexAssertion"),
            new JMeterPaletteItem("SMIME Assertion", Kind.ASSERTION, "org.apache.jmeter.assertions.gui.SMIMEAssertionGui", "org.apache.jmeter.assertions.SMIMEAssertionTestElement"),
            new JMeterPaletteItem("Compare Assertion", Kind.ASSERTION, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.assertions.CompareAssertion"),
            new JMeterPaletteItem("JSR223 Assertion", Kind.ASSERTION, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.assertions.JSR223Assertion"),
            new JMeterPaletteItem("BeanShell Assertion", Kind.ASSERTION, "org.apache.jmeter.assertions.gui.BeanShellAssertionGui", "org.apache.jmeter.assertions.BeanShellAssertion"),
            new JMeterPaletteItem("BSF Assertion", Kind.ASSERTION, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.assertions.BSFAssertion"),

            new JMeterPaletteItem("User Parameters", Kind.PRE_PROCESSOR, "org.apache.jmeter.modifiers.gui.UserParametersGui", "org.apache.jmeter.modifiers.UserParameters"),
            new JMeterPaletteItem("Counter", Kind.PRE_PROCESSOR, "org.apache.jmeter.modifiers.gui.CounterConfigGui", "org.apache.jmeter.modifiers.CounterConfig"),
            new JMeterPaletteItem("Sample Timeout", Kind.PRE_PROCESSOR, "org.apache.jmeter.modifiers.gui.SampleTimeoutGui", "org.apache.jmeter.modifiers.SampleTimeout"),
            new JMeterPaletteItem("HTML Link Parser", Kind.PRE_PROCESSOR, "org.apache.jmeter.protocol.http.modifier.gui.AnchorModifierGui", "org.apache.jmeter.protocol.http.modifier.AnchorModifier"),
            new JMeterPaletteItem("HTTP URL Re-writing Modifier", Kind.PRE_PROCESSOR, "org.apache.jmeter.protocol.http.modifier.gui.URLRewritingModifierGui", "org.apache.jmeter.protocol.http.modifier.URLRewritingModifier"),
            new JMeterPaletteItem("RegEx User Parameters", Kind.PRE_PROCESSOR, "org.apache.jmeter.protocol.http.modifier.gui.RegExUserParametersGui", "org.apache.jmeter.protocol.http.modifier.RegExUserParameters"),
            new JMeterPaletteItem("JSR223 PreProcessor", Kind.PRE_PROCESSOR, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.modifiers.JSR223PreProcessor"),
            new JMeterPaletteItem("BeanShell PreProcessor", Kind.PRE_PROCESSOR, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.modifiers.BeanShellPreProcessor"),
            new JMeterPaletteItem("BSF PreProcessor", Kind.PRE_PROCESSOR, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.modifiers.BSFPreProcessor"),

            new JMeterPaletteItem("Boundary Extractor", Kind.POST_PROCESSOR, "org.apache.jmeter.extractor.gui.BoundaryExtractorGui", "org.apache.jmeter.extractor.BoundaryExtractor"),
            new JMeterPaletteItem("Regular Expression Extractor", Kind.POST_PROCESSOR, "org.apache.jmeter.extractor.gui.RegexExtractorGui", "org.apache.jmeter.extractor.RegexExtractor"),
            new JMeterPaletteItem("JSON Extractor", Kind.POST_PROCESSOR, "org.apache.jmeter.extractor.json.jsonpath.gui.JSONPostProcessorGui", "org.apache.jmeter.extractor.json.jsonpath.JSONPostProcessor"),
            new JMeterPaletteItem("JMESPath Extractor", Kind.POST_PROCESSOR, "org.apache.jmeter.extractor.json.jmespath.gui.JMESPathExtractorGui", "org.apache.jmeter.extractor.json.jmespath.JMESPathExtractor"),
            new JMeterPaletteItem("CSS Selector Extractor", Kind.POST_PROCESSOR, "org.apache.jmeter.extractor.gui.HtmlExtractorGui", "org.apache.jmeter.extractor.HtmlExtractor"),
            new JMeterPaletteItem("XPath Extractor", Kind.POST_PROCESSOR, "org.apache.jmeter.extractor.gui.XPathExtractorGui", "org.apache.jmeter.extractor.XPathExtractor"),
            new JMeterPaletteItem("XPath2 Extractor", Kind.POST_PROCESSOR, "org.apache.jmeter.extractor.gui.XPath2ExtractorGui", "org.apache.jmeter.extractor.XPath2Extractor"),
            new JMeterPaletteItem("JSR223 PostProcessor", Kind.POST_PROCESSOR, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.extractor.JSR223PostProcessor"),
            new JMeterPaletteItem("BeanShell PostProcessor", Kind.POST_PROCESSOR, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.extractor.BeanShellPostProcessor"),
            new JMeterPaletteItem("BSF PostProcessor", Kind.POST_PROCESSOR, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.extractor.BSFPostProcessor"),
            new JMeterPaletteItem("Debug PostProcessor", Kind.POST_PROCESSOR, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.extractor.DebugPostProcessor"),

            new JMeterPaletteItem("Constant Timer", Kind.TIMER, "org.apache.jmeter.timers.gui.ConstantTimerGui", "org.apache.jmeter.timers.ConstantTimer"),
            new JMeterPaletteItem("Constant Throughput Timer", Kind.TIMER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.timers.ConstantThroughputTimer"),
            new JMeterPaletteItem("Precise Throughput Timer", Kind.TIMER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.timers.poissonarrivals.PreciseThroughputTimer"),
            new JMeterPaletteItem("Uniform Random Timer", Kind.TIMER, "org.apache.jmeter.timers.gui.UniformRandomTimerGui", "org.apache.jmeter.timers.UniformRandomTimer"),
            new JMeterPaletteItem("Gaussian Random Timer", Kind.TIMER, "org.apache.jmeter.timers.gui.GaussianRandomTimerGui", "org.apache.jmeter.timers.GaussianRandomTimer"),
            new JMeterPaletteItem("Poisson Random Timer", Kind.TIMER, "org.apache.jmeter.timers.gui.PoissonRandomTimerGui", "org.apache.jmeter.timers.PoissonRandomTimer"),
            new JMeterPaletteItem("Synchronizing Timer", Kind.TIMER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.timers.SyncTimer"),
            new JMeterPaletteItem("JSR223 Timer", Kind.TIMER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.timers.JSR223Timer"),
            new JMeterPaletteItem("BeanShell Timer", Kind.TIMER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.timers.BeanShellTimer"),
            new JMeterPaletteItem("BSF Timer", Kind.TIMER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.timers.BSFTimer"),

            new JMeterPaletteItem("View Results Tree", Kind.LISTENER, "org.apache.jmeter.visualizers.ViewResultsFullVisualizer", "org.apache.jmeter.reporters.ResultCollector"),
            new JMeterPaletteItem("Summary Report", Kind.LISTENER, "org.apache.jmeter.visualizers.SummaryReport", "org.apache.jmeter.reporters.ResultCollector"),
            new JMeterPaletteItem("View Results in Table", Kind.LISTENER, "org.apache.jmeter.visualizers.TableVisualizer", "org.apache.jmeter.reporters.ResultCollector"),
            new JMeterPaletteItem("Aggregate Report", Kind.LISTENER, "org.apache.jmeter.visualizers.StatVisualizer", "org.apache.jmeter.reporters.ResultCollector"),
            new JMeterPaletteItem("Aggregate Graph", Kind.LISTENER, "org.apache.jmeter.visualizers.StatGraphVisualizer", "org.apache.jmeter.reporters.ResultCollector"),
            new JMeterPaletteItem("Response Time Graph", Kind.LISTENER, "org.apache.jmeter.visualizers.RespTimeGraphVisualizer", "org.apache.jmeter.reporters.ResultCollector"),
            new JMeterPaletteItem("Graph Results", Kind.LISTENER, "org.apache.jmeter.visualizers.GraphVisualizer", "org.apache.jmeter.reporters.ResultCollector"),
            new JMeterPaletteItem("Assertion Results", Kind.LISTENER, "org.apache.jmeter.visualizers.AssertionVisualizer", "org.apache.jmeter.reporters.ResultCollector"),
            new JMeterPaletteItem("Comparison Assertion Visualizer", Kind.LISTENER, "org.apache.jmeter.visualizers.ComparisonVisualizer", "org.apache.jmeter.reporters.ResultCollector"),
            new JMeterPaletteItem("Simple Data Writer", Kind.LISTENER, "org.apache.jmeter.visualizers.SimpleDataWriter", "org.apache.jmeter.reporters.ResultCollector"),
            new JMeterPaletteItem("Save Responses to a file", Kind.LISTENER, "org.apache.jmeter.reporters.gui.ResultSaverGui", "org.apache.jmeter.reporters.ResultSaver"),
            new JMeterPaletteItem("Mailer Visualizer", Kind.LISTENER, "org.apache.jmeter.visualizers.MailerVisualizer", "org.apache.jmeter.reporters.ResultCollector"),
            new JMeterPaletteItem("BeanShell Listener", Kind.LISTENER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.visualizers.BeanShellListener"),
            new JMeterPaletteItem("BSF Listener", Kind.LISTENER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.visualizers.BSFListener"),
            new JMeterPaletteItem("JSR223 Listener", Kind.LISTENER, "org.apache.jmeter.testbeans.gui.TestBeanGUI", "org.apache.jmeter.visualizers.JSR223Listener"),
            new JMeterPaletteItem("Backend Listener", Kind.LISTENER, "org.apache.jmeter.visualizers.backend.BackendListenerGui", "org.apache.jmeter.visualizers.backend.BackendListener"),

            new JMeterPaletteItem("WorkBench", Kind.NON_TEST, "org.apache.jmeter.control.gui.WorkBenchGui", "org.apache.jmeter.testelement.WorkBench"),
            new JMeterPaletteItem("Property Display", Kind.NON_TEST, "org.apache.jmeter.visualizers.PropertyControlGui", null),
            new JMeterPaletteItem("HTTP(S) Test Script Recorder", Kind.NON_TEST, "org.apache.jmeter.protocol.http.proxy.gui.ProxyControlGui", "org.apache.jmeter.protocol.http.proxy.ProxyControl")
    );

    private final String label;
    private final Kind kind;
    private final String guiClassName;
    private final String testClassName;

    public JMeterPaletteItem(String label, Kind kind, String guiClassName, String testClassName) {
        this.label = label;
        this.kind = kind;
        this.guiClassName = guiClassName;
        this.testClassName = testClassName;
    }

    public String key() {
        if (testClassName != null) {
            return guiClassName + "|" + testClassName;
        }
        return guiClassName;
    }

    public String label() {
        return label;
    }

    public String guiClassName() {
        return guiClassName;
    }

    public String testClassName() {
        return testClassName;
    }

    public TestElement createTestElement() throws ReflectiveOperationException {
        TestElement element = createViaGui();
        if (element == null && testClassName != null) {
            element = createDirect();
        }
        if (element == null) {
            throw new ReflectiveOperationException("Unable to create " + label);
        }
        applyCommonProperties(element);
        return element;
    }

    private TestElement createDirect() throws ReflectiveOperationException {
        TestElement element = (TestElement) JMeterPluginClasspath.loadClass(testClassName)
                .getDeclaredConstructor()
                .newInstance();
        return element;
    }

    public Kind kind() {
        return kind;
    }

    public static JMeterPaletteItem findByKey(String key) {
        for (JMeterPaletteItem item : JMeterPaletteCatalog.items()) {
            if (item.matchesKey(key)) {
                return item;
            }
        }
        return null;
    }

    public static JMeterPaletteItem findByLabel(String label) {
        for (JMeterPaletteItem item : JMeterPaletteCatalog.items()) {
            if (item.label.equals(label)) {
                return item;
            }
        }
        return null;
    }

    public static JMeterPaletteItem findByTestClass(String className) {
        for (JMeterPaletteItem item : JMeterPaletteCatalog.items()) {
            if (item.testClassName != null && item.testClassName.equals(className)) {
                return item;
            }
        }
        return null;
    }

    public static JMeterPaletteItem discovered(String label, Kind kind, String guiClassName, String testClassName) {
        return new JMeterPaletteItem(label, kind, guiClassName, testClassName);
    }

    private TestElement createViaGui() {
        try {
            return createViaGuiOrThrow();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private TestElement createViaGuiOrThrow() throws ReflectiveOperationException {
        Object gui = createGui();
        return ((JMeterGUIComponent) gui).createTestElement();
    }

    private Object createGui() throws ReflectiveOperationException {
        Class<?> guiClass = JMeterPluginClasspath.loadClass(guiClassName);
        if (isTestBeanGui() && testClassName != null) {
            Class<?> testClass = JMeterPluginClasspath.loadClass(testClassName);
            return guiClass.getDeclaredConstructor(Class.class).newInstance(testClass);
        }
        return guiClass.getDeclaredConstructor().newInstance();
    }

    private boolean isTestBeanGui() {
        return "org.apache.jmeter.testbeans.gui.TestBeanGUI".equals(guiClassName);
    }

    private void applyCommonProperties(TestElement element) {
        element.setName(label);
        element.setEnabled(true);
        element.setProperty(TestElement.GUI_CLASS, guiClassName);
        element.setProperty(TestElement.TEST_CLASS, element.getClass().getName());
    }

    private boolean matchesKey(String key) {
        return key().equals(key) || guiClassName.equals(key);
    }

    @Override
    public String toString() {
        return label;
    }

    public enum Kind {
        THREAD_GROUP,
        TEST_FRAGMENT,
        SAMPLER,
        CONTROLLER,
        CONFIG,
        ASSERTION,
        TIMER,
        PRE_PROCESSOR,
        POST_PROCESSOR,
        LISTENER,
        NON_TEST
    }
}

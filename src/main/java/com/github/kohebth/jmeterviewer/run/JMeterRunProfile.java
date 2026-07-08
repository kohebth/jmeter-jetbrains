package com.github.kohebth.jmeterviewer.run;

public final class JMeterRunProfile {
    private final String remoteHosts;
    private final String resultFile;
    private final String logLevel;
    private final Advanced advanced;

    public JMeterRunProfile(String remoteHosts, String resultFile, String logLevel, Advanced advanced) {
        this.remoteHosts = clean(remoteHosts);
        this.resultFile = clean(resultFile);
        this.logLevel = clean(logLevel);
        this.advanced = advanced == null ? Advanced.empty() : advanced;
    }

    public static JMeterRunProfile empty() {
        return new JMeterRunProfile("", "", "", Advanced.empty());
    }

    public String remoteHosts() {
        return remoteHosts;
    }

    public String resultFile() {
        return resultFile;
    }

    public String logLevel() {
        return logLevel;
    }

    public Advanced advanced() {
        return advanced;
    }

    public boolean isEmpty() {
        return remoteHosts.isEmpty()
                && resultFile.isEmpty()
                && logLevel.isEmpty()
                && advanced.isEmpty();
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }

    public static final class Advanced {
        private final String userPropertiesFile;
        private final String jmeterProperties;
        private final String systemProperties;

        public Advanced(String userPropertiesFile, String jmeterProperties, String systemProperties) {
            this.userPropertiesFile = clean(userPropertiesFile);
            this.jmeterProperties = clean(jmeterProperties);
            this.systemProperties = clean(systemProperties);
        }

        public static Advanced empty() {
            return new Advanced("", "", "");
        }

        public String userPropertiesFile() {
            return userPropertiesFile;
        }

        public String jmeterProperties() {
            return jmeterProperties;
        }

        public String systemProperties() {
            return systemProperties;
        }

        public boolean isEmpty() {
            return userPropertiesFile.isEmpty()
                    && jmeterProperties.isEmpty()
                    && systemProperties.isEmpty();
        }
    }
}

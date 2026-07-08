import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.github.kohebth"
version = "0.1.2-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.jmeter:ApacheJMeter_config:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_core:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_components:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_bolt:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_ftp:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_http:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_java:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_jdbc:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_jms:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_junit:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_ldap:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_mail:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_mongodb:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_native:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_tcp:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_functions:5.6.3")
    implementation("org.freemarker:freemarker:2.3.32")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

intellij {
    version.set("2022.1.4")
    type.set("PC")
}

tasks {
    withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-Xlint:deprecation")
    }

    patchPluginXml {
        sinceBuild.set("221")
        untilBuild.set(provider { null })
    }

    test {
        useJUnitPlatform()
    }
}

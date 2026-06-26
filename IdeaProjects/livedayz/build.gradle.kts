import sun.jvmstat.monitor.MonitoredVmUtil.mainClass

plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.0"   // <-- добавьте эту строку
}

group = "livedayz"
version = "1.0-SNAPSHOT"


repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.pcap4j:pcap4j-core:1.8.2")
    implementation("org.pcap4j:pcap4j-packetfactory-static:1.8.2")
    implementation("org.pcap4j:pcap4j-packetfactory-propertiesbased:1.8.2")
    implementation("org.slf4j:slf4j-nop:2.0.9")

}

// Задача для сборки жирного JAR
tasks.shadowJar {
    archiveBaseName.set("dayz-nav")
    archiveVersion.set("1.0")
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "livedayz.app.DayZLiveCapture"
    }
}


tasks.test {
    useJUnitPlatform()
}
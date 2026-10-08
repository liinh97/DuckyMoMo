plugins {
    java
}

dependencies {
    // Ghim đúng bản server đang chạy (log Paper: "Implementing API version ...")
    compileOnly("io.papermc.paper:paper-api:26.2.build.132-stable")
    // Chỉ cần API của AuthMe; plugin AuthMe đã có sẵn trên server
    compileOnly("fr.xephi:authme-core:6.0.1") { isTransitive = false }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25 // Paper 26.x chạy Java 25
}

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.jar {
    archiveBaseName = "DuckyMoMoCore"
    archiveVersion = ""
}

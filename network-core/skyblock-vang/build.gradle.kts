// Plugin nhỏ riêng Skyblock: /vang (server này không chạy DuckyMoMoCore). Build: gradle :skyblock-vang:build
plugins {
    java
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.132-stable")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.jar {
    archiveFileName.set("SkyVang.jar")
}

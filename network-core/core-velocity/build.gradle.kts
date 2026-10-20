// DuckyMoMoSkin: plugin Velocity (proxy) cho Tủ skin /tuskin + cầu nối web -> SkinsRestorer.
// Biên dịch dựa trên đúng jar đang chạy ở proxy (libs/, ops/build-core.sh tự chép, không commit): Velocity 4.3, SkinsRestorer 15.12.
plugins {
    java
}

dependencies {
    compileOnly(files("../libs/velocity.jar", "../libs/SkinsRestorer.jar"))
    compileOnly("net.luckperms:api:5.4")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.jar {
    archiveBaseName = "DuckyMoMoSkin"
    archiveVersion = ""
}

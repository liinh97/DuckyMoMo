plugins {
    java
}

dependencies {
    // Ghim đúng bản server đang chạy (log Paper: "Implementing API version ...")
    compileOnly("io.papermc.paper:paper-api:26.2.build.132-stable")
    // Chỉ cần API của AuthMe; plugin AuthMe đã có sẵn trên server
    compileOnly("fr.xephi:authme-core:6.0.1") { isTransitive = false }
    // Prefix role/rank donate cho chat ở lobby (LuckPerms có sẵn trên mọi server)
    compileOnly("net.luckperms:api:5.4")
    // Ghi lượt phá block vào CoreProtect khi DuckyMoMoCore tự phá (chống đặt-phá lấy tiền nghề)
    compileOnly("net.coreprotect:coreprotect:23.0") { isTransitive = false }
    // Vẽ thị trấn Towny lên bản đồ web squaremap (banghoi)
    compileOnly("xyz.jpenilla:squaremap-api:1.3.15")
    compileOnly("com.palmergames.bukkit.towny:towny:0.103.2.0") { isTransitive = false }
    // Menu dạng form cho người chơi Bedrock (Floodgate trên server con)
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
    // Thu phí /tpr qua Vault (VaultUnlocked giữ API Vault cũ)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    // Placeholder %dmm_bachu_1_ten%… cho hologram bảng xếp hạng (PlaceholderAPI 2.12.3 đang chạy ở banghoi)
    compileOnly("me.clip:placeholderapi:2.12.3") { isTransitive = false }
    // Lữ khách (wander-npcs, banghoi): NPC Citizens tự dời chỗ trên bản đồ, nói chuyện bằng BetonQuest
    compileOnly("net.citizensnpcs:citizensapi:2.0.44-SNAPSHOT") { isTransitive = false }
    // Dán schematic spawn vào world spawn riêng (24/10): biên dịch với đúng jar WorldEdit trên server (chép vào libs/, không commit)
    compileOnly(rootProject.files("libs/worldedit-bukkit-7.4.6.jar"))
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

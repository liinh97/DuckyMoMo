subprojects {
    group = "vn.duckymomo"
    version = "0.1.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-public/") // AuthMe
        maven("https://maven.playpro.com") // CoreProtect API
        maven("https://repo.glaremasters.me/repository/towny/") // Towny API
        maven("https://repo.opencollab.dev/main/") // Floodgate API (form cho Bedrock)
    }
}

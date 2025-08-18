pluginManagement {
    val quarkusPluginVersion: String by settings
    val quarkusPluginId: String by settings
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}
rootProject.name="arylic-mqtt"

includeBuild("../homeassistant-discovery")

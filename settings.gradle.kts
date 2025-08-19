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

val haDiscoveryDir = providers.gradleProperty("haDiscoveryDir")
includeBuild(if (haDiscoveryDir.isPresent) haDiscoveryDir else "../homeassistant-discovery")

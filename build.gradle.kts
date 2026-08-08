buildscript {
    dependencies {
        // AGP 9 has built-in Kotlin; this upgrades the bundled KGP so the
        // Compose compiler plugin and Kotlin compiler stay on the same version.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    id("com.android.application") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

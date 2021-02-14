import com.gradle.publish.PluginBundleExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("net.kyori.indra") version "1.0.2" apply false
  kotlin("jvm") version embeddedKotlinVersion apply false

  id("com.github.ben-manes.versions") version "0.33.0"
}

group = "net.kyori"
version = "1.4.0-SNAPSHOT"

allprojects {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

subprojects {

  apply(plugin = "java-gradle-plugin")
  apply(plugin = "com.gradle.plugin-publish")
  apply(plugin = "maven-publish")
  apply(plugin = "net.kyori.indra")
  apply(plugin = "net.kyori.indra.license-header")

  group = rootProject.group
  version = rootProject.version
  description = rootProject.description

  tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
      jvmTarget = "1.8" // Why is this not the default version? D:
      kotlinOptions.languageVersion = "1.3"
    }
  }

  dependencies {
    "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.7.0")
    "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.7.0")
  }

  val pluginBundle = extensions.getByType<PluginBundleExtension>().apply {
    website = "https://github.com/KyoriPowered/indra"
    vcsUrl = "https://github.com/KyoriPowered/indra.git"
    description = "KyoriPowered organizational build standards and utilities"
    tags = listOf("kyori", "standard")
  }

  // Plugin publishing is a bit weird, so we can't use standard Indra for it
  extensions.getByType<PublishingExtension>().publications.withType<MavenPublication>().configureEach {
    pom {
      name.set(project.name)
      description.set(pluginBundle.description)
      url.set(pluginBundle.vcsUrl)

      organization {
        name.set("KyoriPowered")
        url.set("https://kyori.net")
      }

      developers {
        developer {
          id.set("kashike")
          timezone.set("America/Vancouver")
        }
        developer {
          id.set("zml")
          email.set("zml at stellardrift [.] ca")
          timezone.set("America/Vancouver")
        }
      }

      licenses {
        license {
          name.set("MIT")
          url.set("https://github.com/KyoriPowered/indra/raw/master/license.txt")
          distribution.set("repo")
        }
      }
    }
  }
}

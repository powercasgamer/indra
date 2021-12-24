import net.kyori.indra.IndraExtension
import net.kyori.indra.gradle.IndraPluginPublishingExtension

plugins {
  val indraVersion = "2.0.6"
  id("net.kyori.indra") version indraVersion apply false
  id("net.kyori.indra.publishing.gradle-plugin") version indraVersion apply false
  id("com.gradle.plugin-publish") version "0.18.0" apply false
  id("com.github.ben-manes.versions") version "0.39.0"
  id("com.diffplug.eclipse.apt") version "3.33.1" apply false
  eclipse
}

group = "net.kyori"
version = "2.1.0-SNAPSHOT"
description = "KyoriPowered organizational build standards and utilities"

allprojects {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

subprojects {
  apply(plugin = "java-gradle-plugin")
  apply(plugin = "com.gradle.plugin-publish")
  apply(plugin = "net.kyori.indra")
  apply(plugin = "net.kyori.indra.license-header")
  apply(plugin = "net.kyori.indra.publishing.gradle-plugin")
  apply(plugin = "com.diffplug.eclipse.apt")

  dependencies {
    "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.7.2")
    "testImplementation"("org.junit.jupiter:junit-jupiter-params:5.7.2")
    "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.7.2")
  }

  extensions.configure(IndraExtension::class) {
    github("KyoriPowered", "indra") {
      ci(true)
    }
    mitLicense()

    configurePublications {
      pom {
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
      }

      versionMapping {
        usage(Usage.JAVA_API) { fromResolutionOf(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME) }
        usage(Usage.JAVA_RUNTIME) { fromResolutionResult() }
      }
    }

    publishSnapshotsTo("stellardrift", "https://repo.stellardrift.ca/repository/snapshots/")
    
  }

  extensions.configure(IndraPluginPublishingExtension::class) {
    bundleTags("kyori", "standard")
    website("https://github.com/KyoriPowered/indra/wiki")
  }

  tasks.named("test", Test::class) {
    if (JavaVersion.current() > JavaVersion.VERSION_1_8) {
      jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    }
  }
}

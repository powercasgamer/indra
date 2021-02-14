dependencies {
  compileOnlyApi("org.immutables:value:2.8.8:annotations")
  annotationProcessor("org.immutables:value:2.8.8")
  compileOnlyApi("org.immutables:builder:2.8.8")
  compileOnlyApi("org.checkerframework:checker-qual:3.10.0")
  api(project(":indra-git"))
  implementation("gradle.plugin.org.cadixdev.gradle:licenser:0.5.0")
}

indraPluginPublishing {
  plugin(
    "indra",
    "net.kyori.indra.IndraPlugin",
    "Indra",
    "Simplified tools for configuring modern JVM projects",
    listOf("boilerplate", "java", "jvm")
  )

  plugin(
    "indra.checkstyle",
    "net.kyori.indra.IndraCheckstylePlugin",
    "Indra Checkstyle",
    "Checkstyle configuration in line with the Indra file layout",
    listOf("boilerplate", "checkstyle")
  )

  plugin(
    "indra.license-header",
    "net.kyori.indra.IndraLicenseHeaderPlugin",
    "Indra License Header",
    "License header configuration in line with the Indra file layout",
    listOf("boilerplate", "license", "license-header", "licensing")
  )

  plugin(
    "indra.publishing",
    "net.kyori.indra.IndraPublishingPlugin",
    "Indra Publishing",
    "Reasonable publishing configuration and repository aliases",
    listOf("boilerplate", "publishing", "nexus")
  )
}

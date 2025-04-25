plugins {
    `java-library`
    `maven-publish`
    id("com.jfrog.artifactory") version "5.+"
}

repositories {
    mavenLocal()
    maven(url = "https://artifacts.wolfyscript.com/artifactory/gradle-dev")
    maven(url = "https://repo.maven.apache.org/maven2/")
}

dependencies {
    api(libs.com.fasterxml.jackson.core.jackson.annotations)
    api(libs.com.fasterxml.jackson.core.jackson.core)
    api(libs.com.fasterxml.jackson.core.jackson.databind)
    api(libs.com.typesafe.config)
    testImplementation(libs.junit.junit)
}

group = "com.wolfyscript"
version = "2.1-SNAPSHOT"
description = "HOCON support for Jackson"
java.sourceCompatibility = JavaVersion.VERSION_1_8

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
        artifact(file("$rootDir/gradle.properties"))
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>() {
    options.encoding = "UTF-8"
}

artifactory {

    publish {
        contextUrl = "https://artifacts.wolfyscript.com/artifactory"
        repository {
            repoKey = "gradle-dev-local"
            username = project.properties["wolfyRepoPublishUsername"].toString()
            password = project.properties["wolfyRepoPublishToken"].toString()
        }
        defaults {
            publications("maven")
            setPublishArtifacts(true)
            setPublishPom(true)
            isPublishBuildInfo = false
        }
    }

}

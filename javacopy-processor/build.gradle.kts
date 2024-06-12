plugins {
    id("java-library")
    `maven-publish`
    kotlin("jvm")
}

java {
    sourceCompatibility= JavaVersion.VERSION_17
    targetCompatibility =JavaVersion.VERSION_17
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.symbol.processing.api)
    implementation(project(":javacopy-annotation"))

    implementation("com.google.auto.service:auto-service-annotations:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
    implementation("com.squareup:javapoet:1.13.0")
    implementation("com.squareup:kotlinpoet:1.17.0")
    implementation("com.squareup:kotlinpoet-ksp:1.17.0")
    implementation("androidx.annotation:annotation:1.7.0")
    implementation(gradleApi())
}

val groupId: String by project
val version: String by project

publishing {
    publications {
        create<MavenPublication>("javacopy") {
            groupId = groupId
            artifactId = "processor"
            version = version

            afterEvaluate {
                from(components["java"])
            }
        }
    }
}

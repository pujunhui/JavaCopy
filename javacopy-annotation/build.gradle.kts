plugins {
    id("java-library")
    `maven-publish`
}

java {
    sourceCompatibility= JavaVersion.VERSION_17
    targetCompatibility =JavaVersion.VERSION_17
}

val groupId: String by project
val version: String by project

publishing {
    publications {
        create<MavenPublication>("javacopy") {
            groupId =  groupId
            artifactId = "annotation"
            version = version

            afterEvaluate {
                from(components["java"])
            }
        }
    }
}

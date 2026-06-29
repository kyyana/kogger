plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.vanniktech.maven.publish)
}

repositories {
    mavenCentral()
}

kotlin {
    linuxX64()
    linuxArm64()

    sourceSets {
        nativeMain {
            dependencies {
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines)
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(group.toString(), "kogger", version.toString())

    pom {
        name = "kogger"
        description = "A small logging library for Kotlin/Native."
        inceptionYear = "2026"
        url = "https://github.com/kyyana/kogger"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "thelipe7"
                name = "Felipe Machado"
                url = "https://github.com/thelipe7/"
            }
        }
        scm {
            url = "https://github.com/kyyana/kogger/"
            connection = "scm:git:git://github.com/kyyana/kogger.git"
            developerConnection = "scm:git:ssh://git@github.com/kyyana/kogger.git"
        }
    }
}

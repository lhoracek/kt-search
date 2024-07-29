import org.gradle.internal.impldep.org.jsoup.safety.Safelist.basic
import org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode.WARNING
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Properties

plugins {
    kotlin("multiplatform") apply false
    id("org.jetbrains.dokka") apply false
}

println("project: $path")
println("version: $version")
println("group: $group")


// function to load propertie file local.properties
fun loadAccessToken(): String {
    val properties = Properties().apply {
        rootProject.file("local.properties").reader().use(::load)
    }
    return properties["azdoArtifactsAccessToken"] as String
}

allprojects {
    repositories {
        mavenCentral()
        maven(url = "https://pkgs.dev.azure.com/drmaxglobal/mobile-team/_packaging/gmad/maven/v1") {
            name = "gmad"
            credentials {
                username = "drmaxglobal"
                password = System.getenv("SYSTEM_ACCESSTOKEN") ?: loadAccessToken()
            }
            content {
                includeGroup("com.jillesvangurp")
            }
        }
    }
}

subprojects {

    tasks.withType<KotlinJvmCompile> {
        jvmTargetValidationMode.set(WARNING)

        kotlinOptions {
            // this is the minimum LTS version we support, 11 and 8 are no longer supported
            jvmTarget = "17"
        }
    }

    tasks.register("versionCheck") {
        doLast {
            if (rootProject.version == "unspecified") {
                error("call with -Pversion=x.y.z to set a version and make sure it lines up with the current tag")
            }
        }
    }

    tasks.withType<PublishToMavenRepository> {
        dependsOn("versionCheck")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        testLogging.events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
        )
        addTestListener(object : TestListener {
            val failures = mutableListOf<String>()
            override fun beforeSuite(desc: TestDescriptor) {
            }

            override fun afterSuite(desc: TestDescriptor, result: TestResult) {
            }

            override fun beforeTest(desc: TestDescriptor) {
            }

            override fun afterTest(desc: TestDescriptor, result: TestResult) {
                if (result.resultType == TestResult.ResultType.FAILURE) {
                    val report =
                        """
                    TESTFAILURE ${desc.className} - ${desc.name}
                    ${
                            result.exception?.let { e ->
                                """
                            ${e::class.simpleName} ${e.message}
                        """.trimIndent()
                            }
                        }
                    -----------------
                    """.trimIndent()
                    failures.add(report)
                }
            }
        })
    }

    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")

    afterEvaluate {
        tasks.register<Jar>("dokkaJar") {
            from(tasks["dokkaHtml"])
            dependsOn(tasks["dokkaHtml"])
            archiveClassifier.set("javadoc")
        }

        configure<PublishingExtension> {
            repositories {
                maven {
                    url = uri("https://pkgs.dev.azure.com/drmaxglobal/mobile-team/_packaging/gmad/maven/v1")
                    name = "drmaxglobal"
                    credentials {
                        username = "drmaxglobal"
                        password = System.getenv("SYSTEM_ACCESSTOKEN") ?: loadAccessToken()
                    }
                }
            }
        }
    }
}



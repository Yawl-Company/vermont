package com.yawl

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

class AndroidCodeCoverageConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("jacoco")

            extensions.configure(CommonExtension::class.java) { android ->
                with(android) {
                    buildTypes.configureEach {
                        it.enableUnitTestCoverage = true
                        it.enableAndroidTestCoverage = true
                    }
                }
            }

            extensions.configure(JacocoPluginExtension::class.java) {
                it.toolVersion = libs.versions.jacoco.get()
            }

            extensions.configure(AndroidComponentsExtension::class.java) { android ->
                val objects = project.objects
                val fileTree = objects.fileTree()
                val filesProperty = objects.listProperty(RegularFile::class.java)
                val directoriesProperty = objects.listProperty(Directory::class.java)
                val buildDirectory = layout.buildDirectory.get().asFile

                android.onVariants { variant ->
                    val taskId = variant.name.capitalized()
                    val reportTask = tasks.register(
                        "create${taskId}CodeCoverageReport",
                        JacocoReport::class.java,
                    ) { jacoco ->
                        with(jacoco) {
                            classDirectories.setFrom(
                                filesProperty,
                                directoriesProperty.map { directories ->
                                    directories.map { directory ->
                                        fileTree
                                            .setDir(directory)
                                            .exclude(exclusions)
                                    }
                                }
                            )

                            reports.apply {
                                xml.required.set(true)
                                html.required.set(true)
                            }

                            sourceDirectories.setFrom(
                                files(
                                    "$projectDir/src/main/java",
                                    "$projectDir/src/main/kotlin",
                                ),
                            )

                            executionData.setFrom(
                                project.fileTree("$buildDirectory/outputs/jacoco/${variant.name}UnitTest.exec")
                                    .matching { it.include("**/*.exec") },
                                project.fileTree("$buildDirectory/outputs/jacoco/${variant.name}AndroidTest.ec")
                                    .matching { it.include("**/*.exec") }
                            )
                        }

                        group = "Report"
                        description = "Generate code coverage report on the $taskId"
                    }

                    variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                        .use(reportTask)
                        .toGet(
                            ScopedArtifact.CLASSES,
                            { _ -> filesProperty },
                            { _ -> directoriesProperty },
                        )
                }
            }
        }
    }

    private val exclusions = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Module*.*",
        "**/*Dagger*.*",
    )
}
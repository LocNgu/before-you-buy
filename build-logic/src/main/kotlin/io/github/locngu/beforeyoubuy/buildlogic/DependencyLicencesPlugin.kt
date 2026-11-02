package io.github.locngu.beforeyoubuy.buildlogic

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Attribute

private val ARTIFACT_TYPE = Attribute.of("artifactType", String::class.java)

/**
 * Registers `checkDependencyLicences` on an Android application: for every variant it checks the licence notices
 * AboutLibraries generates into the variant's resources (ADR-0023). The allow-list is `third-party/allowlist.json`
 * in the project, next to the licence texts AboutLibraries reads.
 */
class DependencyLicencesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("com.android.application") {
            val aggregate =
                project.tasks.register("checkDependencyLicences") {
                    group = "verification"
                    description =
                        "Fails if a shipped dependency isn't Apache-2.0, MIT or BSD, or its notice is missing (ADR-0023)."
                }
            val allowList = project.layout.projectDirectory.file("third-party/allowlist.json")
            val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                val check =
                    project.tasks.register(
                        "check${variant.name.replaceFirstChar { it.uppercase() }}DependencyLicences",
                        CheckDependencyLicencesTask::class.java
                    ) {
                        group = "verification"
                        description = "Checks the licences and notices of the ${variant.name} classpath (ADR-0023)."
                        variantName.set(variant.name)
                        val runtime = variant.runtimeConfiguration.incoming
                        runtimeClasspath.set(runtime.resolutionResult.rootComponent)
                        // Whole AARs (a NOTICE can sit outside classes.jar) and the JARs, of external modules only.
                        listOf("aar", "jar").forEach { type ->
                            val artifacts =
                                runtime.artifactView {
                                    attributes.attribute(ARTIFACT_TYPE, type)
                                    componentFilter { it is ModuleComponentIdentifier }
                                }.artifacts
                            runtimeArtifacts.addAll(artifacts.resolvedArtifacts)
                            runtimeArtifactFiles.from(artifacts.artifactFiles)
                        }
                        variant.sources.res?.let { resDirectories.from(it.all) }
                        this.allowList.set(allowList)
                        rootDirectory.set(project.rootProject.layout.projectDirectory)
                        result.set(
                            project.layout.buildDirectory.file("reports/checkDependencyLicences/${variant.name}.txt")
                        )
                    }
                aggregate.configure { dependsOn(check) }
            }
        }
    }
}

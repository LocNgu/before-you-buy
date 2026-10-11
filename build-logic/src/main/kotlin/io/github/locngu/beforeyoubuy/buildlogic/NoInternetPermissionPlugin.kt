package io.github.locngu.beforeyoubuy.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers `checkNoInternetPermission` on an Android application: it inspects the merged manifest of every
 * release variant (ADR-0003).
 */
class NoInternetPermissionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("com.android.application") {
            val check =
                project.tasks.register("checkNoInternetPermission", CheckNoInternetPermissionTask::class.java) {
                    group = "verification"
                    description =
                        "Fails if the merged release manifest requests android.permission.INTERNET (ADR-0003)."
                    rootDirectory.set(project.rootProject.layout.projectDirectory)
                    mergerReport.set(
                        project.layout.buildDirectory.file("outputs/logs/manifest-merger-release-report.txt")
                    )
                    result.set(project.layout.buildDirectory.file("reports/checkNoInternetPermission/result.txt"))
                }
            val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
                val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
                check.configure { mergedManifests.from(manifest) }
            }
        }
    }
}

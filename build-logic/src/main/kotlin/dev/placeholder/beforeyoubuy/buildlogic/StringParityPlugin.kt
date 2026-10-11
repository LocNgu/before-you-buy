package dev.placeholder.beforeyoubuy.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Registers `checkStringParity` on the root project. It covers the `res/` directories of every module. */
class StringParityPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        require(project == project.rootProject) { "Apply beforeyoubuy.string-parity to the root project." }
        project.tasks.register("checkStringParity", CheckStringParityTask::class.java) {
            group = "verification"
            description = "Fails if values/ and values-de/ don't have the same string resources (ADR-0016)."
            valuesFiles.from(
                project.fileTree(project.rootDir) {
                    include("**/src/*/res/values/*.xml", "**/src/*/res/values-de/*.xml")
                    exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", ".git/**", ".claude/**", "build-logic/**")
                }
            )
            rootDirectory.set(project.layout.projectDirectory)
            result.set(project.layout.buildDirectory.file("reports/checkStringParity/result.txt"))
        }
    }
}

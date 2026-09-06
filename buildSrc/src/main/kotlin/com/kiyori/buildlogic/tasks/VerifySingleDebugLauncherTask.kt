package com.kiyori.buildlogic.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

@DisableCachingByDefault(
    because = "This task verifies the merged Debug Manifest and has no generated output."
)
abstract class VerifySingleDebugLauncherTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val document =
            DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(mergedManifest.get().asFile)
        val launchableComponents = mutableListOf<String>()
        val allActivityNames = mutableListOf<String>()

        listOf("activity", "activity-alias").forEach { componentTag ->
            val components = document.getElementsByTagName(componentTag)
            for (componentIndex in 0 until components.length) {
                val component = components.item(componentIndex) as Element
                val componentName = component.getAttributeNS(androidNamespace, "name")
                allActivityNames += componentName
                val children = component.childNodes
                for (childIndex in 0 until children.length) {
                    val intentFilter = children.item(childIndex) as? Element ?: continue
                    if (intentFilter.tagName != "intent-filter") continue
                    val actions =
                        intentFilter.getElementsByTagName("action").let { nodes ->
                            buildSet {
                                for (index in 0 until nodes.length) {
                                    add(
                                        (nodes.item(index) as Element)
                                            .getAttributeNS(androidNamespace, "name")
                                    )
                                }
                            }
                        }
                    val categories =
                        intentFilter.getElementsByTagName("category").let { nodes ->
                            buildSet {
                                for (index in 0 until nodes.length) {
                                    add(
                                        (nodes.item(index) as Element)
                                            .getAttributeNS(androidNamespace, "name")
                                    )
                                }
                            }
                        }
                    if (
                        "android.intent.action.MAIN" in actions &&
                            "android.intent.category.LAUNCHER" in categories
                    ) {
                        launchableComponents += componentName
                    }
                }
            }
        }

        check("live.pw.renderX.LatexView" !in allActivityNames) {
            "RenderX sample LatexView leaked into the merged Debug Manifest"
        }
        check(
            launchableComponents ==
                listOf("com.ai.assistance.operit.ui.main.MainActivity")
        ) {
            "Debug APK must expose exactly one launcher MainActivity, found $launchableComponents"
        }
        logger.lifecycle(
            "Verified one Debug launcher: ${launchableComponents.single()}"
        )
    }
}

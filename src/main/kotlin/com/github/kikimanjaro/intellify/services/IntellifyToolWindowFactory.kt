package com.github.kikimanjaro.intellify.services

import com.github.kikimanjaro.intellify.ui.SpotifyToolWindowPanel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindow

class IntellifyToolWindowFactory : ToolWindowFactory, DumbAware {
    private var statusUpdaterThread: Thread? = null
    private lateinit var spotifyToolWindowStatusUpdater: SpotifyToolWindowStatusUpdater

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        println("Starting status updater thread")
        spotifyToolWindowStatusUpdater = SpotifyToolWindowStatusUpdater(toolWindow)
        statusUpdaterThread = Thread(spotifyToolWindowStatusUpdater)
        statusUpdaterThread!!.start()
        println("Creating Intellify Tool Window")
        val spotifyToolWindowPanel = SpotifyToolWindowPanel(toolWindow, spotifyToolWindowStatusUpdater).getContentPanel()
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(spotifyToolWindowPanel, "", false)
        )
        println("Intellify Tool Window created")
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return true
    }
}
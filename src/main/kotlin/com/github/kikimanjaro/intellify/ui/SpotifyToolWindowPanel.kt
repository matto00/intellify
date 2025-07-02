package com.github.kikimanjaro.intellify.ui

import com.github.kikimanjaro.intellify.services.SpotifyService
import com.github.kikimanjaro.intellify.services.SpotifyToolWindowStatusUpdater
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.util.preferredWidth
import java.awt.*
import javax.swing.*

class SpotifyToolWindowPanel(private val toolWindow: ToolWindow, private val spotifyToolWindowStatusUpdater: SpotifyToolWindowStatusUpdater) {
    private val contentPanel = JPanel()
    private val currentTrackPanel: SpotifyCurrentTrackPanel = SpotifyCurrentTrackPanel(this, spotifyToolWindowStatusUpdater)
    private val playlistsPanel: SpotifyPlaylistsPanel = SpotifyPlaylistsPanel(this, spotifyToolWindowStatusUpdater)

    private var activePanel: JPanel = currentTrackPanel

    init {
        contentPanel.layout = BorderLayout(0, 20)
        contentPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0))
        contentPanel.add(currentTrackPanel, BorderLayout.CENTER)
        currentTrackPanel.update()
        SpotifyService.currentPanel = this
    }

    fun maxWidth() : Int {
        return activePanel.preferredWidth.coerceAtMost(400).coerceAtLeast(50)
    }

    fun maxHeight() : Int {
        return maxWidth()
    }

    fun getContentPanel(): JPanel {
        return contentPanel
    }

    fun update() {
        when (activePanel) {
            is SpotifyCurrentTrackPanel -> (activePanel as SpotifyCurrentTrackPanel).update()
            is SpotifyPlaylistsPanel -> return
        }
    }

    fun openPlaylists() {
        contentPanel.removeAll()
        contentPanel.add(playlistsPanel, BorderLayout.CENTER)
        playlistsPanel.openPlaylists()
        contentPanel.revalidate()
        contentPanel.repaint()
        activePanel = playlistsPanel
    }

    fun openCurrentTrack() {
        contentPanel.removeAll()
        contentPanel.add(currentTrackPanel, BorderLayout.CENTER)
        SpotifyService.currentPanel = this
        currentTrackPanel.update()
        contentPanel.revalidate()
        contentPanel.repaint()
        activePanel = currentTrackPanel
    }
}

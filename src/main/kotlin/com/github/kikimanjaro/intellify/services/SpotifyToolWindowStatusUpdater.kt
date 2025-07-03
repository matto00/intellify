package com.github.kikimanjaro.intellify.services

import com.github.kikimanjaro.intellify.ui.ImageFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import javax.swing.Icon
import javax.swing.SwingUtilities

class SpotifyToolWindowStatusUpdater(
    private var toolWindow: ToolWindow?
) : Runnable {
    private var stop = false
    private val spotifyActiveIcon: Icon = IconLoader.getIcon("/icons/spotify.svg", this::class.java)
    private val spotifyInactiveIcon: Icon = IconLoader.getIcon("/icons/spotify-inactive.svg", this::class.java)
    val playIcon: Icon = IconLoader.getIcon("/icons/play.svg", this::class.java)
    val pauseIcon: Icon = IconLoader.getIcon("/icons/pause.svg", this::class.java)
    val nextIcon: Icon = IconLoader.getIcon("/icons/next.svg", this::class.java)
    val prevIcon: Icon = IconLoader.getIcon("/icons/prev.svg", this::class.java)
    val addIcon: Icon = IconLoader.getIcon("/icons/add.svg", this::class.java)
    val greenCheckIcon: Icon = IconLoader.getIcon("/icons/greencheckmark.svg", this::class.java)
    val playlistsIcon: Icon = IconLoader.getIcon("/icons/playlists.svg", this::class.java)
    val shuffleOff: Icon = IconLoader.getIcon("/icons/shuffle-off.svg", this::class.java)
    val shuffleOn: Icon = IconLoader.getIcon("/icons/shuffle-on.svg", this::class.java)
    val currentIcon: Icon
        get() = if (SpotifyService.title.isNotEmpty()) spotifyActiveIcon else spotifyInactiveIcon

    override fun run() {
        while (!stop) { //TODO: change with timer
            try {
                SpotifyService.getInformationAboutUsersCurrentPlayingTrack()
                updateUI()
                Thread.sleep(1000L)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateUI() {
        SwingUtilities.invokeLater {
            if (toolWindow == null || !toolWindow!!.isVisible) {
                return@invokeLater
            }
            ImageFactory.updateCache()
            SpotifyService.currentPanel?.update()
            toolWindow?.setIcon(currentIcon)
        }
    }

    fun stop() {
        stop = true
    }
}
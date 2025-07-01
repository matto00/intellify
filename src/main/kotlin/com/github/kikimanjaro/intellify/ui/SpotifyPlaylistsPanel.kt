package com.github.kikimanjaro.intellify.ui

import com.github.kikimanjaro.intellify.services.SpotifyService
import com.github.kikimanjaro.intellify.services.SpotifyStatusUpdater
import se.michaelthelin.spotify.model_objects.specification.Paging
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack
import se.michaelthelin.spotify.model_objects.specification.Track
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.Dimension
import javax.swing.*
import javax.swing.border.Border

class PackItemsMeta(
    val title: String,
    val topBarText: String,
    val items: Array<out Any>,
    val contextUri: String? = null
)

class SpotifyPlaylistsPanel(private val parent: JPanel, private val spotifyStatusUpdater: SpotifyStatusUpdater) : JDialog() {

    init {
        isVisible = false
        title = "Spotify Playlists"
        defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        size = Dimension(300, 400)
        setLocationRelativeTo(null)
        isResizable = false
        contentPane = JPanel()
        contentPane.layout = BorderLayout()
        contentPane.isVisible = false
    }

    fun openPlaylists() {
        val playlists = SpotifyService.getPlaylists()?.items as Array<PlaylistSimplified>?

        if (playlists.isNullOrEmpty()) {
            JOptionPane.showMessageDialog(this, "No playlists found.", "Playlists", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        packItems(
            PackItemsMeta(
                "Playlists",
                "Select a playlist",
                playlists)
        )
    }

    fun openPlaylistSongs(playlist: PlaylistSimplified) {
        val songsForPlaylist = SpotifyService.getSongsForPlaylist(playlist.id)?.items
        if (songsForPlaylist.isNullOrEmpty()) {
            JOptionPane.showMessageDialog(this, "No songs found in this playlist.", "Playlists", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        // Filter out null tracks and cast to Track, see PlaylistTrack for details
        val tracks = songsForPlaylist.mapNotNull { it.track as? Track }.toTypedArray()
        packItems(
            PackItemsMeta(
                "$playlist.name",
                "Play a song",
                tracks,
                playlist.uri)
        )
    }

    private fun packItems(meta: PackItemsMeta) {
        this.title = title
        this.contentPane.removeAll()
        this.contentPane = JPanel()
        this.contentPane.layout = BorderLayout()

        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)

        // Top bar
        val topBar = JPanel(BorderLayout())
        topBar.preferredSize = Dimension(250, 32)

        val titleLabel = JLabel(meta.topBarText)
        titleLabel.isEnabled = false
        titleLabel.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
        titleLabel.horizontalAlignment = JLabel.CENTER
        topBar.add(titleLabel, BorderLayout.CENTER)

        if (meta.items.isArrayOf<Track>()) {
            val backButton = JButton("Back")
            backButton.addActionListener {
                openPlaylists()
            }
            topBar.add(backButton, BorderLayout.WEST)
        }

        this.contentPane.add(topBar, BorderLayout.NORTH)

        meta.items.forEach { item: Any ->
            when(item) {
                is PlaylistSimplified -> {
                    addPlaylistItem(contentPanel, item)
                }
                is Track -> {
                    addTrackItem(contentPanel, item, meta.contextUri)
                }
                else -> {
                    println("Unsupported item type: ${item::class.java.name}")
                }
            }
        }

        val scrollPane = JScrollPane(contentPanel)
        scrollPane.preferredSize = Dimension(300, 400)
        scrollPane.border = null
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED

        this.contentPane.add(scrollPane)
        this.pack()
        this.setLocationRelativeTo(parent)
        this.isVisible = true

        if (!this.contentPane.isVisible) {
            this.contentPane.isVisible = true
        }

    }

    private fun addPlaylistItem(contentPanel: JPanel, playlist: PlaylistSimplified) {
        val playlistPanel = JPanel(BorderLayout())
        val truncatedName = if (playlist.name.length > 20) {
            playlist.name.substring(0, 20) + "..."
        } else {
            playlist.name
        }

        val openButton = JButton(truncatedName)
        openButton.addActionListener {
            openPlaylistSongs(playlist)
        }

        val playButton = JButton(spotifyStatusUpdater.playIcon)
        playButton.addActionListener {
            SpotifyService.playPlaylist(playlist.uri)
            this.contentPane.isVisible = false
            this.dispose()
        }

        playlistPanel.add(openButton, BorderLayout.CENTER)
        playlistPanel.add(playButton, BorderLayout.EAST)
        playlistPanel.preferredSize = Dimension(250, 32)

        val menuItem = JMenuItem()
        menuItem.layout = BorderLayout()
        menuItem.add(playlistPanel, BorderLayout.CENTER)
        menuItem.preferredSize = Dimension(260, 40)

        contentPanel.add(menuItem)
    }

    private fun addTrackItem(contentPanel: JPanel, track: Track, contextUri: String? = null) {
        val truncatedName = if (track.name.length > 40) {
            track.name.substring(0, 40) + "..."
        } else {
            track.name
        }

        val menuItem = JMenuItem(truncatedName)
        menuItem.size = Dimension(260, 25)
        menuItem.addActionListener {
            if (contextUri != null) SpotifyService.setPlayContext(contextUri)
            SpotifyService.playTrack(track.uri)
            this.contentPane.isVisible = false
            this.dispose()
        }
        contentPanel.add(menuItem)
    }
}
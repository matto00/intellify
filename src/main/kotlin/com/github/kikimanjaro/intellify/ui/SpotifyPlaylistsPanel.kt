package com.github.kikimanjaro.intellify.ui

import com.github.kikimanjaro.intellify.services.SpotifyService
import com.github.kikimanjaro.intellify.services.SpotifyToolWindowStatusUpdater
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified
import se.michaelthelin.spotify.model_objects.specification.Track
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.*

class PackItemsMeta(
    val title: String,
    val topBarText: String,
    val items: Array<out Any>,
    val contextUri: String? = null
)

class SpotifyPlaylistsPanel(private val parent: SpotifyToolWindowPanel, private val spotifyToolWindowStatusUpdater: SpotifyToolWindowStatusUpdater) : JPanel() {

    init {
        isVisible = false
        layout = BorderLayout()
    }

    fun openPlaylists() {
        this.removeAll()

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
        this.removeAll()

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
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)

        // Top bar
        val topBar = JPanel(BorderLayout())
        topBar.preferredSize = Dimension(250, 32)

        val titleLabel = JLabel(meta.topBarText)
        titleLabel.isEnabled = false
        titleLabel.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
        titleLabel.horizontalAlignment = JLabel.LEFT
        topBar.add(titleLabel, BorderLayout.WEST)

        val backButton = JButton("Back")
        backButton.addActionListener {
            if (meta.items.isArrayOf<PlaylistSimplified>())
                parent.openCurrentTrack()
            else
                parent.openPlaylists()
        }
        topBar.add(backButton, BorderLayout.EAST)

        this.add(topBar, BorderLayout.NORTH)

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

        val scrollPane = JBScrollPane(contentPanel)
        scrollPane.preferredSize = Dimension(300, 400)
        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED

        this.isVisible = true

        if (!this.isVisible) {
            this.isVisible = true
        }

        this.add(scrollPane)
    }

    private fun addPlaylistItem(contentPanel: JPanel, playlist: PlaylistSimplified) {
        val playlistPanel = JPanel(BorderLayout())

        val imageSize = 100

        val imageUrl = playlist.images.firstOrNull()?.url ?: ""
        val image: BufferedImage = try {
            if (imageUrl.isNotEmpty()) {
                ImageIO.read(URL(imageUrl))
            } else {
                // Use a placeholder image if URL is empty
                UIUtil.createImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB)
            }
        } catch (e: Exception) {
            // Handle invalid URL or loading error
            UIUtil.createImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB)
        }
        val scaledImage = image.getScaledInstance(imageSize, imageSize, Image.SCALE_SMOOTH)

        val imageIcon = ImageIcon(scaledImage)
        val imageButton = JButton(imageIcon)
        imageButton.preferredSize = Dimension(imageSize, imageSize)
        imageButton.border = BorderFactory.createEmptyBorder()
        imageButton.addActionListener {
            openPlaylistSongs(playlist)
        }

        val playlistMeta = JPanel(BorderLayout())
        playlistMeta.preferredSize = Dimension(imageSize + 50, imageSize)

        val playlistTitle = JLabel(playlist.name)
        playlistTitle.border = BorderFactory.createEmptyBorder(0, 8, 0, 8)


        val playlistDescription = if (playlist.description.isNotEmpty()) {
            val label = JLabel(playlist.description)
            label.font = label.font.deriveFont(label.font.size2D * 0.8f)
            label.border = BorderFactory.createEmptyBorder(0, 8, 0, 8)
            label
        } else {
            null
        }

        val playlistTotalSongs = JLabel("${playlist.tracks.total} songs")
        playlistTotalSongs.font = playlistTotalSongs.font.deriveFont(playlistTotalSongs.font.size2D * 0.8f)
        playlistTotalSongs.border = BorderFactory.createEmptyBorder(0, 8, 0, 8)

        playlistMeta.add(playlistTitle, BorderLayout.NORTH)
        when (playlistDescription) {
            null -> playlistMeta.add(playlistTotalSongs, BorderLayout.CENTER)
            else -> {
                playlistMeta.add(playlistDescription, BorderLayout.CENTER)
                playlistMeta.add(playlistTotalSongs, BorderLayout.SOUTH)
            }
        }

        playlistPanel.add(imageButton, BorderLayout.WEST)
        playlistPanel.add(playlistMeta, BorderLayout.CENTER)
        playlistPanel.preferredSize = Dimension(imageSize + 50, imageSize)

        val menuItem = JMenuItem()
        menuItem.layout = BorderLayout()
        menuItem.add(playlistPanel, BorderLayout.CENTER)
        menuItem.preferredSize = Dimension(imageSize + 50, imageSize)
        menuItem.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)

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
            parent.openCurrentTrack()
        }
        contentPanel.add(menuItem)
    }
}
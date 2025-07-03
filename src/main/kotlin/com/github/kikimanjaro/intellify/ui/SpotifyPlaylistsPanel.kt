package com.github.kikimanjaro.intellify.ui

import com.github.kikimanjaro.intellify.services.SpotifyService
import com.github.kikimanjaro.intellify.services.SpotifyToolWindowStatusUpdater
import com.intellij.ui.components.JBScrollPane
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack
import se.michaelthelin.spotify.model_objects.specification.Track
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Image
import java.awt.image.BufferedImage
import javax.swing.*

class PackItemsMeta(
    val title: String,
    val items: Array<out Any>,
    val playlist: PlaylistSimplified? = null,
)

class FetchMeta(var offset: Int = 0, var limit: Int = 50, var total: Int = 0, var hasMore: Boolean = true)

class SpotifyPlaylistsPanel(private val parent: SpotifyToolWindowPanel, private val spotifyToolWindowStatusUpdater: SpotifyToolWindowStatusUpdater) : JPanel() {

    private var isLoading: Boolean = false
    private val playlistFetchMeta: FetchMeta = FetchMeta(10)
    private val playlistTrackFetchMeta: FetchMeta = FetchMeta(0, 50)

    private val contentPanel = JPanel()
    
    init {
        isVisible = false
        layout = BorderLayout()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
    }
    
    fun openPlaylists() {
        this.removeAll()
        playlistFetchMeta.offset = 0

        val playlistsResponse = SpotifyService.getPlaylists(0, playlistFetchMeta.limit)
        if (playlistsResponse == null) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to fetch playlists. Please check your connection.",
                "Playlists",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        playlistFetchMeta.total = playlistsResponse.total
        playlistFetchMeta.hasMore = playlistFetchMeta.total > (playlistFetchMeta.offset + playlistFetchMeta.limit)

        val playlists = playlistsResponse.items as Array<PlaylistSimplified>

        if (playlists.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "No more playlists found.",
                "Playlists",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        packItems(
            PackItemsMeta(
                "Playlists",
                playlists
            )
        )
    }

    private fun getMorePlaylists(meta: PackItemsMeta) {
        playlistFetchMeta.offset += playlistFetchMeta.limit
        playlistFetchMeta.hasMore = playlistFetchMeta.total > playlistFetchMeta.offset
        if (playlistFetchMeta.hasMore) {
            val morePlaylists = SpotifyService.getPlaylists(
                playlistFetchMeta.offset,
                playlistFetchMeta.limit
            )?.items as Array<PlaylistSimplified>?
            if (morePlaylists.isNullOrEmpty()) {
                JOptionPane.showMessageDialog(
                    this,
                    "No more playlists found.",
                    "Playlists",
                    JOptionPane.INFORMATION_MESSAGE
                )
                return
            }
            packContentPanel(
                PackItemsMeta(
                    meta.title,
                    morePlaylists
                )
            )
        }
    }

    private fun openPlaylistSongs(playlist: PlaylistSimplified) {
        this.removeAll()
        playlistTrackFetchMeta.offset = 0

        val songsForPlaylistResponse = SpotifyService.getSongsForPlaylist(playlist.id, 0, playlistTrackFetchMeta.limit)

        if (songsForPlaylistResponse == null) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to fetch songs for playlist. Please check your connection.",
                "Playlists",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        playlistTrackFetchMeta.total = songsForPlaylistResponse.total
        playlistTrackFetchMeta.hasMore = playlistTrackFetchMeta.total > (playlistTrackFetchMeta.offset + playlistTrackFetchMeta.limit)

        val songsForPlaylist = songsForPlaylistResponse.items as Array<PlaylistTrack>
        if (songsForPlaylist.isEmpty()) {
            JOptionPane.showMessageDialog(
                this, 
                "No more songs found in this playlist.",
                "Playlists",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        // Filter out null tracks and cast to Track, see PlaylistTrack for details
        val tracks = songsForPlaylist.mapNotNull { it.track as? Track }.toTypedArray()
        packItems(
            PackItemsMeta(
                playlist.name,
                tracks,
                playlist
            )
        )
    }

    private fun getMoreTracks(meta: PackItemsMeta) {
        playlistTrackFetchMeta.offset += playlistTrackFetchMeta.limit
        playlistTrackFetchMeta.hasMore = playlistTrackFetchMeta.total > playlistTrackFetchMeta.offset
        if (playlistTrackFetchMeta.hasMore) {
            val moreTracks = SpotifyService.getSongsForPlaylist(
                meta.playlist!!.id,
                playlistTrackFetchMeta.offset,
                playlistTrackFetchMeta.limit
            )?.items
            if (moreTracks.isNullOrEmpty()) {
                JOptionPane.showMessageDialog(
                    this,
                    "No more songs found in this playlist.",
                    "Playlists",
                    JOptionPane.INFORMATION_MESSAGE
                )
                return
            }

            // Filter out null tracks and cast to Track, see PlaylistTrack for details
            val tracks = moreTracks.mapNotNull { it.track as? Track }.toTypedArray()
            packContentPanel(
                PackItemsMeta(
                    meta.title,
                    tracks,
                    meta.playlist
                )
            )
        }
    }

    private fun packItems (meta: PackItemsMeta) {
        // Top bar
        val topBar = JPanel(BorderLayout())
        topBar.preferredSize = Dimension(250, 32)

        val titleLabel = JLabel(meta.title)
        titleLabel.isEnabled = false
        titleLabel.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
        titleLabel.horizontalAlignment = JLabel.LEFT
        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.size2D * 1.2f)
        titleLabel.foreground = UIManager.getColor("Label.foreground")
        topBar.add(titleLabel, BorderLayout.WEST)

        val backButton = JButton("Back")
        backButton.border = BorderFactory.createEmptyBorder(0, 0, 0, 4)
        backButton.addActionListener {
            if (meta.items.isArrayOf<PlaylistSimplified>())
                parent.openCurrentTrack()
            else
                parent.openPlaylists()
        }
        topBar.add(backButton, BorderLayout.EAST)

        this.add(topBar, BorderLayout.NORTH)

        this.contentPanel.removeAll()
        packContentPanel(meta)

        val activeScrollPane = JBScrollPane(contentPanel)
        activeScrollPane.preferredSize = Dimension(300, 400)
        activeScrollPane.border = BorderFactory.createEmptyBorder()
        activeScrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        val scrollBar = activeScrollPane.verticalScrollBar
        scrollBar.addAdjustmentListener {
            val atBottom = scrollBar.value + scrollBar.visibleAmount >= scrollBar.maximum
            if (atBottom && !isLoading) {
                if (meta.items.isArrayOf<PlaylistSimplified>()) {
                    getMorePlaylists(meta)
                } else {
                    getMoreTracks(meta)
                }
            }
        }

        this.isVisible = true

        if (!this.isVisible) {
            this.isVisible = true
        }

        this.add(activeScrollPane)
        this.revalidate()
        this.repaint()
    }

    private fun packContentPanel(meta: PackItemsMeta) {
        isLoading = true
        val workers = mutableListOf<SwingWorker<BufferedImage, Void>>()
        meta.items.forEach { item: Any ->
            when(item) {
                is PlaylistSimplified -> {
                    val worker = object : SwingWorker<BufferedImage, Void>() {
                        override fun doInBackground(): BufferedImage {
                            val imageUrl = item.images.firstOrNull()?.url ?: ""
                            return ImageFactory.getImage(imageUrl)
                        }

                        override fun done() {
                            val image = get()
                            val scaledImage = image.getScaledInstance(100, 100, Image.SCALE_SMOOTH)
                            val imageIcon = ImageIcon(scaledImage)
                            // Build and add the playlist item to contentPanel (on EDT)
                            addPlaylistItem(item, imageIcon)
                            contentPanel.revalidate()
                            contentPanel.repaint()

                            if (workers.all { it.isDone }) {
                                isLoading = false
                            }
                        }
                    }
                    workers.add(worker)
                    worker.execute()
                }
                is Track -> {
                    val worker = object : SwingWorker<BufferedImage, Void>() {
                        override fun doInBackground(): BufferedImage {
                            val imageUrl = item.album.images.firstOrNull()?.url ?: ""
                            return ImageFactory.getImage(imageUrl)
                        }

                        override fun done() {
                            val image = get()
                            val scaledImage = image.getScaledInstance(75, 75, Image.SCALE_SMOOTH)
                            val imageIcon = ImageIcon(scaledImage)
                            // Build and add the playlist item to contentPanel (on EDT)
                            addTrackItem(item, meta.playlist!!.uri, imageIcon)
                            contentPanel.revalidate()
                            contentPanel.repaint()

                            if (workers.all { it.isDone }) {
                                isLoading = false
                            }
                        }
                    }
                    workers.add(worker)
                    worker.execute()
                }
                else -> {
                    println("Unsupported item type: ${item::class.java.name}")
                }
            }
        }
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun addPlaylistItem(playlist: PlaylistSimplified, imageIcon: ImageIcon? = null) {
        val playlistPanel = JPanel(BorderLayout())

        val imageSize = 100

        val imageButton = JButton(imageIcon)
        imageButton.preferredSize = Dimension(imageSize, imageSize)
        imageButton.border = BorderFactory.createEmptyBorder()
        imageButton.addActionListener {
            openPlaylistSongs(playlist)
        }

        val playlistMeta = JPanel()
        playlistMeta.layout = BoxLayout(playlistMeta, BoxLayout.Y_AXIS)
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

        playlistMeta.add(playlistTitle)
        when (playlistDescription) {
            null -> playlistMeta.add(playlistTotalSongs)
            else -> {
                playlistMeta.add(playlistDescription)
                playlistMeta.add(playlistTotalSongs)
            }
        }
        val playlistMetaSpacing = JSeparator(JSeparator.VERTICAL)
        playlistMetaSpacing.preferredSize = Dimension(imageSize + 50, 8)
        playlistMeta.add(playlistMetaSpacing)

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

    private fun addTrackItem(track: Track, contextUri: String? = null, imageIcon: ImageIcon? = null) {
        val imageSize = 75
        val trackMetaWidth = 300

        val trackPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))

        val imageButton = JButton(imageIcon)
        imageButton.preferredSize = Dimension(imageSize, imageSize)
        imageButton.border = BorderFactory.createEmptyBorder()
        imageButton.addActionListener {
            if (contextUri != null) SpotifyService.setPlayContext(contextUri)
            SpotifyService.playTrack(track.uri)
            parent.openCurrentTrack()
        }
        trackPanel.add(imageButton)

        val trackMeta = JPanel()
        trackMeta.layout = BoxLayout(trackMeta, BoxLayout.Y_AXIS)

        val trackTitle = JLabel(track.name)
        trackTitle.font = trackTitle.font.deriveFont(trackTitle.font.size2D * 1.2f)
        trackTitle.border = BorderFactory.createEmptyBorder(0, 8, 0, 8)

        val trackArtists = JLabel(track.artists.joinToString(", ") { it.name })
        trackArtists.font = trackArtists.font.deriveFont(trackArtists.font.size2D * 0.8f)
        trackArtists.border = BorderFactory.createEmptyBorder(0, 8, 0, 8)

        val trackAlbum = JLabel(track.album.name)
        trackAlbum.font = trackAlbum.font.deriveFont(trackAlbum.font.size2D * 0.8f)
        trackAlbum.border = BorderFactory.createEmptyBorder(0, 8, 0, 8)

        trackMeta.add(trackTitle)
        trackMeta.add(trackArtists)
        trackMeta.add(trackAlbum)

        trackPanel.add(trackMeta)
        trackPanel.preferredSize = Dimension(trackMetaWidth, imageSize)
        trackPanel.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
        contentPanel.add(trackPanel)
    }
}
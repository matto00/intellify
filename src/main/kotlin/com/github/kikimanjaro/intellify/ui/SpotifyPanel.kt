package com.github.kikimanjaro.intellify.ui

import com.github.kikimanjaro.intellify.services.AddRemoveCurrentTrackFromLibraryResponse
import com.github.kikimanjaro.intellify.services.CheckSongSavedInLibraryResponse
import com.github.kikimanjaro.intellify.services.SpotifyService
import com.github.kikimanjaro.intellify.services.SpotifyStatusUpdater
import com.jetbrains.rd.swing.escPressedSource
import se.michaelthelin.spotify.model_objects.specification.Paging
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack
import se.michaelthelin.spotify.model_objects.specification.Track
import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.plaf.basic.BasicSliderUI


class SpotifyPanel(val spotifyStatusUpdater: SpotifyStatusUpdater) : JPanel(BorderLayout()) {
    val customWidth = 200
    val customHeight = 200

    private val playPauseButton: JButton
    private val prevButton: JButton
    private val nextButton: JButton
    private val addRemoveFromLibraryButton: JButton

    private val artistNameLabel: JLabel
    private val songNameLabel: JLabel
    private val imageIcon: ImageIcon
    private val imageLabel: JLabel
    private val titlePanel: JPanel

    private val slider: JSlider

    init {
        // Title Panel
        artistNameLabel = JLabel(SpotifyService.artist, JLabel.CENTER)
        artistNameLabel.setFont(artistNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        songNameLabel = JLabel(SpotifyService.song, JLabel.CENTER)

        titlePanel = JPanel(BorderLayout())
        titlePanel.add(artistNameLabel, BorderLayout.NORTH)
        titlePanel.add(songNameLabel, BorderLayout.SOUTH)

        // Image Panel
        val image: BufferedImage = ImageIO.read(URL(SpotifyService.imageUrl))
        val scaledImage = image.getScaledInstance(customWidth, customHeight, Image.SCALE_SMOOTH)

        imageIcon = ImageIcon(scaledImage)
        imageLabel = JLabel(imageIcon)

        // Button Panel
        val buttonPanel = JPanel()
        buttonPanel.layout = BorderLayout()
        buttonPanel.isOpaque = false

        playPauseButton = JButton()
        if (SpotifyService.isPlaying) {
            playPauseButton.icon = spotifyStatusUpdater.pauseIcon
        } else {
            playPauseButton.icon = spotifyStatusUpdater.playIcon
        }
        playPauseButton.addActionListener {
            if (SpotifyService.isPlaying) {
                SpotifyService.pauseTrack()
            } else {
                SpotifyService.startTrack()
            }
            update()
        }
        prevButton = JButton(spotifyStatusUpdater.prevIcon)
        prevButton.addActionListener {
            SpotifyService.prevTrack()
            update()
        }
        nextButton = JButton(spotifyStatusUpdater.nextIcon)
        nextButton.addActionListener {
            SpotifyService.nextTrack()
            update()
        }

        slider = object  : JSlider(0, SpotifyService.durationMs){
            override fun updateUI() {
                setUI(CustomSliderUI(this));
            }
        }
        slider.setBorder(BorderFactory.createEmptyBorder(6,0,4,0));
        slider.value = SpotifyService.progressInMs
        slider.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                val newVal = slider.value
                SpotifyService.setProgress(newVal)
                slider.value = newVal
                update()
                slider.value = newVal
            }
        })

        // Add the buttons to the button panel
        buttonPanel.add(prevButton, BorderLayout.WEST)
        buttonPanel.add(playPauseButton, BorderLayout.CENTER)
        buttonPanel.add(nextButton, BorderLayout.EAST)

        val playlistPanel = JPanel()
        playlistPanel.layout = BorderLayout()
        playlistPanel.isOpaque = false

        val playlistsButton = JButton(spotifyStatusUpdater.playlistsIcon)
        playlistsButton.addActionListener {
            openPlaylists()
            update()
        }

        addRemoveFromLibraryButton = JButton(spotifyStatusUpdater.addIcon)
        addRemoveFromLibraryButton.addActionListener {
            addRemoveCurrentTrackToLikedSongs()
            update()
        }

        // Bottom Panel
        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.add(slider, BorderLayout.NORTH)
        bottomPanel.add(buttonPanel, BorderLayout.CENTER)
        bottomPanel.add(playlistsButton, BorderLayout.WEST)
        bottomPanel.add(addRemoveFromLibraryButton, BorderLayout.EAST)

        // Full layout
        add(titlePanel, BorderLayout.NORTH)
        add(imageLabel, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)
    }

    private fun openPlaylists() {
        val playlists = SpotifyService.getPlaylists()?.items as Array<PlaylistSimplified>?

        println(playlists)

        if (playlists.isNullOrEmpty()) {
            JOptionPane.showMessageDialog(this, "No playlists found.", "Playlists", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val playlistMenu = JPopupMenu()
        playlists.forEach { playlist: PlaylistSimplified ->
            val menuItem = JMenuItem(playlist.name)
            menuItem.addActionListener {
                 openPlaylist(playlistMenu, playlist.id)
            }
            playlistMenu.add(menuItem)
        }

        playlistMenu.show(this, 0, 0)
    }

    private fun addRemoveCurrentTrackToLikedSongs() {
        val addRemoveResponse = SpotifyService.addRemoveCurrentTrackToLikedSongs()
        when (addRemoveResponse) {
            AddRemoveCurrentTrackFromLibraryResponse.CURRENT_TRACK_ADDED_TO_LIBRARY ->
                addRemoveFromLibraryButton.icon = spotifyStatusUpdater.greenCheckIcon

            AddRemoveCurrentTrackFromLibraryResponse.CURRENT_TRACK_REMOVED_FROM_LIBRARY ->
                addRemoveFromLibraryButton.icon = spotifyStatusUpdater.addIcon

            AddRemoveCurrentTrackFromLibraryResponse.REQUEST_FAILED ->
                println("Error: Failed to add / remove song from library")
        }
    }

    private fun openPlaylist(playlistMenu: JPopupMenu, playlistId: String) {
        val songsForPlaylist = SpotifyService.getSongsForPlaylist(playlistId)?.items
        if (songsForPlaylist.isNullOrEmpty()) {
            JOptionPane.showMessageDialog(this, "No songs found in this playlist.", "Playlists", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val playlistSongMenu = JPopupMenu()
        songsForPlaylist.forEach { playlistTrack: PlaylistTrack ->
            val track = playlistTrack.track
            val menuItem = JMenuItem(track.name)
            menuItem.addActionListener {
                playTrack(playlistSongMenu, track.uri)
            }
            playlistSongMenu.add(menuItem)
        }

        if (playlistMenu.isVisible)
            playlistMenu.setVisible(false)
    }

    fun playTrack(playlistSongMenu: JPopupMenu, trackUri: String) {
        SpotifyService.playTrack(trackUri)

        if (playlistSongMenu.isVisible)
            playlistSongMenu.setVisible(false)
    }

    fun update() {
        artistNameLabel.text = SpotifyService.artist
        songNameLabel.text = SpotifyService.song
        titlePanel.repaint()

        val image: BufferedImage = ImageIO.read(URL(SpotifyService.imageUrl))
        val scaledImage = image.getScaledInstance(customWidth, customHeight, Image.SCALE_SMOOTH)

        imageIcon.image = scaledImage
        imageLabel.repaint()

        if (SpotifyService.isPlaying) {
            playPauseButton.icon = spotifyStatusUpdater.pauseIcon
        } else {
            playPauseButton.icon = spotifyStatusUpdater.playIcon
        }

        slider.value = SpotifyService.progressInMs

        if (SpotifyService.trackId != "" && SpotifyService.lastTrackCheckedInLibrary != SpotifyService.trackId) {
            when (SpotifyService.checkCurrentTrackAlreadySaved())  {
                CheckSongSavedInLibraryResponse.SONG_NOT_SAVED ->
                    addRemoveFromLibraryButton.icon = spotifyStatusUpdater.addIcon

                CheckSongSavedInLibraryResponse.SONG_SAVED ->
                    addRemoveFromLibraryButton.icon = spotifyStatusUpdater.greenCheckIcon

                else -> println("Error: Failed to check if song is in library...")
            }
        }
    }
}

private class CustomSliderUI(b: JSlider?) : BasicSliderUI(b) {
    private val trackShape = RoundRectangle2D.Float()
    override fun calculateTrackRect() {
        super.calculateTrackRect()
        if (isHorizontal) {
            trackRect.y = trackRect.y + (trackRect.height - TRACK_HEIGHT) / 2
            trackRect.height = TRACK_HEIGHT
        } else {
            trackRect.x = trackRect.x + (trackRect.width - TRACK_WIDTH) / 2
            trackRect.width = TRACK_WIDTH
        }
        trackShape.setRoundRect(
            trackRect.x.toFloat(),
            trackRect.y.toFloat(),
            trackRect.width.toFloat(),
            trackRect.height.toFloat(),
            TRACK_ARC.toFloat(),
            TRACK_ARC.toFloat()
        )
    }

    override fun calculateThumbLocation() {
        super.calculateThumbLocation()
        if (isHorizontal) {
            thumbRect.y = trackRect.y + (trackRect.height - thumbRect.height) / 2
        } else {
            thumbRect.x = trackRect.x + (trackRect.width - thumbRect.width) / 2
        }
    }

    override fun getThumbSize(): Dimension {
        return THUMB_SIZE
    }

    private val isHorizontal: Boolean
        get() = slider.orientation == JSlider.HORIZONTAL

    override fun paint(g: Graphics, c: JComponent) {
        (g as Graphics2D).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        super.paint(g, c)
    }

    override fun paintTrack(g: Graphics) {
        val g2 = g as Graphics2D
        val clip: Shape = g2.clip
        val horizontal = isHorizontal
        var inverted = slider.inverted

        // Paint shadow.
        g2.color = Color(170, 170, 170)
        g2.fill(trackShape)

        // Paint track background.
        g2.color = Color(200, 200, 200)
        g2.clip = trackShape
        trackShape.y += 1f
        g2.fill(trackShape)
        trackShape.y = trackRect.y.toFloat()
        g2.clip = clip

        // Paint selected track.
        if (horizontal) {
            val ltr = slider.componentOrientation.isLeftToRight
            if (ltr) inverted = !inverted
            val thumbPos = thumbRect.x + thumbRect.width / 2
            if (inverted) {
                g2.clipRect(0, 0, thumbPos, slider.height)
            } else {
                g2.clipRect(thumbPos, 0, slider.width - thumbPos, slider.height)
            }
        } else {
            val thumbPos = thumbRect.y + thumbRect.height / 2
            if (inverted) {
                g2.clipRect(0, 0, slider.height, thumbPos)
            } else {
                g2.clipRect(0, thumbPos, slider.width, slider.height - thumbPos)
            }
        }
        g2.color = Color(29,184,84)
        g2.fill(trackShape)
        g2.clip = clip
    }

    override fun paintThumb(g: Graphics) {
        g.color = Color.WHITE
        g.fillOval(thumbRect.x + thumbRect.width / 4, thumbRect.y + thumbRect.height / 4 , thumbRect.width /2, thumbRect.height /2)
    }

    override fun paintFocus(g: Graphics) {}

    companion object {
        private const val TRACK_HEIGHT = 8
        private const val TRACK_WIDTH = 8
        private const val TRACK_ARC = 10
        private val THUMB_SIZE: Dimension = Dimension(20, 20)
    }
}
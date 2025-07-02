package com.github.kikimanjaro.intellify.ui

import com.github.kikimanjaro.intellify.services.AddRemoveCurrentTrackFromLibraryResponse
import com.github.kikimanjaro.intellify.services.CheckSongSavedInLibraryResponse
import com.github.kikimanjaro.intellify.services.SpotifyService
import com.github.kikimanjaro.intellify.services.SpotifyToolWindowStatusUpdater
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.URI
import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.plaf.basic.BasicSliderUI

class SpotifyCurrentTrackPanel(private val parent: SpotifyToolWindowPanel, private val spotifyToolWindowStatusUpdater: SpotifyToolWindowStatusUpdater): JPanel(BorderLayout()) {
    private val customWidth = 400
    private val customHeight = 400

    private lateinit var playPauseButton: JButton
    private lateinit var prevButton: JButton
    private lateinit var nextButton: JButton
    private lateinit var shuffleToggleButton: JButton
    private lateinit var addRemoveFromLibraryButton: JButton

    private lateinit var artistNameLabel: JLabel
    private lateinit var songNameLabel: JLabel
    private lateinit var imageIcon: ImageIcon
    private lateinit var imageLabel: JLabel
    private lateinit var titlePanel: JPanel

    private lateinit var slider: JSlider

    init {
        val topNavPanel = createTopNavPanel()
        val songPanel = createSongPanel()
        val bottomPanel = createBottomPanel()

        // Full layout
        add(topNavPanel, BorderLayout.NORTH)
        add(songPanel, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)
    }

    private fun createTopNavPanel(): JPanel {
        val topNavPanel = JPanel(BorderLayout())
        val playlistsButton = JButton(spotifyToolWindowStatusUpdater.playlistsIcon)
        playlistsButton.addActionListener {
            parent.openPlaylists()
            update()
        }
        topNavPanel.add(playlistsButton, BorderLayout.WEST)
        return topNavPanel
    }

    private fun createSongPanel(): JPanel {
        val songPanel = JPanel(BorderLayout())

        // Title Panel
        artistNameLabel = JLabel(SpotifyService.artist, JLabel.CENTER)
        songNameLabel = JLabel(SpotifyService.song, JLabel.CENTER)
        songNameLabel.setFont(songNameLabel.getFont().deriveFont(Font.BOLD, 14f));

        titlePanel = JPanel(BorderLayout())
        titlePanel.add(songNameLabel, BorderLayout.CENTER)
        titlePanel.add(artistNameLabel, BorderLayout.SOUTH)

        // Image Panel
        val image: BufferedImage = try {
            if (SpotifyService.imageUrl.isNotEmpty()) {
                ImageIO.read(URL(SpotifyService.imageUrl))
            } else {
                // Use a placeholder image if URL is empty
                UIUtil.createImage(customWidth, customHeight, BufferedImage.TYPE_INT_ARGB)
            }
        } catch (e: Exception) {
            // Handle invalid URL or loading error
            UIUtil.createImage(customWidth, customHeight, BufferedImage.TYPE_INT_ARGB)
        }
        val scaledImage = image.getScaledInstance(customWidth, customHeight, Image.SCALE_SMOOTH)

        imageIcon = ImageIcon(scaledImage)
        imageLabel = JLabel(imageIcon)

        songPanel.add(titlePanel, BorderLayout.NORTH)
        songPanel.add(imageLabel, BorderLayout.CENTER)

        return songPanel
    }

    private fun createButtonPanel(): JPanel {
        val buttonPanel = JPanel()
        buttonPanel.layout = BorderLayout()
        buttonPanel.isOpaque = false

        playPauseButton = JButton()
        if (SpotifyService.isPlaying) {
            playPauseButton.icon = spotifyToolWindowStatusUpdater.pauseIcon
        } else {
            playPauseButton.icon = spotifyToolWindowStatusUpdater.playIcon
        }
        playPauseButton.addActionListener {
            if (SpotifyService.isPlaying) {
                SpotifyService.pauseTrack()
            } else {
                SpotifyService.startTrack()
            }
            update()
        }
        prevButton = JButton(spotifyToolWindowStatusUpdater.prevIcon)
        prevButton.addActionListener {
            SpotifyService.prevTrack()
            update()
        }
        nextButton = JButton(spotifyToolWindowStatusUpdater.nextIcon)
        nextButton.addActionListener {
            SpotifyService.nextTrack()
            update()
        }


        // Add the buttons to the button panel
        buttonPanel.add(prevButton, BorderLayout.WEST)
        buttonPanel.add(playPauseButton, BorderLayout.CENTER)
        buttonPanel.add(nextButton, BorderLayout.EAST)

        return buttonPanel
    }

    private fun createBottomPanel(): JPanel {
        val buttonPanel = createButtonPanel()

        slider = object : JSlider(0, SpotifyService.durationMs) {
            override fun updateUI() {
                setUI(CustomToolWindowSliderUI(this));
            }
        }
        slider.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
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

        shuffleToggleButton = JButton(spotifyToolWindowStatusUpdater.shuffleOff)
        shuffleToggleButton.addActionListener {
            SpotifyService.toggleShuffle()
            when (SpotifyService.isShuffling) {
                true -> shuffleToggleButton.icon = spotifyToolWindowStatusUpdater.shuffleOn
                false -> shuffleToggleButton.icon = spotifyToolWindowStatusUpdater.shuffleOff
            }
            update()
        }

        addRemoveFromLibraryButton = JButton(spotifyToolWindowStatusUpdater.addIcon)
        addRemoveFromLibraryButton.addActionListener {
            addRemoveCurrentTrackToLikedSongs()
            update()
        }

        // Bottom Panel
        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.add(slider, BorderLayout.NORTH)
        bottomPanel.add(buttonPanel, BorderLayout.CENTER)
        bottomPanel.add(shuffleToggleButton, BorderLayout.WEST)
        bottomPanel.add(addRemoveFromLibraryButton, BorderLayout.EAST)

        return bottomPanel
    }

    private fun addRemoveCurrentTrackToLikedSongs() {
        val addRemoveResponse = SpotifyService.addRemoveCurrentTrackToLikedSongs()
        when (addRemoveResponse) {
            AddRemoveCurrentTrackFromLibraryResponse.CURRENT_TRACK_ADDED_TO_LIBRARY ->
                addRemoveFromLibraryButton.icon = spotifyToolWindowStatusUpdater.greenCheckIcon

            AddRemoveCurrentTrackFromLibraryResponse.CURRENT_TRACK_REMOVED_FROM_LIBRARY ->
                addRemoveFromLibraryButton.icon = spotifyToolWindowStatusUpdater.addIcon

            AddRemoveCurrentTrackFromLibraryResponse.REQUEST_FAILED ->
                println("Error: Failed to add / remove song from library")
        }
    }

    fun update() {
        artistNameLabel.text = SpotifyService.artist
        songNameLabel.text = SpotifyService.song
        titlePanel.repaint()

        val image: BufferedImage = try {
            if (SpotifyService.imageUrl.isNotEmpty()) {
                ImageIO.read(URL(SpotifyService.imageUrl))
            } else {
                // Use a placeholder image if URL is empty
                UIUtil.createImage(customWidth, customHeight, BufferedImage.TYPE_INT_ARGB)
            }
        } catch (e: Exception) {
            // Handle invalid URL or loading error
            UIUtil.createImage(customWidth, customHeight, BufferedImage.TYPE_INT_ARGB)
        }
        val scaledImage = image.getScaledInstance(parent.maxWidth(), parent.maxHeight(), Image.SCALE_SMOOTH)

        imageIcon.image = scaledImage
        imageLabel.repaint()

        if (SpotifyService.isPlaying) {
            playPauseButton.icon = spotifyToolWindowStatusUpdater.pauseIcon
        } else {
            playPauseButton.icon = spotifyToolWindowStatusUpdater.playIcon
        }

        slider.value = SpotifyService.progressInMs
        slider.maximum = SpotifyService.durationMs
        slider.value = SpotifyService.progressInMs
        slider.repaint()

        if (SpotifyService.trackId != "" && SpotifyService.lastTrackCheckedInLibrary != SpotifyService.trackId) {
            when (SpotifyService.checkCurrentTrackAlreadySaved())  {
                CheckSongSavedInLibraryResponse.SONG_NOT_SAVED ->
                    addRemoveFromLibraryButton.icon = spotifyToolWindowStatusUpdater.addIcon

                CheckSongSavedInLibraryResponse.SONG_SAVED ->
                    addRemoveFromLibraryButton.icon = spotifyToolWindowStatusUpdater.greenCheckIcon

                else -> println("Error: Failed to check if song is in library...")
            }
        }
    }
}

private class CustomToolWindowSliderUI(b: JSlider?) : BasicSliderUI(b) {
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

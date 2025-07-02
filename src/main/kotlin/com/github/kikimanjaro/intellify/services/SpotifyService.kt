package com.github.kikimanjaro.intellify.services

import com.github.kikimanjaro.intellify.services.Secret.Companion.clientId
import com.github.kikimanjaro.intellify.services.Secret.Companion.clientSecret
import com.github.kikimanjaro.intellify.ui.SpotifyToolWindowPanel
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.BrowserUtil
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.remoteServer.util.CloudConfigurationUtil.createCredentialAttributes
import se.michaelthelin.spotify.SpotifyApi
import se.michaelthelin.spotify.SpotifyHttpManager
import se.michaelthelin.spotify.enums.AuthorizationScope
import se.michaelthelin.spotify.exceptions.detailed.UnauthorizedException
import se.michaelthelin.spotify.model_objects.specification.Paging
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack
import se.michaelthelin.spotify.model_objects.specification.Track
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest
import se.michaelthelin.spotify.requests.data.player.StartResumeUsersPlaybackRequest
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import kotlin.concurrent.thread

enum class CheckSongSavedInLibraryResponse {
    REQUEST_FAILED,
    SONG_SAVED,
    SONG_NOT_SAVED
}

enum class AddRemoveCurrentTrackFromLibraryResponse {
    REQUEST_FAILED,
    CURRENT_TRACK_ADDED_TO_LIBRARY,
    CURRENT_TRACK_REMOVED_FROM_LIBRARY
}

data class RequestKey(val function: Any, val args: Any?)

data class RequestMeta(val response: Any?, val lastCalled: Long)

object RequestCache {
    private val cache = mutableMapOf<Int, RequestMeta>()

    fun put(key: RequestKey, meta: RequestMeta) {
        cache[key.hashCode()] = meta
    }

    fun get(key: RequestKey): RequestMeta? {
        return cache[key.hashCode()]
    }
}

object SpotifyService {
    var currentPanel: SpotifyToolWindowPanel? = null
    private const val codeServiceName = "Intellify-code"
    private const val accesServiceName = "Intellify-acces"
    private const val refreshServiceName = "Intellify-refresh"
    private val redirectUri =
        SpotifyHttpManager.makeUri("http://127.0.0.1:30498/callback")
    private val spotifyApi = SpotifyApi.Builder()
        .setClientId(clientId)
        .setClientSecret(clientSecret)
        .setRedirectUri(redirectUri)
        .setAccessToken(retrieveAccessToken())
        .setRefreshToken(retrieveRefreshToken())
        .build()

    private val authorizationCodeUriRqst = AuthorizationCodeUriRequest.Builder().client_id(clientId)
        .redirect_uri(SpotifyHttpManager.makeUri("http://127.0.0.1:30498/callback")).show_dialog(true)
        .response_type("code").scope(
            AuthorizationScope.USER_LIBRARY_READ,
            AuthorizationScope.APP_REMOTE_CONTROL,
            AuthorizationScope.USER_READ_CURRENTLY_PLAYING,
            AuthorizationScope.USER_MODIFY_PLAYBACK_STATE,
            AuthorizationScope.USER_TOP_READ,
            AuthorizationScope.USER_LIBRARY_MODIFY,
            AuthorizationScope.PLAYLIST_READ_PRIVATE,
            AuthorizationScope.PLAYLIST_READ_COLLABORATIVE,
            AuthorizationScope.PLAYLIST_MODIFY_PRIVATE,
            AuthorizationScope.PLAYLIST_MODIFY_PUBLIC,
        ).build()
    var code = retrieveCode()

    private val codeServiceCredentialAttributes: CredentialAttributes?
        get() = createCredentialAttributes(codeServiceName, "user")

    private val accesServiceCredentialAttributes: CredentialAttributes?
        get() = createCredentialAttributes(accesServiceName, "user")

    private val refreshServiceCredentialAttributes: CredentialAttributes?
        get() = createCredentialAttributes(refreshServiceName, "user")

    var trackId = ""
    var trackUri = ""
    var title = ""
    var artist = ""
    var song = ""
    var imageUrl = ""
    var lastTrackCheckedInLibrary = ""

    var durationMs = 0
    var progressInMs = 0

    var isPlaying = false
    var isShuffling = false

    fun refreshAccessTokenWithRefreshToken() {
        try {
            if (spotifyApi.refreshToken != null && spotifyApi.refreshToken.isNotEmpty()) {
                val authorizationCodeRefreshRequest = spotifyApi.authorizationCodeRefresh().build()
                val authorizationCodeCredentialsFuture = authorizationCodeRefreshRequest.executeAsync()

                // Thread free to do other tasks...

                // Example Only. Never block in production code.
                val authorizationCodeCredentials = authorizationCodeCredentialsFuture.join()

                // Set access token for further "spotifyApi" object usage
                spotifyApi.accessToken = authorizationCodeCredentials.accessToken
                saveAccessToken(authorizationCodeCredentials.accessToken)
                println("Expires in: " + authorizationCodeCredentials.expiresIn)
            } else if (spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                getTokensFromCode()
            } else {
                getCodeFromBrowser()
            }
        } catch (e: CompletionException) {
            println("Error: " + e.cause!!.message)
            getCodeFromBrowser()
        } catch (e: CancellationException) {
            println("Async operation cancelled.")
        } catch (e: Exception) {
            println("Error: " + e.message)
        }
    }

    fun getTokensFromCode() {
        try {
            if (code.isNotEmpty()) {
                val authorizationCodeCredentialsFuture = spotifyApi.authorizationCode(code).build().executeAsync()
                val authorizationCodeCredentials = authorizationCodeCredentialsFuture.join()

                spotifyApi.accessToken = authorizationCodeCredentials.accessToken
                saveAccessToken(authorizationCodeCredentials.accessToken)
                spotifyApi.refreshToken = authorizationCodeCredentials.refreshToken
                saveRefreshToken(authorizationCodeCredentials.refreshToken)
//                println("Expires in: " + authorizationCodeCredentials.expiresIn)
            } else {
                getCodeFromBrowser()
            }
        } catch (e: CompletionException) {
            println("Error: " + e.cause!!.message)
            refreshAccessTokenWithRefreshToken()
        } catch (e: CancellationException) {
            println("Async operation cancelled.")
        } catch (e: Exception) {
            println("Error: " + e.message)
        }
    }

    fun getCodeFromBrowser() {
        try {
            val uriFuture = authorizationCodeUriRqst.executeAsync()

            val uri = uriFuture.join()
//            println("URI: $uri")
            openServer()
            BrowserUtil.browse(uri) //TODO: use embeded browser
        } catch (e: CompletionException) {
            println("Error: " + e.cause!!.message)
        } catch (e: CancellationException) {
            println("Async operation cancelled.")
        } catch (e: Exception) {
            println("Error: " + e.message)
        }
    }

    fun getInformationAboutUsersCurrentPlayingTrack() {
        try {
            if (code.isNotEmpty() && spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                val currentlyPlayingContext = spotifyApi.usersCurrentlyPlayingTrack.build().execute()
                if (currentlyPlayingContext.item is Track) {
                    isPlaying = currentlyPlayingContext.is_playing
                    val track = currentlyPlayingContext.item as Track
                    trackId = track.id
                    trackUri = track.uri
                    song = track.name
                    artist = track.artists[0].name
                    title = track.name
                    title += " - " + track.artists[0].name
                    durationMs = track.durationMs
                    progressInMs = currentlyPlayingContext.progress_ms
                    if (track.album != null && track.album.images.isNotEmpty()) {
                        imageUrl = track.album.images[0].url
                    } else {
                        imageUrl = ""
                    }
                }
            } else {
                getTokensFromCode()
            }
        } catch (e: CompletionException) {
            println("Error: " + e.cause!!.message)
            refreshAccessTokenWithRefreshToken()
        } catch (e: CancellationException) {
            println("Async operation cancelled.")
        } catch (e: UnauthorizedException) {
            println("Unauthorized.")
            refreshAccessTokenWithRefreshToken()
        } catch (e: Exception) {
            println("Error: " + e.message)
        }
    }

    private fun <T, R> callLambdaAndUpdateCache(fn: (T?) -> R?, args: T?): R? {
        val response = if (args != null) {
            fn(args)
        } else {
            fn(null)
        }

        RequestCache.put(
            RequestKey(fn.toString(), args),
            RequestMeta(
                response = response,
                lastCalled = System.currentTimeMillis()
            )
        )
        return response
    }

    private inline fun <T, reified R> syncApiRequestLambda(
        noinline fn: (T?) -> R?,
        args: T?,
        debounceMillis: Long = 250L
    ): R? {
        return try {
            if (code.isNotEmpty() && spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                val requestKey = RequestKey(fn.toString(), args)
                return when (RequestCache.get(requestKey)) {
                    // If the request is not cached, execute it and cache the response
                    null -> {
                        callLambdaAndUpdateCache(fn, args)
                    }
                    else -> {
                        // Check if the request is debounced
                        val meta = RequestCache.get(requestKey) as RequestMeta
                        if (System.currentTimeMillis() - meta.lastCalled < debounceMillis) {
                            meta.response as R?
                        } else {
                            // Update cache
                            callLambdaAndUpdateCache(fn, args)
                        }
                    }
                }
            } else {
                null
            }
        } catch (e: CompletionException) {
            println("Error: " + e.cause!!.message)
            null
        } catch (e: CancellationException) {
            println("Async operation cancelled.")
            null
        } catch (e: Exception) {
            println("Error: " + e.message)
            null
        }
    }

    /**
     * Starts or resumes playback for the given URI. (Not currently supported by Spotify API)
     * @param uri The URI of the context to play. Valid URIs include playlists, albums, and artists.
     * @return A [StartResumeUsersPlaybackRequest.Builder] to build and execute the request.
     */
    private fun startResumePlaybackForUri(uri: String): StartResumeUsersPlaybackRequest.Builder {
        return StartResumeUsersPlaybackRequest.Builder(spotifyApi.accessToken)
            .setDefaults(spotifyApi.httpManager, spotifyApi.scheme, spotifyApi.host, spotifyApi.port)
            .context_uri(uri)
    }

    fun playTrack(selectedTrackUri: String) {
        // Workaround for the fact that Spotify API does not support playing a specific track by URI
        syncApiRequestLambda(
            { uri ->  spotifyApi.addItemToUsersPlaybackQueue(uri!!).build().execute() },
            selectedTrackUri
        )
        nextTrack()
    }

    fun pauseTrack() {
        syncApiRequestLambda(
            { _ -> spotifyApi.pauseUsersPlayback().build().execute() },
            null
        )
    }

    fun startTrack() {
        syncApiRequestLambda(
            { _ -> spotifyApi.startResumeUsersPlayback().build().execute() },
            null
        )
    }

    fun nextTrack() {
        syncApiRequestLambda(
            { _ -> spotifyApi.skipUsersPlaybackToNextTrack().build().execute() },
            null
        )
    }

    fun prevTrack() {
        syncApiRequestLambda(
            { _ -> spotifyApi.skipUsersPlaybackToPreviousTrack().build().execute() },
            null
        )
    }

    fun setProgress(progressInMsToGoTo: Int) {
        syncApiRequestLambda(
            { progress -> spotifyApi.seekToPositionInCurrentlyPlayingTrack(progress!!).build().execute()},
            progressInMsToGoTo
        )
    }

    fun toggleShuffle() {
        isShuffling = !isShuffling
        syncApiRequestLambda(
            { shuffle -> spotifyApi.toggleShuffleForUsersPlayback(shuffle!!).build().execute() },
            isShuffling
        )
    }

    fun addRemoveCurrentTrackToLikedSongs(): AddRemoveCurrentTrackFromLibraryResponse {
        lastTrackCheckedInLibrary = trackId

        val checkResponse = checkCurrentTrackAlreadySaved()
        when (checkResponse) {
            CheckSongSavedInLibraryResponse.REQUEST_FAILED -> {
                println("Error: Failed to check if song is saved.")
                return AddRemoveCurrentTrackFromLibraryResponse.REQUEST_FAILED
            }
            CheckSongSavedInLibraryResponse.SONG_SAVED -> {
                syncApiRequestLambda(
                    { id -> spotifyApi.removeUsersSavedTracks(id).build().execute() },
                    trackId
                )
                return AddRemoveCurrentTrackFromLibraryResponse.CURRENT_TRACK_REMOVED_FROM_LIBRARY
            }
            CheckSongSavedInLibraryResponse.SONG_NOT_SAVED -> {
                syncApiRequestLambda(
                    { id -> spotifyApi.saveTracksForUser(id).build().execute() },
                    trackId
                )
                return AddRemoveCurrentTrackFromLibraryResponse.CURRENT_TRACK_ADDED_TO_LIBRARY
            }
        }
    }

    fun checkCurrentTrackAlreadySaved(): CheckSongSavedInLibraryResponse {
        val isSaved = syncApiRequestLambda(
            { id -> spotifyApi.checkUsersSavedTracks(id).build().execute() },
            trackId
        )?.get(0)
        if (isSaved == true) return CheckSongSavedInLibraryResponse.SONG_SAVED  // isSaved == true since it may be null
        return CheckSongSavedInLibraryResponse.SONG_NOT_SAVED
    }

    fun setPlayContext(contextUri: String) {
        syncApiRequestLambda(
            { uri -> startResumePlaybackForUri(uri!!).build().execute() },
            contextUri
        )
    }

    fun playPlaylist(selectedPlaylistUri: String) {
        setPlayContext(selectedPlaylistUri)
    }

    fun getPlaylists(): Paging<PlaylistSimplified>? {
        return syncApiRequestLambda(
            { _ -> spotifyApi.listOfCurrentUsersPlaylists.build().execute() },
            null,
            10000L // Debounce for 10 seconds to avoid too many requests
        )
    }

    fun getSongsForPlaylist(playlistId: String): Paging<PlaylistTrack>? {
        return syncApiRequestLambda(
            { id -> spotifyApi.getPlaylistsItems(id).build().execute() },
            playlistId,
            10000L // Debounce for 10 seconds to avoid too many requests
        )
    }

    fun addToPlaylist(playlistId: String) {
        syncApiRequestLambda(
            { ids -> spotifyApi.addItemsToPlaylist(ids!![0], arrayOf(ids[1])).build().execute() },
            arrayOf(playlistId, trackId)
        )
    }

    fun openServer() {
        val server = ServerSocket(30498)
//        println("Server is running on port ${server.localPort}")

        var stop = false;
        thread {
            while (!stop) {
                try {
                    val socket = server.accept()
                    println("Client connected")

                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    val reader = BufferedReader(InputStreamReader(input))
                    val writer = BufferedWriter(OutputStreamWriter(output))
                    val line = reader.readLine()
                    writer.write("HTTP/1.1 200 OK\r\n") //TODO: make this beautiful, maybe with an image
                    writer.write(
                        "<!DOCTYPE html>\n" +
                                "<html lang=\"en\">\n" +
                                "<head>\n" +
                                "    <meta charset=\"UTF-8\">\n" +
                                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                                "    <title>My html page</title>\n" +
                                "</head>\n" +
                                "<body>\n" +
                                "\n" +
                                "    <p>\n" +
                                "        Thank you for using Intellify.\n" +
                                "    </p>\n" +
                                "    \n" +
                                "    <p>\n" +
                                "         You can close this, it's useless now :p\n" +
                                "    </p>\n" +
                                "    \n" +
                                "    <p>\n" +
                                "         KikiManjaro\n" +
                                "    </p>\n" +
                                "    \n" +
                                "</body>\n" +
                                "</html>"
                    )
                    writer.flush()
                    code = line.split("=")[1].split(" ")[0]
                    if (code.isNotEmpty()) {
                        saveCode(code)
                        getTokensFromCode()
                        stop = true
                        Thread.sleep(10000)
                        socket.close()
                    }
                } catch (e: Exception) {
                    println("Socket error: " + e.message)
                    stop = true
                }
            }
        }
    }

    private fun saveCode(newCode: String) {
        val credentialAttributes: CredentialAttributes? =
            codeServiceCredentialAttributes // see previous sample
        val credentials = Credentials(codeServiceName, newCode)
        PasswordSafe.instance.set(credentialAttributes!!, credentials)
    }

    private fun retrieveCode(): String {
        val credentialAttributes = codeServiceCredentialAttributes
        return PasswordSafe.instance.getPassword(credentialAttributes!!) ?: ""
    }

    private fun saveAccessToken(token: String) {
        val credentialAttributes: CredentialAttributes? =
            accesServiceCredentialAttributes // see previous sample
        val credentials = Credentials(accesServiceName, token)
        PasswordSafe.instance.set(credentialAttributes!!, credentials)
    }

    private fun retrieveAccessToken(): String? {
        val credentialAttributes = accesServiceCredentialAttributes
        return PasswordSafe.instance.getPassword(credentialAttributes!!)
    }

    private fun saveRefreshToken(token: String) {
        val credentialAttributes: CredentialAttributes? =
            refreshServiceCredentialAttributes // see previous sample
        val credentials = Credentials(refreshServiceName, token)
        PasswordSafe.instance.set(credentialAttributes!!, credentials)
    }

    private fun retrieveRefreshToken(): String? {
        val credentialAttributes = refreshServiceCredentialAttributes
        return PasswordSafe.instance.getPassword(credentialAttributes!!)
    }
}
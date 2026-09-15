package com.retro.grooveplayer.data

import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import okio.Buffer

/**
 * Identifies a track's cover art.
 *
 * [albumArtUri] is MediaStore's album-art entry, which is missing for most sideloaded
 * files - the library was showing placeholder tiles for every track because of it.
 * [songUri] points at the audio file itself, whose embedded artwork is far more
 * reliable, so it is tried first.
 */
data class AudioCover(
    val songUri: String,
    val albumArtUri: String?
)

/** Reads cover art out of the audio file's tags, falling back to MediaStore. */
class AudioCoverFetcher(
    private val data: AudioCover,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        embeddedArtwork()?.let { return it }
        return mediaStoreArtwork()
    }

    private fun embeddedArtwork(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(options.context, Uri.parse(data.songUri))
            val bytes = retriever.embeddedPicture ?: return null
            SourceResult(
                source = ImageSource(
                    source = Buffer().apply { write(bytes) },
                    context = options.context
                ),
                mimeType = null,
                dataSource = DataSource.DISK
            )
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Nothing useful to do if the retriever will not close.
            }
        }
    }

    private fun mediaStoreArtwork(): FetchResult? {
        val uri = data.albumArtUri ?: return null
        return try {
            val stream = options.context.contentResolver.openInputStream(Uri.parse(uri))
                ?: return null
            val buffer = Buffer().apply { readFrom(stream) }
            SourceResult(
                source = ImageSource(source = buffer, context = options.context),
                mimeType = null,
                dataSource = DataSource.DISK
            )
        } catch (e: Exception) {
            // Expected for tracks with no MediaStore art; the placeholder shows instead.
            null
        }
    }

    class Factory : Fetcher.Factory<AudioCover> {
        override fun create(data: AudioCover, options: Options, imageLoader: ImageLoader): Fetcher =
            AudioCoverFetcher(data, options)
    }
}

/** Cache key. Without it every cover would be re-decoded on each recomposition. */
class AudioCoverKeyer : Keyer<AudioCover> {
    override fun key(data: AudioCover, options: Options): String = data.songUri
}

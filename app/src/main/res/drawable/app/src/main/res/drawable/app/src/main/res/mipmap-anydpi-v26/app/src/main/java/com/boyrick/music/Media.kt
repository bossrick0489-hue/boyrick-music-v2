package com.boyrick.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.json.JSONArray
import org.json.JSONObject

data class Track(val id: Long, val title: String, val artist: String, val durationMs: Long, val uri: Uri)
data class Item(val id: Long, val title: String, val durationMs: Long, val uri: Uri)
data class Playlist(val name: String, val ids: List<Long>)

object Engine {
    private var player: ExoPlayer? = null

    fun get(ctx: Context): ExoPlayer {
        val existing = player
        if (existing != null) return existing
        val p = ExoPlayer.Builder(ctx.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = p
        return p
    }
}

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession.Builder(this, Engine.get(this)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}

fun queryAudio(ctx: Context): List<Track> {
    val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val proj = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION
    )
    val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 20000"
    val out = mutableListOf<Track>()
    ctx.contentResolver.query(base, proj, sel, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            val artist = c.getString(2)
            out.add(
                Track(
                    id,
                    c.getString(1) ?: "Sem título",
                    if (artist.isNullOrBlank() || artist == "<unknown>") "Artista desconhecido" else artist,
                    c.getLong(3),
                    ContentUris.withAppendedId(base, id)
                )
            )
        }
    }
    return out
}

private fun queryItems(
    ctx: Context, base: Uri, idCol: String, titleCol: String, durCol: String?, order: String
): List<Item> {
    val proj = if (durCol != null) arrayOf(idCol, titleCol, durCol) else arrayOf(idCol, titleCol)
    val out = mutableListOf<Item>()
    ctx.contentResolver.query(base, proj, null, null, order)?.use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            out.add(
                Item(
                    id,
                    c.getString(1) ?: "Sem título",
                    if (durCol != null) c.getLong(2) else 0L,
                    ContentUris.withAppendedId(base, id)
                )
            )
        }
    }
    return out
}

fun queryVideos(ctx: Context): List<Item> = queryItems(
    ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
    MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DURATION,
    "${MediaStore.Video.Media.DATE_ADDED} DESC"
)

fun queryImages(ctx: Context): List<Item> = queryItems(
    ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    MediaStore.Images.Media._ID, MediaStore.Images.Media.TITLE, null,
    "${MediaStore.Images.Media.DATE_ADDED} DESC"
).take(400)

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0L)
    return "%d:%02d".format(s / 60, s % 60)
}

fun playQueue(ctx: Context, tracks: List<Track>, index: Int) {
    val p = Engine.get(ctx)
    val items = tracks.map { t ->
        MediaItem.Builder()
            .setMediaId(t.id.toString())
            .setUri(t.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).build())
            .build()
    }
    p.setMediaItems(items, index, 0L)
    p.prepare()
    p.play()
}

fun playVideo(ctx: Context, v: Item) {
    val p = Engine.get(ctx)
    p.setMediaItem(
        MediaItem.Builder()
            .setMediaId("v" + v.id)
            .setUri(v.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(v.title).build())
            .build()
    )
    p.prepare()
    p.play()
}

object Store {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("boyrick_music", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<Playlist> {
        return try {
            val arr = JSONArray(sp(ctx).getString("playlists", "[]"))
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("ids")
                Playlist(o.getString("name"), List(ids.length()) { j -> ids.getLong(j) })
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p -> arr.put(JSONObject().put("name", p.name).put("ids", JSONArray(p.ids))) }
        sp(ctx).edit().putString("playlists", arr.toString()).apply()
    }
}

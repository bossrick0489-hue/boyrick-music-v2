package com.boyrick.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Sky = Color(0xFF7FD6FF)
private val Ocean = Color(0xFF1E9BE0)
private val Ink = Color(0xFF20262D)
private val Panel = Color(0xFF2B333C)
private val Muted = Color(0xFF9AAAB8)
private val Line = Color(0xFF3B4650)

private fun perms(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.POST_NOTIFICATIONS
    ) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun hasMedia(ctx: Context): Boolean {
    val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    return ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startService(Intent(this, PlaybackService::class.java))
        } catch (e: Exception) {
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Sky, background = Ink, surface = Panel)) {
                App()
            }
        }
    }
}

class PState(private val p: Player) {
    var playing by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var mediaId by mutableStateOf("")
    var hasItem by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeat by mutableStateOf(0)

    fun sync() {
        playing = p.isPlaying
        title = p.mediaMetadata.title?.toString() ?: ""
        artist = p.mediaMetadata.artist?.toString() ?: ""
        mediaId = p.currentMediaItem?.mediaId ?: ""
        hasItem = p.currentMediaItem != null
        shuffle = p.shuffleModeEnabled
        repeat = p.repeatMode
    }
}

@Composable
fun rememberPState(): PState {
    val ctx = LocalContext.current
    val exo = remember { Engine.get(ctx) }
    val st = remember { PState(exo).also { it.sync() } }
    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                st.sync()
            }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l) }
    }
    return st
}

@Composable
fun Logo(size: Dp) {
    val shape = RoundedCornerShape(size * 0.26f)
    Box(
        Modifier.size(size).clip(shape).background(Panel).border(2.dp, Sky, shape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "B", color = Sky, fontSize = (size.value * 0.5f).sp,
                fontWeight = FontWeight.Black, lineHeight = (size.value * 0.52f).sp
            )
            Text("music", color = Muted, fontSize = (size.value * 0.16f).sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasMedia(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasMedia(ctx)
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms()) }
    if (!granted) {
        Column(
            Modifier.fillMaxSize().background(Ink).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Logo(104.dp)
            Spacer(Modifier.height(20.dp))
            Text("BOYRICK MUSIC", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "Precisamos de acesso às tuas músicas, vídeos e fotos.",
                color = Muted, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { launcher.launch(perms()) },
                colors = ButtonDefaults.buttonColors(containerColor = Sky, contentColor = Ink)
            ) { Text("PERMITIR ACESSO", fontWeight = FontWeight.Bold) }
        }
    } else {
        Library()
    }
}

@Composable
fun Empty(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(msg, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
fun Thumb(uri: Uri, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                ctx.contentResolver.loadThumbnail(uri, android.util.Size(240, 240), null)
            } catch (e: Exception) {
                null
            }
        }
    }
    val b = bmp
    if (b != null) {
        Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(Line))
    }
}

@Composable
fun TrackRow(t: Track, active: Boolean, trailing: String, onPlay: () -> Unit, onTrailing: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onPlay() }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(if (active) Sky else Panel)
                .border(1.dp, if (active) Sky else Ocean, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(t.title.take(1).uppercase(), color = if (active) Ink else Sky, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title, color = if (active) Sky else Color.White, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium
            )
            Text(t.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(fmt(t.durationMs), color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text(
            trailing, color = Sky, fontSize = 20.sp,
            modifier = Modifier.clickable { onTrailing() }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
        )
    }
}

@Composable
fun Library() {
    val ctx = LocalContext.current
    val st = rememberPState()
    val exo = remember { Engine.get(ctx) }
    var tab by remember { mutableStateOf(0) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var videos by remember { mutableStateOf<List<Item>>(emptyList()) }
    var images by remember { mutableStateOf<List<Item>>(emptyList()) }
    var playlists by remember { mutableStateOf(Store.load(ctx)) }
    var openPl by remember { mutableStateOf<String?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var video by remember { mutableStateOf<Item?>(null) }
    var imageIdx by remember { mutableStateOf(-1) }
    var addTrack by remember { mutableStateOf<Track?>(null) }
    var newPl by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tracks = withContext(Dispatchers.IO) { queryAudio(ctx) }
        videos = withContext(Dispatchers.IO) { queryVideos(ctx) }
        images = withContext(Dispatchers.IO) { queryImages(ctx) }
    }

    fun savePl(l: List<Playlist>) {
        playlists = l
        Store.save(ctx, l)
    }

    BackHandler(enabled = openPl != null) { openPl = null }

    Column(
        Modifier.fillMaxSize().background(Ink)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Ocean.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f), radius = size.width
                    )
                )
            }
            .statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp, 12.dp, 14.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Logo(48.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("BOYRICK MUSIC", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("SLIDE XTREME", color = Sky, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            listOf("Músicas", "Vídeos", "Fotos", "Playlists").forEachIndexed { i, s ->
                Column(
                    Modifier.weight(1f).clickable { tab = i; openPl = null }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s, color = if (tab == i) Sky else Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.height(2.dp).width(24.dp).background(if (tab == i) Sky else Color.Transparent))
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> if (tracks.isEmpty()) Empty("Nenhuma música encontrada no telemóvel.") else LazyColumn {
                    items(tracks.size) { i ->
                        val t = tracks[i]
                        TrackRow(t, st.mediaId == t.id.toString(), "⋮", { playQueue(ctx, tracks, i) }, { addTrack = t })
                    }
                }
                1 -> if (videos.isEmpty()) Empty("Nenhum vídeo encontrado.") else LazyColumn {
                    items(videos.size) { i ->
                        val v = videos[i]
                        Row(
                            Modifier.fillMaxWidth().clickable { playVideo(ctx, v); video = v }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Thumb(v.uri, Modifier.size(width = 96.dp, height = 56.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(fmt(v.durationMs), color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
                2 -> if (images.isEmpty()) Empty("Nenhuma foto encontrada.") else LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(images.size) { i ->
                        AsyncImage(
                            model = images[i].uri, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.aspectRatio(1f).clickable { imageIdx = i }
                        )
                    }
                }
                else -> {
                    val pl = playlists.firstOrNull { it.name == openPl }
                    if (pl == null) {
                        Column(Modifier.fillMaxSize()) {
                            Text(
                                "+ NOVA PLAYLIST", color = Ink, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(14.dp).clip(RoundedCornerShape(50)).background(Sky)
                                    .clickable { newPl = true }.padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                            if (playlists.isEmpty()) Empty("Ainda não tens playlists.") else LazyColumn {
                                items(playlists.size) { i ->
                                    val p = playlists[i]
                                    Row(
                                        Modifier.fillMaxWidth().clickable { openPl = p.name }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Panel)
                                                .border(1.dp, Ocean, RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) { Text("♪", color = Sky, fontSize = 20.sp) }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(p.name, color = Color.White, fontWeight = FontWeight.Medium)
                                            Text("${p.ids.size} faixas", color = Muted, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val list = pl.ids.mapNotNull { id -> tracks.firstOrNull { it.id == id } }
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("‹", color = Sky, fontSize = 28.sp, modifier = Modifier.clickable { openPl = null }.padding(end = 12.dp))
                                Text(pl.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(
                                    "APAGAR", color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.clickable {
                                        savePl(playlists.filter { it.name != pl.name })
                                        openPl = null
                                    }
                                )
                            }
                            if (list.isNotEmpty()) {
                                Text(
                                    "▶ TOCAR TUDO", color = Ink, fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                        .clip(RoundedCornerShape(50)).background(Sky)
                                        .clickable { playQueue(ctx, list, 0) }
                                        .padding(horizontal = 18.dp, vertical = 10.dp)
                                )
                            }
                            if (list.isEmpty()) Empty("Playlist vazia.\nAdiciona faixas com ⋮ na aba Músicas.") else LazyColumn {
                                items(list.size) { i ->
                                    val t = list[i]
                                    TrackRow(
                                        t, st.mediaId == t.id.toString(), "✕",
                                        { playQueue(ctx, list, i) },
                                        { savePl(playlists.map { if (it.name == pl.name) it.copy(ids = it.ids - t.id) else it }) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (st.hasItem && video == null) {
            Row(
                Modifier.fillMaxWidth().background(Panel).clickable { showPlayer = true }
                    .navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Ink).border(1.dp, Sky, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("B", color = Sky, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(st.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(st.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    if (st.playing) "❚❚" else "▶", color = Sky, fontSize = 20.sp,
                    modifier = Modifier.clickable { if (exo.isPlaying) exo.pause() else exo.play() }.padding(10.dp)
                )
                Text(
                    "⏭", color = Sky, fontSize = 20.sp,
                    modifier = Modifier.clickable { exo.seekToNextMediaItem() }.padding(10.dp)
                )
            }
        }
    }

    if (showPlayer) {
        Dialog(onDismissRequest = { showPlayer = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            NowPlaying(st, exo) { showPlayer = false }
        }
    }

    if (video != null) {
        Dialog(
            onDismissRequest = { exo.pause(); video = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(factory = { c -> PlayerView(c).apply { player = exo } }, modifier = Modifier.fillMaxSize())
                Text(
                    "✕", color = Color.White, fontSize = 24.sp,
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
                        .clickable { exo.pause(); video = null }.padding(16.dp)
                )
            }
        }
    }

    if (imageIdx >= 0) {
        Dialog(onDismissRequest = { imageIdx = -1 }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val pager = rememberPagerState(initialPage = imageIdx) { images.size }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                HorizontalPager(state = pager) { p ->
                    AsyncImage(
                        model = images[p].uri, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    "${pager.currentPage + 1} / ${images.size}", color = Color.White, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)
                )
                Text(
                    "✕", color = Color.White, fontSize = 24.sp,
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
                        .clickable { imageIdx = -1 }.padding(16.dp)
                )
            }
        }
    }

    if (addTrack != null) {
        val t = addTrack!!
        AlertDialog(
            onDismissRequest = { addTrack = null },
            containerColor = Panel,
            title = { Text("Adicionar à playlist", color = Color.White) },
            text = {
                if (playlists.isEmpty()) {
                    Text("Cria primeiro uma playlist na aba Playlists.", color = Muted)
                } else {
                    Column {
                        playlists.forEach { p ->
                            Text(
                                p.name, color = Sky,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    savePl(playlists.map { if (it.name == p.name && t.id !in it.ids) it.copy(ids = it.ids + t.id) else it })
                                    addTrack = null
                                }.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addTrack = null }) { Text("Fechar", color = Muted) } }
        )
    }

    if (newPl) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newPl = false },
            containerColor = Panel,
            title = { Text("Nova playlist", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    placeholder = { Text("Nome da playlist") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty() && playlists.none { it.name == n }) savePl(playlists + Playlist(n, emptyList()))
                    newPl = false
                }) { Text("Criar", color = Sky) }
            },
            dismissButton = { TextButton(onClick = { newPl = false }) { Text("Cancelar", color = Muted) } }
        )
    }
}

@Composable
fun Vinyl(playing: Boolean) {
    var angle by remember { mutableStateOf(0f) }
    LaunchedEffect(playing) {
        while (playing) {
            angle = (angle + 0.9f) % 360f
            delay(16)
        }
    }
    Box(Modifier.size(270.dp).rotate(angle), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Panel, radius = r)
            for (i in 0..11) {
                drawCircle(Line, radius = r * (0.40f + i * 0.05f), style = Stroke(width = 1.dp.toPx()))
            }
            drawArc(
                Sky.copy(alpha = 0.35f), startAngle = 200f, sweepAngle = 50f, useCenter = false,
                topLeft = Offset(r * 0.14f, r * 0.14f), size = Size(r * 1.72f, r * 1.72f),
                style = Stroke(width = 6.dp.toPx())
            )
            drawArc(
                Sky.copy(alpha = 0.22f), startAngle = 20f, sweepAngle = 50f, useCenter = false,
                topLeft = Offset(r * 0.14f, r * 0.14f), size = Size(r * 1.72f, r * 1.72f),
                style = Stroke(width = 6.dp.toPx())
            )
            drawCircle(Sky, radius = r * 0.34f)
            drawCircle(Ocean, radius = r * 0.34f, style = Stroke(width = 3.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("B", color = Ink, fontSize = 46.sp, fontWeight = FontWeight.Black, lineHeight = 46.sp)
            Text("music", color = Ink, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun NowPlaying(st: PState, exo: Player, onClose: () -> Unit) {
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            pos = exo.currentPosition
            dur = exo.duration.coerceAtLeast(0L)
            delay(300)
        }
    }
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2E4A5C), Ink)))
            .statusBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("⌄", color = Sky, fontSize = 28.sp, modifier = Modifier.clickable { onClose() }.padding(end = 12.dp))
            Spacer(Modifier.weight(1f))
            Text("A TOCAR", color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(40.dp))
        }
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -120f) exo.seekToNextMediaItem()
                        else if (total > 120f) exo.seekToPreviousMediaItem()
                    }
                ) { _, amount -> total += amount }
            }
        ) { Vinyl(st.playing) }
        Spacer(Modifier.height(20.dp))
        Text(st.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(st.artist, color = Muted, fontSize = 14.sp, maxLines = 1)
        Spacer(Modifier.height(14.dp))
        val top = max(1f, dur.toFloat())
        Slider(
            value = (drag ?: pos.toFloat()).coerceIn(0f, top),
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.let { exo.seekTo(it.toLong()) }
                drag = null
            },
            valueRange = 0f..top,
            colors = SliderDefaults.colors(thumbColor = Sky, activeTrackColor = Sky, inactiveTrackColor = Line)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos), color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(fmt(dur), color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⏮", color = Sky, fontSize = 30.sp, modifier = Modifier.clickable { exo.seekToPreviousMediaItem() })
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Sky)
                    .clickable { if (exo.isPlaying) exo.pause() else exo.play() },
                contentAlignment = Alignment.Center
            ) {
                Text(if (st.playing) "❚❚" else "▶", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Text("⏭", color = Sky, fontSize = 30.sp, modifier = Modifier.clickable { exo.seekToNextMediaItem() })
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "ALEATÓRIO", color = if (st.shuffle) Sky else Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { exo.shuffleModeEnabled = !exo.shuffleModeEnabled }.padding(8.dp)
            )
            Text(
                when (st.repeat) {
                    Player.REPEAT_MODE_ONE -> "REPETIR 1"
                    Player.REPEAT_MODE_ALL -> "REPETIR TUDO"
                    else -> "REPETIR"
                },
                color = if (st.repeat != Player.REPEAT_MODE_OFF) Sky else Muted,
                fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable {
                    exo.repeatMode = when (exo.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }.padding(8.dp)
            )
        }
    }
}

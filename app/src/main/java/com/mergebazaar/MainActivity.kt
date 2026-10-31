package com.mergebazaar

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// --- Warm Sunset & Wood Market Palette (Gossip Harbor Style) ---
private val SunsetTop = Color(0xFFFFB56B)
private val SunsetMid = Color(0xFFFFD89C)
private val SunsetLow = Color(0xFFFFE9C5)
private val SunsetBottom = Color(0xFFF6C68B)

private val WoodDarkest = Color(0xFF42210B)
private val WoodDark = Color(0xFF5D3113)
private val WoodMid = Color(0xFF7D451D)
private val WoodLight = Color(0xFF9E5B2A)
private val WoodPlankLight = Color(0xFFFFF3DC)
private val WoodPlankBottom = Color(0xFFF3DEC0)
private val WoodSignBorder = Color(0xFF6B3A18)

private val GoldBright = Color(0xFFFFDF40)
private val GoldMid = Color(0xFFFFB300)
private val GoldDeep = Color(0xFFE07A00)
private val OrangeVibrant = Color(0xFFFF6D00)
private val OrangeDeep = Color(0xFFC84B00)

private val EmeraldBright = Color(0xFF2ECC71)
private val EmeraldDeep = Color(0xFF1E8449)
private val SkyBlue = Color(0xFF29B6F6)
private val DeepBrownText = Color(0xFF4A2508)
private val SoftCream = Color(0xFFFFFAF0)

private var toneGen: ToneGenerator? = null
fun playTone(kind: String, muted: Boolean) {
    if (muted) return
    try {
        if (toneGen == null) toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
        val tone = when (kind) {
            "merge" -> ToneGenerator.TONE_PROP_BEEP
            "serve" -> ToneGenerator.TONE_PROP_ACK
            "spawn" -> ToneGenerator.TONE_PROP_BEEP2
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        toneGen?.startTone(tone, 120)
    } catch (e: Exception) { /* sound is optional */ }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val game = Game.create(this)
        setContent {
            MergeBazaarApp(game)
        }
    }
}

@Composable
fun MergeBazaarApp(game: Game) {
    var tab by remember { mutableStateOf("Board") }
    var lastBanner by remember { mutableStateOf("") }
    var lastSoundTs by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        game.checkDaily()
        while (true) {
            delay(1000)
            game.tick()
        }
    }

    LaunchedEffect(game.soundEvent) {
        val (ts, kind) = game.soundEvent
        if (ts > lastSoundTs && kind.isNotEmpty()) {
            lastSoundTs = ts
            playTone(kind, game.muted)
        }
    }

    LaunchedEffect(game.banner) {
        if (game.banner != lastBanner) {
            lastBanner = game.banner
            if (game.soundEvent.first == 0L) {
                when {
                    game.banner.contains("Merged") -> playTone("merge", game.muted)
                    game.banner.contains("Served") -> playTone("serve", game.muted)
                    game.banner.contains("Spawned") -> playTone("spawn", game.muted)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SunsetTop, SunsetMid, SunsetLow, SunsetBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 1. Compact Header Row: Title + White Pill Chips with Gold Icon Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🎪 Merge Bazaar",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = WoodDarkest,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color(0x88FFFFFF),
                                offset = Offset(0f, 2f),
                                blurRadius = 2f
                            )
                        ),
                        maxLines = 1
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatChip(icon = "🪙", value = "${game.coins}")
                    StatChip(icon = "⭐", value = "${game.stars}")
                    StatChip(icon = "⚡", value = "${game.energy}/50")
                }
            }

            // 2. Thin Level & Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "👑 Level ${game.level}  •  ${game.stars % 3}/3 ⭐",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DeepBrownText
                    )
                    Text(
                        text = "🔥 ${game.streak}d streak  •  🏆 ${game.score}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DeepBrownText
                    )
                }
                val progressFraction by animateFloatAsState(
                    targetValue = ((game.stars % 3) / 3f).coerceIn(0.06f, 1f),
                    animationSpec = tween(350),
                    label = "levelProgress"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .shadow(1.dp, RoundedCornerShape(999.dp))
                        .clip(RoundedCornerShape(999.dp))
                        .background(WoodDark)
                        .border(1.dp, Color(0xFFFFD584), RoundedCornerShape(999.dp))
                        .padding(1.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(GoldBright, GoldMid, OrangeVibrant)
                                )
                            )
                    )
                }
            }

            // Optional Rush Event Banner (1 compact line when live)
            if (eventActive()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(999.dp))
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFF416C), Color(0xFFFF4B2B), Color(0xFFFF8C00))
                            )
                        )
                        .border(1.dp, GoldBright, RoundedCornerShape(999.dp))
                        .padding(vertical = 3.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔥 BAZAAR RUSH! Double ⭐ rewards! ~${eventEndsInMinutes()}m left",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            // 3. Tabs Row (Glossy Gold/Orange Selected Pill, Warm Cream Unselected Pill)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                listOf("Board", "Quests", "Pass", "Collection", "Race").forEach { t ->
                    val selected = tab == t
                    val tabShape = RoundedCornerShape(999.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .shadow(if (selected) 4.dp else 2.dp, tabShape)
                            .clip(tabShape)
                            .background(
                                if (selected) {
                                    Brush.verticalGradient(
                                        listOf(GoldBright, GoldMid, OrangeVibrant)
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(Color.White, Color(0xFFFFEFD5))
                                    )
                                }
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (selected) WoodDark else Color(0xFFD9A76A),
                                shape = tabShape
                            )
                            .clickable { tab = t }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = t,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (selected) Color.White else WoodDarkest,
                            style = if (selected) {
                                TextStyle(
                                    shadow = Shadow(
                                        color = Color(0x995D2906),
                                        offset = Offset(0f, 1.5f),
                                        blurRadius = 2f
                                    )
                                )
                            } else TextStyle.Default,
                            maxLines = 1
                        )
                    }
                }
            }

            // 4. Active Tab Content (Board is strictly non-scrolling & fits first screen)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (tab) {
                    "Board" -> BoardTab(game)
                    "Quests" -> QuestsTab(game)
                    "Pass" -> PassTab(game)
                    "Collection" -> CollectionTab(game)
                    "Race" -> RaceTab(game)
                }
            }
        }
    }
}

@Composable
fun StatChip(icon: String, value: String) {
    val pillShape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .shadow(3.dp, pillShape)
            .clip(pillShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFFFF5E4))
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.verticalGradient(listOf(GoldBright, GoldDeep)),
                shape = pillShape
            )
            .padding(start = 3.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Gold circular medallion for the icon
        Box(
            modifier = Modifier
                .size(18.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFFF380), GoldMid, GoldDeep)
                    )
                )
                .border(1.dp, Color(0xFFFFF9C4), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 10.sp)
        }
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = DeepBrownText,
            maxLines = 1
        )
    }
}

@Composable
fun SmallButton(
    text: String,
    enabled: Boolean = true,
    fontSize: TextUnit = 10.5.sp,
    height: Dp = 32.dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    val bgBrush = if (enabled) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFDF5D),
                Color(0xFFFF9E1B),
                Color(0xFFE65C00)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFD7C8B6),
                Color(0xFFB5A38F)
            )
        )
    }
    val borderColor = if (enabled) Color(0xFFB84200) else Color(0xFF8E7F6E)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .shadow(if (enabled) 3.dp else 0.dp, shape)
            .clip(shape)
            .background(bgBrush)
            .border(1.5.dp, borderColor, shape)
            .drawWithContent {
                drawContent()
                // Glossy top pill highlight
                if (enabled) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.0f)
                            )
                        ),
                        topLeft = Offset(size.width * 0.06f, 2f),
                        size = Size(size.width * 0.88f, size.height * 0.42f),
                        cornerRadius = CornerRadius(size.height, size.height)
                    )
                }
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            color = if (enabled) Color.White else Color(0xFFF2ECE4),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                shadow = if (enabled) {
                    Shadow(
                        color = Color(0xAA6A2300),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 2f
                    )
                } else null
            )
        )
    }
}

@Composable
fun BoardTab(game: Game) {
    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    var boardSize by remember { mutableStateOf(0f) }
    var boardHeight by remember { mutableStateOf(0f) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragEmoji by remember { mutableStateOf("") }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var dragOver by remember { mutableStateOf<Int?>(null) }

    // Floating +Coins / +Stars state triggered on merge and serve
    var activeFx by remember { mutableStateOf<FloatingFx?>(null) }
    val fxProgress = remember { Animatable(0f) }
    var prevCoins by remember { mutableStateOf(game.coins) }

    LaunchedEffect(game.floatingFx?.id) {
        val fx = game.floatingFx
        if (fx != null) {
            activeFx = fx
            fxProgress.snapTo(0f)
            fxProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing)
            )
            if (activeFx?.id == fx.id) {
                activeFx = null
            }
        }
    }

    // Fallback coin gain watcher in case coins increase without floatingFx
    LaunchedEffect(game.coins) {
        val diff = game.coins - prevCoins
        prevCoins = game.coins
        if (diff > 0 && game.floatingFx == null) {
            val targetIdx = game.lastMergedCell?.first ?: game.selectedCell ?: 14
            game.triggerFloatingFx(targetIdx, "+$diff 🪙")
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Orders Row: Wooden Shop Signs with Customer Avatar, Gold Coins & Star Rewards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            game.orders.forEachIndexed { idx, ord ->
                val selected = game.selectedOrder == idx
                val ready = game.board.any {
                    it is Cell.Item && it.chain == ord.chain && it.level == ord.level
                }
                WoodenOrderSignCard(
                    order = ord,
                    selected = selected,
                    ready = ready,
                    onClick = { game.tapOrder(idx) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. Compact Status / Hint Banner
        val bannerShape = RoundedCornerShape(10.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, bannerShape)
                .clip(bannerShape)
                .background(
                    when (game.bannerKind) {
                        "warning" -> Brush.horizontalGradient(listOf(Color(0xFFFFE0DC), Color(0xFFFFF0ED)))
                        "success" -> Brush.horizontalGradient(listOf(Color(0xFFD6F9E3), Color(0xFFECFFF3)))
                        "info" -> Brush.horizontalGradient(listOf(Color(0xFFD9F1FF), Color(0xFFEEF9FF)))
                        else -> Brush.horizontalGradient(listOf(Color(0xFFFFF6E3), Color(0xFFFFFBF2)))
                    }
                )
                .border(
                    width = 1.dp,
                    color = when (game.bannerKind) {
                        "warning" -> Color(0xFFE74C3C)
                        "success" -> EmeraldBright
                        "info" -> SkyBlue
                        else -> Color(0xFFD9A76A)
                    },
                    shape = bannerShape
                )
                .padding(vertical = 3.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val statusPrefix = when {
                game.scissorMode -> "✂️ SCISSORS: Tap any item to clear!  •  "
                game.doubleMergesLeft > 0 -> "✨ 2x COINS (${game.doubleMergesLeft}): "
                else -> ""
            }
            Text(
                text = statusPrefix + game.banner,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DeepBrownText,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 3. THE 6x5 BOARD in a Wooden Market-Stall Frame (dark wood border, soft shadow, rounded corners)
        val matchingOrderItem: Pair<Chain, Int>? = game.selectedOrder
            ?.let { game.orders.getOrNull(it) }
            ?.let { it.chain to it.level }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = WoodDarkest,
                    spotColor = WoodDarkest
                )
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(WoodLight, WoodMid, WoodDark, WoodDarkest)
                    )
                )
                .border(3.5.dp, WoodDarkest, RoundedCornerShape(22.dp))
                .drawWithContent {
                    drawContent()
                    // Subtle wooden frame inner gold/warm bevel trim
                    drawRoundRect(
                        color = Color(0x55FFD59A),
                        topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                        size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
                        cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                .padding(7.dp)
        ) {
            // Inner recessed market tray
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF72401B),
                                Color(0xFF8C5327),
                                Color(0xFF6B3A17)
                            )
                        )
                    )
                    .border(1.5.dp, Color(0xFF3E1E08), RoundedCornerShape(16.dp))
                    .padding(5.dp)
                    .onGloballyPositioned { coords ->
                        boardOrigin = coords.positionInWindow()
                        boardSize = coords.size.width.toFloat()
                        boardHeight = coords.size.height.toFloat()
                    }
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    for (row in 0 until 5) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            for (col in 0 until 6) {
                                val idx = row * 6 + col
                                val cell = game.board[idx]
                                val isSelected = game.selectedCell == idx
                                val isDragOver = dragOver == idx
                                val isReadyForOrder = cell is Cell.Item &&
                                    game.orders.any { it.chain == cell.chain && it.level == cell.level }
                                val isHighlightedOrderMatch = cell is Cell.Item &&
                                    matchingOrderItem != null &&
                                    cell.chain == matchingOrderItem.first &&
                                    cell.level == matchingOrderItem.second

                                val mergeStamp = if (game.lastMergedCell?.first == idx) game.lastMergedCell?.second ?: 0L else 0L
                                val spawnStamp = if (game.lastSpawnedCell?.first == idx) game.lastSpawnedCell?.second ?: 0L else 0L

                                GlossyBoardTile(
                                    idx = idx,
                                    cell = cell,
                                    isSelected = isSelected,
                                    isDragOver = isDragOver,
                                    isReadyForOrder = isReadyForOrder,
                                    isHighlightedOrderMatch = isHighlightedOrderMatch,
                                    mergeTimestamp = mergeStamp,
                                    spawnTimestamp = spawnStamp,
                                    onTap = { game.tapCell(idx) },
                                    onDragStart = { startOffset ->
                                        if (cell is Cell.Item) {
                                            val cellW = boardSize / 6f
                                            val cellH = if (boardHeight > 0f) boardHeight / 5f else cellW
                                            dragFrom = idx
                                            dragEmoji = cell.chain.emoji(cell.level)
                                            dragPos = Offset(
                                                boardOrigin.x + col * cellW + startOffset.x,
                                                boardOrigin.y + row * cellH + startOffset.y
                                            )
                                        }
                                    },
                                    onDrag = { dragAmount ->
                                        if (dragFrom != null) {
                                            dragPos += dragAmount
                                            val cellW = boardSize / 6f
                                            val cellH = if (boardHeight > 0f) boardHeight / 5f else cellW
                                            val localX = dragPos.x - boardOrigin.x
                                            val localY = dragPos.y - boardOrigin.y
                                            val dropCol = (localX / cellW).toInt()
                                            val dropRow = (localY / cellH).toInt()
                                            dragOver = if (dropCol in 0..5 && dropRow in 0..4) {
                                                dropRow * 6 + dropCol
                                            } else null
                                        }
                                    },
                                    onDragEnd = {
                                        val from = dragFrom
                                        dragFrom = null
                                        dragEmoji = ""
                                        dragOver = null
                                        if (from != null && boardSize > 0f) {
                                            val cellW = boardSize / 6f
                                            val cellH = if (boardHeight > 0f) boardHeight / 5f else cellW
                                            val localX = dragPos.x - boardOrigin.x
                                            val localY = dragPos.y - boardOrigin.y
                                            val dropCol = (localX / cellW).toInt()
                                            val dropRow = (localY / cellH).toInt()
                                            if (dropCol in 0..5 && dropRow in 0..4) {
                                                val to = dropRow * 6 + dropCol
                                                if (to != from) {
                                                    game.moveOrMerge(from, to)
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        dragFrom = null
                                        dragEmoji = ""
                                        dragOver = null
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                )
                            }
                        }
                    }
                }

                // Drag Ghost Overlay
                if (dragEmoji.isNotEmpty()) {
                    val density = LocalDensity.current
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (dragPos.x - boardOrigin.x).roundToInt() - with(density) { 24.dp.toPx().toInt() },
                                    (dragPos.y - boardOrigin.y).roundToInt() - with(density) { 24.dp.toPx().toInt() }
                                )
                            }
                            .scale(1.18f)
                    ) {
                        Text(text = dragEmoji, fontSize = 34.sp)
                    }
                }

                // Floating +Coins / +Stars Animation Overlay on Merge & Serve
                val fx = activeFx
                if (fx != null && boardSize > 0f) {
                    val density = LocalDensity.current
                    val fxCol = (fx.cellIndex % 6).coerceIn(0, 5)
                    val fxRow = (fx.cellIndex / 6).coerceIn(0, 4)
                    val cellW = boardSize / 6f
                    val cellH = if (boardHeight > 0f) boardHeight / 5f else cellW
                    val p = fxProgress.value
                    val risePx = with(density) { (38.dp * p).toPx() }
                    val alpha = (1f - (p * 0.85f)).coerceIn(0f, 1f)
                    val popScale = if (p < 0.2f) 0.8f + (p / 0.2f) * 0.45f else 1.25f - ((p - 0.2f) * 0.25f)

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (fxCol * cellW + cellW * 0.05f).roundToInt(),
                                    y = (fxRow * cellH - risePx).roundToInt()
                                )
                            }
                            .graphicsLayer {
                                this.alpha = alpha
                                this.scaleX = popScale
                                this.scaleY = popScale
                            }
                            .shadow(4.dp, RoundedCornerShape(999.dp))
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFFF176), GoldMid, OrangeVibrant)
                                )
                            )
                            .border(1.5.dp, Color.White, RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = fx.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                style = TextStyle(
                                    shadow = Shadow(
                                        color = WoodDarkest,
                                        offset = Offset(0f, 1.5f),
                                        blurRadius = 3f
                                    )
                                )
                            )
                            if (fx.subText.isNotEmpty()) {
                                Text(
                                    text = fx.subText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFFFDE7),
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = WoodDarkest,
                                            offset = Offset(0f, 1.5f),
                                            blurRadius = 3f
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Action Buttons: Two compact rows of Gold/Orange Pill Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.weight(1f)) {
                SmallButton(
                    text = "↩️ Undo 10🪙",
                    enabled = game.lastSnapshot != null && game.coins >= 10
                ) { game.undo() }
            }
            Box(Modifier.weight(1f)) {
                SmallButton(text = "⚡+20 50🪙") { game.buyEnergy() }
            }
            Box(Modifier.weight(1f)) {
                SmallButton(text = "🎁 L3 100🪙") { game.buyItem() }
            }
            Box(Modifier.weight(1f)) {
                SmallButton(text = if (game.muted) "🔇 Muted" else "🔊 Sound") {
                    game.muted = !game.muted
                    game.save()
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.weight(1f)) {
                SmallButton(text = "🎁 Free Gift") { game.claimGift() }
            }
            Box(Modifier.weight(1f)) {
                SmallButton(text = if (game.scissorMode) "✂️ Cancel" else "✂️ Cut 30🪙") {
                    game.buyScissors()
                }
            }
            Box(Modifier.weight(1f)) {
                SmallButton(text = "✨ 2x 50🪙") { game.buyDouble() }
            }
            Box(Modifier.weight(1f)) {
                SmallButton(text = "🆕 Reset") { game.newGame() }
            }
        }
    }
}

@Composable
private fun WoodenOrderSignCard(
    order: Order,
    selected: Boolean,
    ready: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val signShape = RoundedCornerShape(14.dp)
    val outerBorderColor = when {
        selected -> SkyBlue
        ready -> EmeraldBright
        else -> WoodSignBorder
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (selected || ready) 6.dp else 4.dp,
                shape = signShape,
                ambientColor = WoodDarkest,
                spotColor = WoodDarkest
            )
            .clip(signShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(WoodMid, WoodDark)
                )
            )
            .border(2.dp, outerBorderColor, signShape)
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        // Inner warm wooden parchment sign face
        val innerShape = RoundedCornerShape(11.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(innerShape)
                .background(
                    Brush.verticalGradient(
                        colors = when {
                            selected -> listOf(Color(0xFFE3F6FF), Color(0xFFC5EAFF))
                            ready -> listOf(Color(0xFFE8FFF0), Color(0xFFC8F7D8))
                            else -> listOf(WoodPlankLight, WoodPlankBottom)
                        }
                    )
                )
                .drawWithContent {
                    drawContent()
                    // Decorative brass nail heads in top corners of the wooden shop sign
                    val nailColor = Color(0xFFD49B35)
                    drawCircle(nailColor, radius = 2.5.dp.toPx(), center = Offset(6.dp.toPx(), 6.dp.toPx()))
                    drawCircle(nailColor, radius = 2.5.dp.toPx(), center = Offset(size.width - 6.dp.toPx(), 6.dp.toPx()))
                }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Customer Avatar + Desired Item Bubble
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Customer portrait medallion
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFFF8E7), Color(0xFFE5C396))
                            )
                        )
                        .border(1.dp, WoodMid, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = order.avatar, fontSize = 14.sp)
                }
                Spacer(Modifier.width(3.dp))
                // Speech/Order request badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                        .border(
                            1.dp,
                            if (ready) EmeraldBright else Color(0xFFD8B88A),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = order.emoji, fontSize = 15.sp)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "L${order.level}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = DeepBrownText
                    )
                }
            }

            // Gold Coins & Star Reward Ribbon
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = if (ready) {
                                listOf(EmeraldBright, EmeraldDeep)
                            } else {
                                listOf( Color(0xFF7B4318), Color(0xFF5A2E0C))
                            }
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (ready) "SERVE +${order.coins}🪙 +1⭐" else "+${order.coins} 🪙  +1 ⭐",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    color = if (ready) Color.White else GoldBright,
                    maxLines = 1
                )
            }

            // Timer Row
            val urgent = order.expiresIn <= 30
            Text(
                text = "⏰ ${order.expiresIn}s",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (urgent) Color(0xFFD32F2F) else Color(0xFF7E4A21),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun GlossyBoardTile(
    idx: Int,
    cell: Cell,
    isSelected: Boolean,
    isDragOver: Boolean,
    isReadyForOrder: Boolean,
    isHighlightedOrderMatch: Boolean,
    mergeTimestamp: Long,
    spawnTimestamp: Long,
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Merge / Spawn Pop Animation: Tile scales up then settles with spring physics
    val popScale = remember(idx) { Animatable(1f) }

    LaunchedEffect(mergeTimestamp) {
        if (mergeTimestamp > 0L) {
            popScale.snapTo(0.75f)
            popScale.animateTo(
                targetValue = 1.28f,
                animationSpec = tween(durationMillis = 130, easing = FastOutSlowInEasing)
            )
            popScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    LaunchedEffect(spawnTimestamp) {
        if (spawnTimestamp > 0L) {
            popScale.snapTo(0.5f)
            popScale.animateTo(
                targetValue = 1.16f,
                animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
            )
            popScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    val tileShape = RoundedCornerShape(13.dp)

    // Each item level gets its own bright, juicy, distinct gradient color palette
    val (topColor, bottomColor, rimColor) = when (cell) {
        is Cell.Generator -> when (cell.chain) {
            Chain.FOOD -> Triple(Color(0xFFFF9E44), Color(0xFFD9531E), Color(0xFF9E3308))
            Chain.CRAFT -> Triple(Color(0xFFFF7EB3), Color(0xFFD83177), Color(0xFF941649))
            Chain.TOY -> Triple(Color(0xFFA77BFF), Color(0xFF673AB7), Color(0xFF421E85))
        }
        is Cell.Item -> when (cell.level) {
            1 -> Triple(Color(0xFFFFF176), Color(0xFFFFC107), Color(0xFFD48800)) // Sunny Lemon Gold
            2 -> Triple(Color(0xFF69F0AE), Color(0xFF00C853), Color(0xFF008636)) // Juicy Mint Green
            3 -> Triple(Color(0xFF4FC3F7), Color(0xFF0288D1), Color(0xFF01579B)) // Sky Azure Blue
            4 -> Triple(Color(0xFFCE93D8), Color(0xFF8E24AA), Color(0xFF5E0F75)) // Royal Amethyst Purple
            5 -> Triple(Color(0xFFFF8A65), Color(0xFFF4511E), Color(0xFFB52B00)) // Coral Tangerine
            6 -> Triple(Color(0xFFFF80AB), Color(0xFFD81B60), Color(0xFF990940)) // Ruby Magenta
            else -> Triple(Color(0xFFFFF59D), Color(0xFFFF8F00), Color(0xFFB25900)) // Radiant Trophy Gold (L7)
        }
        is Cell.Empty -> Triple(Color(0xFFF3DEC3), Color(0xFFE3C59E), Color(0xFFC4A074))
    }

    val borderStrokeColor = when {
        isSelected -> Color(0xFF00E676)
        isHighlightedOrderMatch -> SkyBlue
        isDragOver -> GoldBright
        isReadyForOrder -> EmeraldBright
        cell is Cell.Generator -> GoldBright
        cell is Cell.Item -> Color.White.copy(alpha = 0.75f)
        else -> Color(0xFFC6A377)
    }

    val borderWidth = when {
        isSelected || isHighlightedOrderMatch || isDragOver -> 2.5.dp
        isReadyForOrder || cell is Cell.Generator -> 2.dp
        else -> 1.2.dp
    }

    Box(
        modifier = modifier
            .scale(popScale.value)
            .shadow(
                elevation = if (cell is Cell.Empty) 1.dp else 5.dp,
                shape = tileShape,
                ambientColor = WoodDarkest,
                spotColor = WoodDarkest
            )
            .clip(tileShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(topColor, bottomColor)
                )
            )
            .border(borderWidth, borderStrokeColor, tileShape)
            .drawWithContent {
                val w = size.width
                val h = size.height
                if (cell !is Cell.Empty) {
                    // Soft 3D bottom bevel shadow inside the tile
                    drawRoundRect(
                        color = rimColor.copy(alpha = 0.55f),
                        topLeft = Offset(0f, h * 0.82f),
                        size = Size(w, h * 0.18f),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                    )
                    // Glossy light highlight arc at the top of the tile
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.65f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        topLeft = Offset(w * 0.08f, h * 0.05f),
                        size = Size(w * 0.84f, h * 0.38f),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                    )
                } else {
                    // Recessed inner shadow for empty wooden slot
                    drawRoundRect(
                        color = Color(0x225D3113),
                        topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                        size = Size(w - 4.dp.toPx(), h - 4.dp.toPx()),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                    )
                }
                drawContent()
            }
            .pointerInput(idx, cell) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(idx, cell) {
                detectDragGestures(
                    onDragStart = { offset -> onDragStart(offset) },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                    onDrag = { _, dragAmount -> onDrag(dragAmount) }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        when (cell) {
            is Cell.Generator -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = cell.chain.crateEmoji,
                        fontSize = 27.sp
                    )
                    // Little lightning generator badge in bottom-right corner
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(WoodDarkest.copy(alpha = 0.8f))
                            .border(0.8.dp, GoldBright, RoundedCornerShape(6.dp))
                            .padding(horizontal = 3.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = "⚡",
                            fontSize = 8.sp,
                            color = GoldBright
                        )
                    }
                }
            }
            is Cell.Item -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = cell.chain.emoji(cell.level),
                        fontSize = 26.sp,
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                    // Level badge pill at bottom
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 2.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xAA3A1A04))
                            .padding(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "L${cell.level}",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    // Ready-to-serve checkmark badge
                    if (isReadyForOrder) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(EmeraldBright)
                                .border(1.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            is Cell.Empty -> {}
        }
    }
}

@Composable
fun StallCard(icon: String, title: String, unlocked: Boolean, status: String) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (unlocked) {
                        listOf(Color(0xFFD8FDE5), Color(0xFFB3F2CA))
                    } else {
                        listOf(WoodPlankLight, WoodPlankBottom)
                    }
                )
            )
            .border(
                width = 2.dp,
                color = if (unlocked) EmeraldBright else WoodMid,
                shape = shape
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (unlocked) Color.White else Color(0xFFE5CFAF))
                .border(1.dp, if (unlocked) EmeraldBright else WoodMid, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = if (unlocked) icon else "🔒", fontSize = 17.sp)
        }
        Spacer(Modifier.width(7.dp))
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = DeepBrownText,
                maxLines = 1
            )
            Text(
                text = if (unlocked) "Unlocked & Open!" else status,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (unlocked) EmeraldDeep else Color(0xFF7B5233),
                maxLines = 1
            )
        }
    }
}

@Composable
fun QuestsTab(game: Game) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        // Story Card in Wooden Frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFF9E6), Color(0xFFFFEAB5))
                    )
                )
                .border(2.dp, WoodMid, RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Text(
                text = "🎪 Maya's Bazaar — ${game.storyText()}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = DeepBrownText,
                lineHeight = 16.sp
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.weight(1f)) {
                StallCard(
                    icon = "🥐🍎",
                    title = "Bakery Stall",
                    unlocked = game.stars >= 3,
                    status = "Unlocks at 3 ⭐ (${game.stars}/3)"
                )
            }
            Box(Modifier.weight(1f)) {
                StallCard(
                    icon = "💐👑",
                    title = "Flower & Gold",
                    unlocked = game.stars >= 6,
                    status = "Unlocks at 6 ⭐ (${game.stars}/6)"
                )
            }
        }
        Text(
            text = "🎯 Today's Missions",
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            color = WoodDarkest
        )
        MissionRow("Merge 10 times today", game.mergesToday, 10, 40, "merge10", game)
        MissionRow("Serve 3 customers today", game.servesToday, 3, 50, "serve3", game)
        MissionRow("Make a Level 4 item today", game.maxLevelToday, 4, 60, "level4", game)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(listOf(WoodPlankLight, WoodPlankBottom))
                )
                .border(2.dp, WoodMid, RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "🏆 Bazaar Achievements",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = WoodDarkest
                )
                Text(
                    text = "• Total merges: ${game.totalMerges}  •  Customers served: ${game.totalServes}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepBrownText
                )
                Text(
                    text = "• Craft a Level 7 Bazaar Trophy for +100 🪙 bonus!",
                    fontSize = 12.sp,
                    color = DeepBrownText
                )
                Text(
                    text = "• Finish any full 7-item collection chain for +100 🪙!",
                    fontSize = 12.sp,
                    color = DeepBrownText
                )
                Text(
                    text = "🔥 Daily streak: ${game.streak} day(s)!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = OrangeDeep
                )
            }
        }
    }
}

@Composable
fun MissionRow(text: String, progress: Int, target: Int, reward: Int, id: String, game: Game) {
    val done = progress >= target
    val claimed = id in game.missionsClaimed
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (done && !claimed) {
                        listOf(Color(0xFFE8FFF0), Color(0xFFC9F7D9))
                    } else {
                        listOf(WoodPlankLight, WoodPlankBottom)
                    }
                )
            )
            .border(1.5.dp, if (done && !claimed) EmeraldBright else WoodMid, shape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${if (claimed) "✅" else if (done) "🎉" else "🎯"} $text",
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DeepBrownText,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${minOf(progress, target)}/$target",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = OrangeDeep
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.width(108.dp)) {
            SmallButton(
                text = if (claimed) "Claimed ✓" else "Claim +$reward🪙",
                enabled = done && !claimed,
                height = 30.dp
            ) {
                game.claimMission(id, reward)
            }
        }
    }
}

@Composable
fun PassTab(game: Game) {
    val tiers = listOf(
        Triple(3, "+40 🪙 Coins", 40 to 0),
        Triple(6, "+20 ⚡ Energy", 0 to 20),
        Triple(9, "+80 🪙 Coins", 80 to 0),
        Triple(12, "+30 ⚡ Energy", 0 to 30),
        Triple(15, "+150 🪙 Jackpot", 150 to 0)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "🎟️ Bazaar Pass — Earn ⭐ & Claim Rewards!",
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            color = WoodDarkest
        )
        tiers.forEachIndexed { i, (starsNeed, reward, rw) ->
            val reached = game.stars >= starsNeed
            val claimed = i in game.passClaimed
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, shape)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            when {
                                claimed -> listOf(EmeraldBright, EmeraldDeep)
                                reached -> listOf(Color(0xFFFFF8D6), Color(0xFFFFE082))
                                else -> listOf(WoodPlankLight, WoodPlankBottom)
                            }
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (claimed) EmeraldDeep else if (reached) GoldDeep else WoodMid,
                        shape = shape
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "⭐ $starsNeed Stars  •  $reward",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = if (claimed) Color.White else DeepBrownText,
                    modifier = Modifier.weight(1f)
                )
                Box(modifier = Modifier.width(100.dp)) {
                    SmallButton(
                        text = if (claimed) "Claimed ✓" else "Claim",
                        enabled = reached && !claimed,
                        height = 32.dp
                    ) {
                        game.claimPass(i, rw.first, rw.second)
                    }
                }
            }
        }
        Text(
            text = "💡 Serving customers during Bazaar Rush gives DOUBLE ⭐!",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = DeepBrownText
        )
    }
}

@Composable
fun CollectionTab(game: Game) {
    var got = 0
    var total = 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "📖 Market Collection — Discover Every Item!",
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            color = WoodDarkest
        )
        Chain.values().forEach { chain ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.verticalGradient(listOf(WoodPlankLight, WoodPlankBottom)))
                    .border(2.dp, WoodMid, RoundedCornerShape(14.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "${chain.crateEmoji} ${chain.label} Chain",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = WoodDarkest
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (lv in 1..7) {
                        total++
                        val key = "${chain}_$lv"
                        val has = key in game.collected
                        if (has) got++
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(if (has) 2.dp else 0.dp, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.verticalGradient(
                                        if (has) {
                                            listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F))
                                        } else {
                                            listOf(Color(0xFFE5D2B8), Color(0xFFD2B998))
                                        }
                                    )
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (has) GoldDeep else Color(0xFFB39674),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = if (has) chain.emoji(lv) else "❓", fontSize = 20.sp)
                                Text(
                                    text = "L$lv",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = DeepBrownText
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = "✨ Discovered $got/$total items. Complete a chain for +100 🪙!",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Black,
            color = DeepBrownText
        )
    }
}

@Composable
fun RaceTab(game: Game) {
    val week = (System.currentTimeMillis() / 604800000).toInt()
    val bots = listOf(
        Triple("🧔 Ravi", 120, 0),
        Triple("👩 Priya", 100, 1),
        Triple("🧑 Arjun", 80, 2),
        Triple("👵 Meera", 60, 3)
    ).map { (name, base, i) ->
        name to (base + ((week * 37 + i * 53) % 90) + game.totalServes / 2)
    }
    val rows = (bots + ("🎪 You" to game.score)).sortedByDescending { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "🏆 Weekly Harbor Race",
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            color = WoodDarkest
        )
        Text(
            text = "Merge & serve orders to outscore rival stall owners!",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = DeepBrownText
        )
        rows.forEachIndexed { i, (name, score) ->
            val me = name.contains("You")
            val shape = RoundedCornerShape(12.dp)
            val medal = when (i) {
                0 -> "🥇"
                1 -> "🥈"
                2 -> "🥉"
                else -> "${i + 1}."
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(if (me) 4.dp else 2.dp, shape)
                    .clip(shape)
                    .background(
                        Brush.horizontalGradient(
                            if (me) {
                                listOf(Color(0xFFFFF3B0), Color(0xFFFFCC80))
                            } else {
                                listOf(WoodPlankLight, WoodPlankBottom)
                            }
                        )
                    )
                    .border(
                        width = if (me) 2.dp else 1.5.dp,
                        color = if (me) OrangeVibrant else WoodMid,
                        shape = shape
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$medal $name",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Black,
                    color = DeepBrownText
                )
                Text(
                    text = "$score pts",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Black,
                    color = if (me) OrangeDeep else WoodDarkest
                )
            }
        }
    }
}

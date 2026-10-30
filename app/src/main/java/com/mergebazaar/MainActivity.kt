package com.mergebazaar

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val BG = Color(0xFFFFF6E9)
private val PANEL = Color(0xFFFFFCF5)
private val ACCENT = Color(0xFFE65C00)
private val GREEN = Color(0xFF27AE60)
private val BLUE = Color(0xFF2980B9)
private val GOLD = Color(0xFFFFD700)

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
    LaunchedEffect(Unit) {
        game.checkDaily()
        while (true) {
            delay(1000)
            game.tick()
        }
    }
    // Play a sound when the banner changes to a success message
    LaunchedEffect(game.banner) {
        if (game.banner != lastBanner) {
            lastBanner = game.banner
            when {
                game.banner.contains("Merged") -> playTone("merge", game.muted)
                game.banner.contains("Served") -> playTone("serve", game.muted)
                game.banner.contains("Spawned") -> playTone("spawn", game.muted)
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎪 Merge Bazaar", fontSize = 24.sp, fontWeight = FontWeight.Black, color = ACCENT)
            SmallButton("New Game") { game.newGame() }
        }
        // Story card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFFF8E1))
                .border(2.dp, Color(0xFFE6A817), RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Text("🎪 Maya's Bazaar — ${game.storyText()}", fontSize = 12.sp, lineHeight = 16.sp)
        }
        // Event banner
        if (eventActive()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFF416C))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🔥 BAZAAR RUSH LIVE! Double stars! Ends in ~${eventEndsInMinutes()} min",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
            }
        }
        // Tabs
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Board", "Quests", "Pass", "Collection", "Race").forEach { t ->
                val selected = tab == t
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) ACCENT else Color.White)
                        .border(1.dp, ACCENT, RoundedCornerShape(999.dp))
                        .clickable { tab = t }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else ACCENT)
                }
            }
        }
        when (tab) {
            "Board" -> BoardTab(game)
            "Quests" -> QuestsTab(game)
            "Pass" -> PassTab(game)
            "Collection" -> CollectionTab(game)
            "Race" -> RaceTab(game)
        }
    }
}

@Composable
fun StatBox(label: String, value: String, sub: String = "") {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE6CFB8), RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7D5A44))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black)
        if (sub.isNotEmpty()) Text(sub, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ACCENT)
    }
}

@Composable
fun SmallButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
        modifier = Modifier.height(38.dp)
    ) { Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun BoardTab(game: Game) {
    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    var boardSize by remember { mutableStateOf(0f) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragEmoji by remember { mutableStateOf("") }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var dragOver by remember { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        // Stats
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) { StatBox("Score", "${game.score}") }
            Box(Modifier.weight(1f)) { StatBox("🪙 Coins", "${game.coins}") }
            Box(Modifier.weight(1f)) { StatBox("⭐ Stars", "${game.stars}") }
            Box(Modifier.weight(1f)) {
                StatBox("⚡ Energy", "${game.energy}/50", if (game.energy >= 50) "Full" else "+1 in ${game.refillCountdown}s")
            }
        }
        // Level bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PANEL)
                .border(1.dp, Color(0xFFE6CCB2), RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Level ${game.level}  •  ⭐ ${game.stars % 3}/3 to Level ${game.level + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("🔥 ${game.streak} day streak", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFECDCC6))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((game.stars % 3) / 3f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(GOLD)
                )
            }
        }
        // Stalls
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StallCard("🥐🍎", "Bakery Stall", game.stars >= 3, "Unlocks at 3 ⭐ (${game.stars}/3)")
            StallCard("💐👑", "Flower & Gold", game.stars >= 6, "Unlocks at 6 ⭐ (${game.stars}/6)")
        }
        // Orders
        Text("Customer Orders (tap order, then tap matching item)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B4C35))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            game.orders.forEachIndexed { idx, ord ->
                val selected = game.selectedOrder == idx
                val ready = game.board.any { it is Cell.Item && it.chain == ord.chain && it.level == ord.level }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Color(0xFFD4EEFF) else Color.White)
                        .border(2.dp, if (selected) BLUE else if (ready) GREEN else Color(0xFFDFBE9F), RoundedCornerShape(14.dp))
                        .clickable { game.tapOrder(idx) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(ord.avatar, fontSize = 20.sp)
                    Text("${ord.emoji} L${ord.level}", fontSize = 16.sp)
                    Text(ord.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("+${ord.coins} 🪙 +1 ⭐", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ACCENT)
                    Text("⏰ ${ord.expiresIn}s", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (ord.expiresIn <= 30) Color.Red else Color(0xFF922B21))
                }
            }
        }
        // Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when (game.bannerKind) {
                        "warning" -> Color(0xFFFFE5E2)
                        "success" -> Color(0xFFE8FCEF)
                        "info" -> Color(0xFFE8F6FF)
                        else -> Color(0xFFFFF9F0)
                    }
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) { Text(game.banner, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        // Board
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFA87653))
                .border(3.dp, Color(0xFF8A5A36), RoundedCornerShape(20.dp))
                .padding(8.dp)
                .onGloballyPositioned { coords ->
                    boardOrigin = coords.positionInWindow()
                    boardSize = coords.size.width.toFloat()
                }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0 until 5) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (col in 0 until 6) {
                            val idx = row * 6 + col
                            val cell = game.board[idx]
                            val isSelected = game.selectedCell == idx
                            val isDragOver = dragOver == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        when {
                                            cell is Cell.Generator -> if (cell.chain == Chain.CRAFT) Color(0xFFD96B98) else if (cell.chain == Chain.TOY) Color(0xFF7C5CBF) else Color(0xFFE08E45)
                                            cell is Cell.Item -> when (cell.level) {
                                                1 -> Color(0xFFFDE08D)
                                                2 -> Color(0xFF8CE9A8)
                                                3 -> Color(0xFF7CC8FF)
                                                4 -> Color(0xFFC58AFF)
                                                else -> Color(0xFFFF9F1C)
                                            }
                                            else -> Color(0xFFF4E6CE)
                                        }
                                    )
                                    .border(
                                        2.dp,
                                        if (isSelected) GREEN else if (isDragOver) ACCENT else Color(0xFFC3A67D),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .pointerInput(idx, cell) {
                                        detectTapGestures(onTap = { game.tapCell(idx) })
                                    }
                                    .pointerInput(idx, cell) {
                                        detectDragGestures(
                                            onDragStart = {
                                                if (cell is Cell.Item) {
                                                    dragFrom = idx
                                                    dragEmoji = cell.chain.emoji(cell.level)
                                                    dragPos = it
                                                }
                                            },
                                            onDragEnd = {
                                                val from = dragFrom
                                                dragFrom = null; dragEmoji = ""; dragOver = null
                                                if (from != null) {
                                                    val cellSize = boardSize / 6f
                                                    val localX = dragPos.x - boardOrigin.x
                                                    val localY = dragPos.y - boardOrigin.y
                                                    val dropCol = (localX / cellSize).toInt()
                                                    val dropRow = (localY / cellSize).toInt()
                                                    if (dropCol in 0..5 && dropRow in 0..4) {
                                                        val to = dropRow * 6 + dropCol
                                                        if (to != from) {
                                                            game.selectedOrder = null
                                                            game.tapCell(from)
                                                            game.tapCell(to)
                                                        }
                                                    }
                                                }
                                            },
                                            onDragCancel = { dragFrom = null; dragEmoji = ""; dragOver = null },
                                            onDrag = { change, dragAmount ->
                                                dragPos += dragAmount
                                                val cellSize = boardSize / 6f
                                                val localX = dragPos.x - boardOrigin.x
                                                val localY = dragPos.y - boardOrigin.y
                                                val dropCol = (localX / cellSize).toInt()
                                                val dropRow = (localY / cellSize).toInt()
                                                dragOver = if (dropCol in 0..5 && dropRow in 0..4) dropRow * 6 + dropCol else null
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                when (cell) {
                                    is Cell.Generator -> Text(cell.chain.crateEmoji, fontSize = 26.sp)
                                    is Cell.Item -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(cell.chain.emoji(cell.level), fontSize = 28.sp)
                                        Text("L${cell.level}", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                    is Cell.Empty -> {}
                                }
                            }
                        }
                    }
                }
            }
            // Drag ghost
            if (dragEmoji.isNotEmpty()) {
                val density = LocalDensity.current
                Box(
                    modifier = Modifier.offset {
                        IntOffset(
                            (dragPos.x - boardOrigin.x).roundToInt() - with(density) { 20.dp.toPx().toInt() },
                            (dragPos.y - boardOrigin.y).roundToInt() - with(density) { 20.dp.toPx().toInt() }
                        )
                    }
                ) { Text(dragEmoji, fontSize = 36.sp) }
            }
        }
        // Controls
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) { SmallButton("↩️ Undo (10 🪙)", enabled = game.lastSnapshot != null && game.coins >= 10) { game.undo() } }
            Box(Modifier.weight(1f)) { SmallButton("⚡ +20 (50 🪙)") { game.buyEnergy() } }
            Box(Modifier.weight(1f)) { SmallButton("🎁 L3 (100 🪙)") { game.buyItem() } }
            Box(Modifier.weight(1f)) { SmallButton(if (game.muted) "🔇 Muted" else "🔊 Sound") { game.muted = !game.muted; game.save() } }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) { SmallButton("🎁 Free Gift") { game.claimGift() } }
            Box(Modifier.weight(1f)) { SmallButton("✂️ Scissors (30 🪙)") { game.buyScissors() } }
            Box(Modifier.weight(1f)) { SmallButton("✨ 2x x5 (50 🪙)") { game.buyDouble() } }
        }
        if (game.scissorMode) Text("✂️ Scissors ready — tap any item to remove it.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ACCENT)
        if (game.doubleMergesLeft > 0) Text("✨ 2x Coins ON — ${game.doubleMergesLeft} merges left!", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ACCENT)
    }
}

@Composable
fun StallCard(icon: String, title: String, unlocked: Boolean, status: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (unlocked) Color(0xFFC6F6D5) else Color(0xFFF2E5D5))
            .border(2.dp, if (unlocked) GREEN else Color(0xFFCDB499), RoundedCornerShape(14.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (unlocked) icon else "🔒", fontSize = 22.sp)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(if (unlocked) "Unlocked! Open!" else status, fontSize = 10.sp)
        }
    }
}

@Composable
fun QuestsTab(game: Game) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🎯 Today's Missions", fontSize = 16.sp, fontWeight = FontWeight.Black)
        MissionRow("Merge 10 times today", game.mergesToday, 10, 40, "merge10", game)
        MissionRow("Serve 3 customers today", game.servesToday, 3, 50, "serve3", game)
        MissionRow("Make a Level 4 item today", game.maxLevelToday, 4, 60, "level4", game)
        Spacer(Modifier.height(8.dp))
        Text("🏆 Achievements", fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text("• Total merges: ${game.totalMerges}  •  Total customers served: ${game.totalServes}", fontSize = 13.sp)
        Text("• Make a Bazaar Trophy (Level 7) for +100 🪙", fontSize = 13.sp)
        Text("• 50 merges for +50 🪙  •  10 customers for +50 🪙", fontSize = 13.sp)
        Text("• Finish a full collection chain for +100 🪙", fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text("🔥 Daily streak: ${game.streak} day(s). Come back tomorrow for a bigger bonus!", fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MissionRow(text: String, progress: Int, target: Int, reward: Int, id: String, game: Game) {
    val done = progress >= target
    val claimed = id in game.missionsClaimed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PANEL)
            .border(1.dp, Color(0xFFE6CCB2), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("${if (claimed) "✅" else if (done) "🎉" else "🎯"} $text", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("${minOf(progress, target)}/$target", fontSize = 13.sp, fontWeight = FontWeight.Black, color = ACCENT)
        Spacer(Modifier.width(8.dp))
        SmallButton(if (claimed) "Claimed ✓" else "Claim +$reward 🪙", enabled = done && !claimed) { game.claimMission(id, reward) }
    }
}

@Composable
fun PassTab(game: Game) {
    val tiers = listOf(
        Triple(3, "+40 🪙", 40 to 0),
        Triple(6, "+20 ⚡", 0 to 20),
        Triple(9, "+80 🪙", 80 to 0),
        Triple(12, "+30 ⚡", 0 to 30),
        Triple(15, "+150 🪙", 150 to 0)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🎟️ Bazaar Pass — earn stars, claim rewards!", fontSize = 16.sp, fontWeight = FontWeight.Black)
        tiers.forEachIndexed { i, (starsNeed, reward, rw) ->
            val reached = game.stars >= starsNeed
            val claimed = i in game.passClaimed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (claimed) GREEN else if (reached) Color(0xFFE8FCEF) else PANEL)
                    .border(1.dp, Color(0xFFE6CCB2), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("⭐ $starsNeed stars — $reward", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (claimed) Color.White else Color.Black, modifier = Modifier.weight(1f))
                SmallButton(if (claimed) "Claimed ✓" else "Claim", enabled = reached && !claimed) { game.claimPass(i, rw.first, rw.second) }
            }
        }
        Text("Tip: serving customers during Bazaar Rush gives double stars!", fontSize = 12.sp)
    }
}

@Composable
fun CollectionTab(game: Game) {
    var got = 0
    var total = 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("📖 Collection — make every item once!", fontSize = 16.sp, fontWeight = FontWeight.Black)
        Chain.values().forEach { chain ->
            Text("${chain.crateEmoji} ${chain.label}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (lv in 1..7) {
                    total++
                    val key = "${chain}_$lv"
                    val has = key in game.collected
                    if (has) got++
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (has) Color(0xFFFFF8E1) else Color(0xFFF0E6D2))
                            .border(2.dp, if (has) GOLD else Color(0xFFD8C3A5), RoundedCornerShape(10.dp))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(if (has) chain.emoji(lv) else "❓", fontSize = 22.sp) }
                }
            }
        }
        Text("Collected $got/$total items. Finish a full chain for +100 🪙!", fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RaceTab(game: Game) {
    val week = (System.currentTimeMillis() / 604800000).toInt()
    val bots = listOf(
        Triple("🧔 Ravi", 120, 0), Triple("👩 Priya", 100, 1),
        Triple("🧑 Arjun", 80, 2), Triple("👵 Meera", 60, 3)
    ).map { (name, base, i) -> name to (base + ((week * 37 + i * 53) % 90) + game.totalServes / 2) }
    val rows = (bots + ("🎪 You" to game.score)).sortedByDescending { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🏆 This Week's Race", fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text("Beat the other bazaar owners! Your score grows as you play.", fontSize = 13.sp)
        rows.forEachIndexed { i, (name, score) ->
            val me = name.contains("You")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (me) Color(0xFFFFE0B2) else PANEL)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${i + 1}. $name${if (me) " (You)" else ""}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("$score pts", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

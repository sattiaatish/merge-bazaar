package com.mergebazaar

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

enum class Chain(val label: String, val crateEmoji: String, val items: List<Pair<String, String>>) {
    FOOD("Food", "📦", listOf(
        "🍎" to "Apple", "🥖" to "Bread", "🥧" to "Pie", "🎂" to "Feast Cake",
        "👑" to "Feast Crown", "🌟" to "Golden Feast", "🏆" to "Bazaar Trophy"
    )),
    CRAFT("Craft", "🧺", listOf(
        "🌸" to "Flower", "💐" to "Bouquet", "🪙" to "Gold Coin", "🏺" to "Gem Vase",
        "💎" to "Royal Jewel", "🪔" to "Golden Lamp", "🏆" to "Bazaar Trophy"
    )),
    TOY("Toy", "🎁", listOf(
        "🎈" to "Balloon", "🪁" to "Kite", "🧸" to "Teddy", "🥁" to "Drum",
        "🤖" to "Robot", "🚀" to "Rocket", "🏆" to "Bazaar Trophy"
    ));

    fun emoji(level: Int) = items[level - 1].first
    fun itemName(level: Int) = items[level - 1].second
}

sealed class Cell {
    object Empty : Cell()
    data class Generator(val chain: Chain) : Cell()
    data class Item(val chain: Chain, val level: Int) : Cell()
}

data class Order(
    val chain: Chain,
    val level: Int,
    val coins: Int,
    val avatar: String,
    var expiresIn: Int = 90
) {
    val emoji: String get() = chain.emoji(level)
    val name: String get() = chain.itemName(level)
}

val CUSTOMERS = listOf("🧑‍🌾", "👵", "🧔", "👩‍🍳", "🤴", "🧙‍♀️")

val STORY = listOf(
    0 to "Chapter 1: Maya's bazaar is empty and dusty. \"Help me bring it back to life! Serve customers and earn stars to rebuild it.\"",
    3 to "Chapter 2: The Bakery Stall is open! \"Fresh bread smells amazing... but who broke my favourite vase? Find out next!\"",
    6 to "Chapter 3: The Flower & Gold Stall sparkles! \"A mysterious customer in a golden mask left a note: meet me at the Night Bazaar...\"",
    9 to "Chapter 4: The Grand Arch is up! \"The masked customer is my old rival Ravi! He wants a merge contest. Beat him in this week's Race!\"",
    12 to "Chapter 5: The Fountain flows! \"Ravi and I are friends again. Together we will open the Night Bazaar. Keep going!\"",
    15 to "Chapter 6: Festival Lights shine! \"The bazaar is alive again, thanks to you. You are the true Bazaar Master!\""
)

fun eventActive(): Boolean {
    val hour = System.currentTimeMillis() / 3600000
    return (hour % 4) == 0L
}

fun eventEndsInMinutes(): Int {
    val now = System.currentTimeMillis()
    val nextHour = ((now / 3600000) + 1) * 3600000
    return (((nextHour - now) / 60000).toInt()).coerceAtLeast(0)
}

class Game(private val prefs: SharedPreferences) {
    var score by mutableStateOf(0)
    var coins by mutableStateOf(0)
    var stars by mutableStateOf(0)
    var energy by mutableStateOf(50)
    var refillCountdown by mutableStateOf(30)
    var board by mutableStateOf<List<Cell>>(List(30) { Cell.Empty })
    var orders by mutableStateOf<List<Order>>(emptyList())
    var selectedCell by mutableStateOf<Int?>(null)
    var selectedOrder by mutableStateOf<Int?>(null)
    var banner by mutableStateOf("Tap a crate (📦, 🧺 or 🎁) to spawn items!")
    var bannerKind by mutableStateOf("normal")
    var muted by mutableStateOf(false)
    var streak by mutableStateOf(0)
    var mergesToday by mutableStateOf(0)
    var servesToday by mutableStateOf(0)
    var maxLevelToday by mutableStateOf(0)
    var totalMerges by mutableStateOf(0)
    var totalServes by mutableStateOf(0)
    var comboCount by mutableStateOf(0)
    var scissorMode by mutableStateOf(false)
    var doubleMergesLeft by mutableStateOf(0)
    var giftNext by mutableStateOf(0L)
    var collected by mutableStateOf<Set<String>>(emptySet())
    var passClaimed by mutableStateOf<Set<Int>>(emptySet())
    var missionsClaimed by mutableStateOf<Set<String>>(emptySet())
    var missionDate by mutableStateOf("")
    var lastBonusDate by mutableStateOf("")
    var lastSeen by mutableStateOf(0L)
    var lastMergeTime by mutableStateOf(0L)
    var lastSnapshot by mutableStateOf<Snapshot?>(null)
    var mergeFx by mutableStateOf<Pair<Int, Int>?>(null) // cell index to coin gain
    val level: Int get() = stars / 3 + 1

    data class Snapshot(val board: List<Cell>, val coins: Int, val score: Int, val stars: Int, val energy: Int, val orders: List<Order>)

    init {
        if (!load()) newGame()
    }

    fun newGame() {
        score = 0; coins = 0; stars = 0; energy = 50; refillCountdown = 30
        totalMerges = 0; totalServes = 0; mergesToday = 0; servesToday = 0
        maxLevelToday = 0; comboCount = 0; scissorMode = false; doubleMergesLeft = 0
        collected = emptySet(); passClaimed = emptySet(); missionsClaimed = emptySet()
        lastSnapshot = null; selectedCell = null; selectedOrder = null
        val b = MutableList<Cell>(30) { Cell.Empty }
        b[0] = Cell.Generator(Chain.FOOD)
        b[6] = Cell.Generator(Chain.CRAFT)
        b[12] = Cell.Generator(Chain.TOY)
        b[1] = Cell.Item(Chain.FOOD, 1); b[2] = Cell.Item(Chain.FOOD, 1)
        b[7] = Cell.Item(Chain.CRAFT, 1); b[8] = Cell.Item(Chain.CRAFT, 1)
        b[13] = Cell.Item(Chain.TOY, 1); b[14] = Cell.Item(Chain.TOY, 1)
        board = b
        orders = listOf(createOrder(1, 2), createOrder(2, 3), createOrder(2, 4))
        banner = "Welcome to Merge Bazaar! Tap 📦, 🧺 or 🎁 to spawn items."
        bannerKind = "normal"
        save()
    }

    fun createOrder(minLevel: Int = 1, maxLevel: Int = 4): Order {
        val chain = Chain.values().random()
        val level = Random.nextInt(minLevel, maxLevel + 1)
        val reward = level * 15 + if (level >= 3) 15 else 0
        return Order(chain, level, reward, CUSTOMERS.random(), 90)
    }

    fun emptyIndices(): List<Int> = board.indices.filter { board[it] is Cell.Empty }

    fun setBanner(text: String, kind: String = "normal") {
        banner = text; bannerKind = kind
    }

    fun takeSnapshot() {
        lastSnapshot = Snapshot(board, coins, score, stars, energy, orders)
    }

    fun undo() {
        val s = lastSnapshot ?: run { setBanner("Nothing to undo yet.", "info"); return }
        if (coins < 10) { setBanner("Need 10 🪙 to undo.", "warning"); return }
        coins -= 10
        board = s.board; score = s.score; stars = s.stars; energy = s.energy; orders = s.orders
        lastSnapshot = null; selectedCell = null; selectedOrder = null
        setBanner("↩️ Undid last move (-10 🪙).", "info")
        save()
    }

    fun tapGenerator(index: Int) {
        val gen = board[index] as? Cell.Generator ?: return
        selectedCell = null
        val empty = emptyIndices()
        if (empty.isEmpty()) { setBanner("⚠️ Board Full! Merge two identical items or serve an order.", "warning"); return }
        val target = empty.random()
        val nb = board.toMutableList()
        nb[target] = Cell.Item(gen.chain, 1)
        board = nb
        mergeFx = null
        val info = gen.chain.items[0]
        setBanner(if (emptyIndices().isEmpty()) "Spawned ${info.first} ${info.second}! ⚠️ Board is now FULL!" else "Spawned Level 1 ${info.first} ${info.second}! Tap two matching items to merge.")
        save()
    }

    fun tapOrder(orderIdx: Int) {
        if (selectedOrder == orderIdx) {
            selectedOrder = null
            setBanner("Order deselected. Tap items to merge or tap a crate to spawn.")
        } else {
            selectedOrder = orderIdx; selectedCell = null
            val ord = orders[orderIdx]
            setBanner("Order selected: Tap a Level ${ord.level} ${ord.emoji} ${ord.name} on the board to serve it!", "info")
        }
    }

    fun tapCell(index: Int) {
        val cell = board[index]
        if (selectedOrder != null) {
            val ord = orders[selectedOrder!!]
            if (cell is Cell.Item && cell.chain == ord.chain && cell.level == ord.level) {
                serveOrder(selectedOrder!!, index); return
            } else if (cell is Cell.Generator) {
                selectedOrder = null; tapGenerator(index); return
            } else {
                setBanner("That doesn't match the order (needs L${ord.level} ${ord.emoji} ${ord.name}).", "warning"); return
            }
        }
        if (cell is Cell.Empty) {
            val sel = selectedCell
            if (sel != null) {
                val nb = board.toMutableList()
                nb[index] = nb[sel]; nb[sel] = Cell.Empty
                board = nb; selectedCell = null
                setBanner("Moved item to an empty tile.")
                save()
            }
            return
        }
        if (cell is Cell.Generator) { tapGenerator(index); return }
        if (cell is Cell.Item) {
            if (scissorMode) {
                scissorMode = false
                val nb = board.toMutableList(); nb[index] = Cell.Empty; board = nb
                selectedCell = null
                setBanner("✂️ Item removed. Scissors used up.", "info")
                save(); return
            }
            val sel = selectedCell
            if (sel == null) {
                selectedCell = index
                setBanner("Selected L${cell.level} ${cell.chain.emoji(cell.level)} ${cell.chain.itemName(cell.level)}. Tap another one to merge.")
                return
            }
            if (sel == index) { selectedCell = null; setBanner("Item deselected."); return }
            val first = board[sel]
            if (first is Cell.Item && first.chain == cell.chain && first.level == cell.level) {
                if (cell.level >= 7) { selectedCell = index; setBanner("Max level reached! Serve it when a customer orders it.", "info"); return }
                if (energy < 1) { setBanner("⚠️ Not enough Energy! Wait for it to refill (+1 every 30s).", "warning"); return }
                takeSnapshot()
                energy -= 1
                val nextLevel = cell.level + 1
                var coinGain = nextLevel * 3
                val scoreGain = nextLevel * 10
                val now = System.currentTimeMillis()
                comboCount = if (now - lastMergeTime < 10000) comboCount + 1 else 1
                lastMergeTime = now
                val mega = comboCount >= 6
                val isCombo = comboCount >= 3
                if (mega) coinGain = nextLevel * 9 else if (isCombo) coinGain *= 2
                if (doubleMergesLeft > 0) { coinGain *= 2; doubleMergesLeft -= 1 }
                val nb = board.toMutableList()
                nb[index] = Cell.Item(cell.chain, nextLevel); nb[sel] = Cell.Empty
                board = nb; selectedCell = null
                coins += coinGain; score += scoreGain
                totalMerges += 1; mergesToday += 1
                maxLevelToday = maxOf(maxLevelToday, nextLevel)
                collected = collected + "${cell.chain}_${nextLevel}"
                mergeFx = index to coinGain
                val comboText = if (mega) " 🔥🔥 MEGA COMBO x3!" else if (isCombo) " 🔥 COMBO x2!" else ""
                setBanner("✨ Merged into L$nextLevel ${cell.chain.emoji(nextLevel)} ${cell.chain.itemName(nextLevel)}! (+$coinGain 🪙, +$scoreGain Score, -1 ⚡)$comboText", "success")
                save()
            } else {
                selectedCell = index
                setBanner("Switched selection to L${cell.level} ${cell.chain.emoji(cell.level)} ${cell.chain.itemName(cell.level)}.")
            }
        }
    }

    fun serveOrder(orderIdx: Int, boardIdx: Int) {
        val ord = orders[orderIdx]
        takeSnapshot()
        val nb = board.toMutableList(); nb[boardIdx] = Cell.Empty; board = nb
        val prevStars = stars
        val prevLevel = prevStars / 3 + 1
        val urgentBonus = if (ord.expiresIn <= 30) 10 else 0
        val rush = eventActive()
        coins += ord.coins + urgentBonus
        stars += if (rush) 2 else 1
        score += ord.coins * 2 + 50
        totalServes += 1; servesToday += 1
        selectedOrder = null; selectedCell = null
        val maxOrderLvl = if (stars >= 10) 7 else if (stars >= 4) 5 else 4
        val newOrders = orders.toMutableList()
        newOrders[orderIdx] = createOrder(1, maxOrderLvl)
        orders = newOrders
        val newLevel = stars / 3 + 1
        var levelUpMsg = ""
        if (newLevel > prevLevel) {
            energy = minOf(50, energy + 20)
            levelUpMsg = " 🎉 LEVEL UP! You are now Level $newLevel! +20 ⚡!"
        }
        var unlockMsg = ""
        if (prevStars < 3 && stars >= 3) unlockMsg = " 🎉 Unlocked Stall 1: Bakery Stall!"
        else if (prevStars < 6 && stars >= 6) unlockMsg = " 🎉 Unlocked Stall 2: Flower & Gold Stall!"
        setBanner("✅ Served ${ord.emoji} ${ord.name}! +${ord.coins + urgentBonus} 🪙 and +${if (rush) 2 else 1} ⭐!${if (rush) " 🔥 Bazaar Rush — double stars!" else ""}${if (urgentBonus > 0) " ⏰ Fast serve +10 🪙!" else ""}$unlockMsg$levelUpMsg", "success")
        save()
    }

    fun tick() {
        // 1 second tick: energy refill + order countdowns
        if (energy < 50) {
            refillCountdown -= 1
            if (refillCountdown <= 0) { energy += 1; refillCountdown = 30 }
        } else refillCountdown = 30
        var expired = false
        val newOrders = orders.map {
            val o = it.copy(expiresIn = it.expiresIn - 1)
            if (o.expiresIn <= 0) { expired = true; createOrder(1, if (stars >= 10) 7 else if (stars >= 4) 5 else 4) } else o
        }
        if (expired) {
            orders = newOrders
            setBanner("⏰ A customer got impatient and left! A new order arrived. Serve fast for +10 🪙!", "warning")
        } else if (orders.any { it.expiresIn != orders[orders.indexOf(it)].expiresIn }) {
            orders = newOrders
        } else {
            orders = newOrders
        }
    }

    fun storyText(): String {
        var s = STORY[0]
        STORY.forEach { if (stars >= it.first) s = it }
        return s.second
    }

    fun buyEnergy() {
        if (energy >= 50) { setBanner("Energy is already full!", "info"); return }
        if (coins < 50) { setBanner("Need 50 🪙 for +20 Energy.", "warning"); return }
        coins -= 50; energy = minOf(50, energy + 20)
        setBanner("⚡ Bought +20 Energy for 50 🪙!", "success"); save()
    }

    fun buyItem() {
        if (coins < 100) { setBanner("Need 100 🪙 for a Level 3 item.", "warning"); return }
        val empty = emptyIndices()
        if (empty.isEmpty()) { setBanner("Board is full! Merge or serve first.", "warning"); return }
        val chain = Chain.values().random()
        val idx = empty.random()
        val nb = board.toMutableList(); nb[idx] = Cell.Item(chain, 3); board = nb
        coins -= 100
        setBanner("🎁 Bought a Level 3 ${chain.emoji(3)} ${chain.itemName(3)} for 100 🪙!", "success"); save()
    }

    fun claimGift(): String {
        val now = System.currentTimeMillis()
        if (now < giftNext) {
            val mins = ((giftNext - now) / 60000 + 1).toInt()
            setBanner("🎁 Next free gift in $mins min. Come back soon!", "info")
            return ""
        }
        giftNext = now + 15 * 60000
        return if (Random.nextBoolean()) {
            energy = minOf(50, energy + 15)
            setBanner("🎁 Free Gift! +15 ⚡ Energy!", "success"); save(); "energy"
        } else {
            coins += 30
            setBanner("🎁 Free Gift! +30 🪙 Coins!", "success"); save(); "coins"
        }
    }

    fun buyScissors() {
        if (scissorMode) { scissorMode = false; setBanner("Scissors put away.", "info"); return }
        if (coins < 30) { setBanner("Need 30 🪙 for Scissors.", "warning"); return }
        coins -= 30; scissorMode = true
        setBanner("✂️ Scissors ready! Tap any item to remove it.", "info"); save()
    }

    fun buyDouble() {
        if (coins < 50) { setBanner("Need 50 🪙 for 2x Coins.", "warning"); return }
        coins -= 50; doubleMergesLeft += 5
        setBanner("✨ 2x Coins ON! Your next 5 merges give double coins!", "success"); save()
    }

    fun claimMission(id: String, reward: Int) {
        if (id in missionsClaimed) return
        missionsClaimed = missionsClaimed + id
        coins += reward
        setBanner("🎉 Mission done! +$reward 🪙 Coins!", "success"); save()
    }

    fun claimPass(tier: Int, coinsReward: Int, energyReward: Int) {
        if (tier in passClaimed) return
        passClaimed = passClaimed + tier
        coins += coinsReward
        energy = minOf(50, energy + energyReward)
        setBanner("🎟️ Bazaar Pass reward claimed!", "success"); save()
    }

    fun checkDaily() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        if (missionDate != today) {
            missionDate = today; mergesToday = 0; servesToday = 0; maxLevelToday = 0; missionsClaimed = emptySet()
        }
        if (lastBonusDate != today) {
            val yesterday = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date(System.currentTimeMillis() - 86400000))
            streak = if (lastBonusDate == yesterday) streak + 1 else 1
            lastBonusDate = today
            val bonus = minOf(30 + (streak - 1) * 10, 100)
            coins += bonus
            setBanner("🎁 Daily Bonus! +$bonus 🪙 Coins. 🔥 $streak day streak!", "success")
        }
        if (lastSeen > 0) {
            val hoursAway = (System.currentTimeMillis() - lastSeen) / 3600000.0
            val idle = minOf(50, (hoursAway * 5).toInt())
            if (idle > 0) {
                coins += idle
                setBanner("👋 Welcome back! Your bazaar earned +$idle 🪙 while you were away!", "success")
            }
        }
        save()
    }

    fun save() {
        val boardStr = board.joinToString(",") {
            when (it) {
                is Cell.Empty -> "E"
                is Cell.Generator -> "G:${it.chain.name}"
                is Cell.Item -> "I:${it.chain.name}:${it.level}"
            }
        }
        val orderStr = orders.joinToString(";") { "${it.chain.name}:${it.level}:${it.coins}:${it.avatar}:${it.expiresIn}" }
        prefs.edit()
            .putInt("score", score).putInt("coins", coins).putInt("stars", stars)
            .putInt("energy", energy).putInt("streak", streak)
            .putInt("mergesToday", mergesToday).putInt("servesToday", servesToday)
            .putInt("maxLevelToday", maxLevelToday).putInt("totalMerges", totalMerges)
            .putInt("totalServes", totalServes).putInt("doubleMergesLeft", doubleMergesLeft)
            .putLong("giftNext", giftNext).putLong("lastSeen", System.currentTimeMillis())
            .putBoolean("muted", muted)
            .putString("board", boardStr).putString("orders", orderStr)
            .putString("collected", collected.joinToString(","))
            .putString("passClaimed", passClaimed.joinToString(","))
            .putString("missionsClaimed", missionsClaimed.joinToString(","))
            .putString("missionDate", missionDate).putString("lastBonusDate", lastBonusDate)
            .apply()
    }

    private fun load(): Boolean {
        if (!prefs.contains("board")) return false
        return try {
            score = prefs.getInt("score", 0); coins = prefs.getInt("coins", 0)
            stars = prefs.getInt("stars", 0); energy = prefs.getInt("energy", 50)
            streak = prefs.getInt("streak", 0)
            mergesToday = prefs.getInt("mergesToday", 0); servesToday = prefs.getInt("servesToday", 0)
            maxLevelToday = prefs.getInt("maxLevelToday", 0); totalMerges = prefs.getInt("totalMerges", 0)
            totalServes = prefs.getInt("totalServes", 0); doubleMergesLeft = prefs.getInt("doubleMergesLeft", 0)
            giftNext = prefs.getLong("giftNext", 0); lastSeen = prefs.getLong("lastSeen", 0)
            muted = prefs.getBoolean("muted", false)
            missionDate = prefs.getString("missionDate", "") ?: ""
            lastBonusDate = prefs.getString("lastBonusDate", "") ?: ""
            collected = (prefs.getString("collected", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet()
            passClaimed = (prefs.getString("passClaimed", "") ?: "").split(",").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }.toSet()
            missionsClaimed = (prefs.getString("missionsClaimed", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet()
            val b = MutableList<Cell>(30) { Cell.Empty }
            (prefs.getString("board", "") ?: "").split(",").forEachIndexed { i, s ->
                if (i >= 30) return@forEachIndexed
                val parts = s.split(":")
                b[i] = when (parts[0]) {
                    "G" -> Cell.Generator(Chain.valueOf(parts[1]))
                    "I" -> Cell.Item(Chain.valueOf(parts[1]), parts[2].toInt())
                    else -> Cell.Empty
                }
            }
            board = b
            orders = (prefs.getString("orders", "") ?: "").split(";").filter { it.isNotEmpty() }.map {
                val p = it.split(":")
                Order(Chain.valueOf(p[0]), p[1].toInt(), p[2].toInt(), p[3], p[4].toInt())
            }.ifEmpty { listOf(createOrder(1, 2), createOrder(2, 3), createOrder(2, 4)) }
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        fun create(context: Context): Game {
            val prefs = context.getSharedPreferences("merge-bazaar", Context.MODE_PRIVATE)
            return Game(prefs)
        }
    }
}

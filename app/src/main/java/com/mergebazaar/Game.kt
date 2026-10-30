package com.mergebazaar

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

enum class Chain(
    val label: String,
    val crateEmoji: String,
    val crateName: String,
    val items: List<Pair<String, String>>
) {
    FOOD(
        "Food",
        "📦",
        "Food Crate",
        listOf(
            "🍎" to "Apple",
            "🥖" to "Bread",
            "🥧" to "Pie",
            "🎂" to "Feast Cake",
            "👑" to "Feast Crown",
            "🌟" to "Golden Feast",
            "🏆" to "Bazaar Trophy"
        )
    ),
    CRAFT(
        "Craft",
        "🧺",
        "Craft Basket",
        listOf(
            "🌸" to "Flower",
            "💐" to "Bouquet",
            "🪙" to "Gold Coin",
            "🏺" to "Gem Vase",
            "💎" to "Royal Jewel",
            "🪔" to "Golden Lamp",
            "🏆" to "Bazaar Trophy"
        )
    ),
    TOY(
        "Toy",
        "🎁",
        "Toy Box",
        listOf(
            "🎈" to "Balloon",
            "🪁" to "Kite",
            "🧸" to "Teddy",
            "🥁" to "Drum",
            "🤖" to "Robot",
            "🚀" to "Rocket",
            "🏆" to "Bazaar Trophy"
        )
    );

    fun emoji(level: Int): String = items[(level - 1).coerceIn(0, items.lastIndex)].first
    fun itemName(level: Int): String = items[(level - 1).coerceIn(0, items.lastIndex)].second
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
    val expiresIn: Int = 90
) {
    val emoji: String get() = chain.emoji(level)
    val name: String get() = chain.itemName(level)
}

data class StallInfo(
    val id: String,
    val icon: String,
    val title: String,
    val starsRequired: Int,
    val description: String
)

data class FloatingFx(
    val id: Long,
    val cellIndex: Int,
    val text: String,
    val subText: String = ""
)

val CUSTOMERS = listOf("🧑‍🌾", "👵", "🧔", "👩‍🍳", "🤴", "🧙‍♀️")

val STALLS = listOf(
    StallInfo("bakery", "🥐🍎", "Bakery Stall", 3, "Fresh warm bread & sweet pies"),
    StallInfo("flower", "💐👑", "Flower & Gold", 6, "Sparkling bouquets & royal crafts"),
    StallInfo("arch", "🏛️✨", "Grand Arch", 9, "Welcoming travelers from afar"),
    StallInfo("fountain", "⛲💎", "Starlight Fountain", 12, "Heart of the Night Bazaar"),
    StallInfo("lanterns", "🏮🎉", "Festival Plaza", 15, "Grand celebration of Bazaar Masters")
)

val STORY = listOf(
    0 to "Chapter 1: Maya's bazaar is empty and dusty. "Help me bring it back to life! Serve customers and earn stars to rebuild it."",
    3 to "Chapter 2: The Bakery Stall is open! "Fresh bread smells amazing... but who broke my favourite vase? Find out next!"",
    6 to "Chapter 3: The Flower & Gold Stall sparkles! "A mysterious customer in a golden mask left a note: meet me at the Night Bazaar..."",
    9 to "Chapter 4: The Grand Arch is up! "The masked customer is my old rival Ravi! He wants a merge contest. Beat him in this week's Race!"",
    12 to "Chapter 5: The Fountain flows! "Ravi and I are friends again. Together we will open the Night Bazaar. Keep going!"",
    15 to "Chapter 6: Festival Lights shine! "The bazaar is alive again, thanks to you. You are the true Bazaar Master!""
)

fun eventActive(): Boolean {
    val hour = System.currentTimeMillis() / 3600000L
    return (hour % 4L) == 0L
}

fun eventEndsInMinutes(): Int {
    val now = System.currentTimeMillis()
    val nextHour = ((now / 3600000L) + 1L) * 3600000L
    return (((nextHour - now) / 60000L).toInt()).coerceAtLeast(1)
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
    var soundEvent by mutableStateOf(0L to "")
    var muted by mutableStateOf(false)
    var streak by mutableStateOf(1)
    var mergesToday by mutableStateOf(0)
    var servesToday by mutableStateOf(0)
    var maxLevelToday by mutableStateOf(1)
    var totalMerges by mutableStateOf(0)
    var totalServes by mutableStateOf(0)
    var comboCount by mutableStateOf(0)
    var scissorMode by mutableStateOf(false)
    var doubleMergesLeft by mutableStateOf(0)
    var giftNext by mutableStateOf(0L)
    var collected by mutableStateOf<Set<String>>(emptySet())
    var passClaimed by mutableStateOf<Set<Int>>(emptySet())
    var missionsClaimed by mutableStateOf<Set<String>>(emptySet())
    var achievementsClaimed by mutableStateOf<Set<String>>(emptySet())
    var missionDate by mutableStateOf("")
    var lastBonusDate by mutableStateOf("")
    var lastSeen by mutableStateOf(0L)
    var lastMergeTime by mutableStateOf(0L)
    var lastSnapshot by mutableStateOf<Snapshot?>(null)

    // Animation triggers for the board
    var lastMergedCell by mutableStateOf<Pair<Int, Long>?>(null)
    var lastSpawnedCell by mutableStateOf<Pair<Int, Long>?>(null)
    var floatingFx by mutableStateOf<FloatingFx?>(null)
    var welcomeBackCoins by mutableStateOf(0)

    val level: Int get() = stars / 3 + 1

    data class Snapshot(
        val board: List<Cell>,
        val coins: Int,
        val score: Int,
        val stars: Int,
        val energy: Int,
        val orders: List<Order>
    )

    init {
        if (!load()) {
            newGame()
        }
    }

    private fun emitSound(kind: String) {
        soundEvent = System.currentTimeMillis() to kind
    }

    fun triggerFloatingFx(cellIndex: Int, text: String, subText: String = "") {
        floatingFx = FloatingFx(
            id = System.currentTimeMillis() + Random.nextLong(1000),
            cellIndex = cellIndex,
            text = text,
            subText = subText
        )
    }

    fun newGame() {
        score = 0
        coins = 50
        stars = 0
        energy = 50
        refillCountdown = 30
        totalMerges = 0
        totalServes = 0
        mergesToday = 0
        servesToday = 0
        maxLevelToday = 1
        comboCount = 0
        scissorMode = false
        doubleMergesLeft = 0
        collected = setOf("FOOD_1", "CRAFT_1", "TOY_1")
        passClaimed = emptySet()
        missionsClaimed = emptySet()
        achievementsClaimed = emptySet()
        lastSnapshot = null
        selectedCell = null
        selectedOrder = null
        lastMergedCell = null
        lastSpawnedCell = null
        floatingFx = null

        val b = MutableList<Cell>(30) { Cell.Empty }
        b[0] = Cell.Generator(Chain.FOOD)
        b[6] = Cell.Generator(Chain.CRAFT)
        b[12] = Cell.Generator(Chain.TOY)
        b[1] = Cell.Item(Chain.FOOD, 1)
        b[2] = Cell.Item(Chain.FOOD, 1)
        b[7] = Cell.Item(Chain.CRAFT, 1)
        b[8] = Cell.Item(Chain.CRAFT, 1)
        b[13] = Cell.Item(Chain.TOY, 1)
        b[14] = Cell.Item(Chain.TOY, 1)
        board = b
        orders = listOf(createOrder(1, 2), createOrder(2, 3), createOrder(2, 4))
        banner = "Tap 📦, 🧺 or 🎁 to spawn, or drag/tap matching items to merge!"
        bannerKind = "normal"
        save()
    }

    fun createOrder(minLevel: Int = 1, maxLevel: Int = 4): Order {
        val chain = Chain.values().random()
        val lvl = Random.nextInt(minLevel, maxLevel + 1).coerceIn(1, 7)
        val reward = lvl * 18 + if (lvl >= 3) 20 else 0
        return Order(chain, lvl, reward, CUSTOMERS.random(), 90)
    }

    fun emptyIndices(): List<Int> = board.indices.filter { board[it] is Cell.Empty }

    fun setBanner(text: String, kind: String = "normal") {
        banner = text
        bannerKind = kind
    }

    fun takeSnapshot() {
        lastSnapshot = Snapshot(board.toList(), coins, score, stars, energy, orders.toList())
    }

    fun undo() {
        val s = lastSnapshot ?: run {
            setBanner("Nothing to undo yet.", "info")
            return
        }
        if (coins < 10) {
            setBanner("Need 10 🪙 to undo.", "warning")
            return
        }
        coins -= 10
        board = s.board
        score = s.score
        stars = s.stars
        energy = s.energy
        orders = s.orders
        lastSnapshot = null
        selectedCell = null
        selectedOrder = null
        setBanner("↩️ Undid last move (-10 🪙).", "info")
        save()
    }

    fun tapGenerator(index: Int) {
        val gen = board[index] as? Cell.Generator ?: return
        selectedCell = null
        selectedOrder = null
        val empty = emptyIndices()
        if (empty.isEmpty()) {
            setBanner("⚠️ Board Full! Merge two identical items or serve an order.", "warning")
            return
        }
        if (energy < 1) {
            setBanner("⚠️ Out of Energy! Refills +1 every 30s or refill in controls.", "warning")
            return
        }
        takeSnapshot()
        energy -= 1
        val spawnLevel = if (Random.nextFloat() < 0.12f) 2 else 1
        val target = empty.random()
        val nb = board.toMutableList()
        nb[target] = Cell.Item(gen.chain, spawnLevel)
        board = nb
        collected = collected + "${gen.chain}_$spawnLevel"
        lastSpawnedCell = target to System.currentTimeMillis()
        emitSound("spawn")
        val emoji = gen.chain.emoji(spawnLevel)
        val name = gen.chain.itemName(spawnLevel)
        setBanner(
            if (emptyIndices().isEmpty()) "Spawned L$spawnLevel $emoji $name! ⚠️ Board is now FULL!"
            else "Spawned L$spawnLevel $emoji $name! (-1 ⚡)"
        )
        save()
    }

    fun tapOrder(orderIdx: Int) {
        if (orderIdx !in orders.indices) return
        val ord = orders[orderIdx]
        // If player already selected a matching cell on the board, immediately serve it!
        val sel = selectedCell
        if (sel != null) {
            val cell = board.getOrNull(sel)
            if (cell is Cell.Item && cell.chain == ord.chain && cell.level == ord.level) {
                serveOrder(orderIdx, sel)
                return
            }
        }
        // Or if there is a matching item on the board and player taps an already-selected order, auto-serve!
        val matchingIdx = board.indexOfFirst { it is Cell.Item && it.chain == ord.chain && it.level == ord.level }
        if (selectedOrder == orderIdx && matchingIdx != -1) {
            serveOrder(orderIdx, matchingIdx)
            return
        }
        if (selectedOrder == orderIdx) {
            selectedOrder = null
            setBanner("Order deselected. Tap items to merge or tap a crate to spawn.")
        } else {
            selectedOrder = orderIdx
            selectedCell = null
            if (matchingIdx != -1) {
                setBanner("Ready to serve! Tap the highlighted L${ord.level} ${ord.emoji} or tap the order again!", "success")
            } else {
                setBanner("Order needs L${ord.level} ${ord.emoji} ${ord.name}. Merge items to craft it!", "info")
            }
        }
    }

    fun moveOrMerge(fromIdx: Int, toIdx: Int) {
        if (fromIdx == toIdx || fromIdx !in board.indices || toIdx !in board.indices) return
        val source = board[fromIdx] as? Cell.Item ?: return
        val dest = board[toIdx]
        selectedOrder = null

        when (dest) {
            is Cell.Empty -> {
                val nb = board.toMutableList()
                nb[toIdx] = source
                nb[fromIdx] = Cell.Empty
                board = nb
                selectedCell = null
                setBanner("Moved ${source.chain.emoji(source.level)} ${source.chain.itemName(source.level)}.")
                save()
            }
            is Cell.Item -> {
                if (dest.chain == source.chain && dest.level == source.level) {
                    performMerge(fromIdx, toIdx, source)
                } else {
                    // Swap items smoothly on drag-and-drop
                    val nb = board.toMutableList()
                    nb[toIdx] = source
                    nb[fromIdx] = dest
                    board = nb
                    selectedCell = toIdx
                    setBanner("Swapped ${source.chain.emoji(source.level)} with ${dest.chain.emoji(dest.level)}.")
                    save()
                }
            }
            is Cell.Generator -> {
                selectedCell = fromIdx
            }
        }
    }

    private fun performMerge(fromIdx: Int, toIdx: Int, cell: Cell.Item) {
        if (cell.level >= 7) {
            selectedCell = toIdx
            setBanner("🏆 Level 7 Bazaar Trophy is max level! Serve it or collect it.", "info")
            return
        }
        if (energy < 1) {
            setBanner("⚠️ Not enough Energy! Wait for refill (+1/30s) or get +20 ⚡.", "warning")
            return
        }
        takeSnapshot()
        energy -= 1
        val nextLevel = cell.level + 1
        var coinGain = nextLevel * 4
        val scoreGain = nextLevel * 12
        val now = System.currentTimeMillis()
        comboCount = if (now - lastMergeTime < 10000L) comboCount + 1 else 1
        lastMergeTime = now
        val mega = comboCount >= 6
        val isCombo = comboCount >= 3
        if (mega) coinGain = nextLevel * 10 else if (isCombo) coinGain *= 2
        if (doubleMergesLeft > 0) {
            coinGain *= 2
            doubleMergesLeft -= 1
        }
        if (nextLevel == 7) {
            coinGain += 100 // Trophy bonus
        }

        val nb = board.toMutableList()
        nb[toIdx] = Cell.Item(cell.chain, nextLevel)
        nb[fromIdx] = Cell.Empty
        board = nb
        selectedCell = null

        coins += coinGain
        score += scoreGain
        totalMerges += 1
        mergesToday += 1
        maxLevelToday = maxOf(maxLevelToday, nextLevel)

        val wasFullChainBefore = (1..7).all { "${cell.chain}_$it" in collected }
        collected = collected + "${cell.chain}_$nextLevel"
        val isFullChainNow = (1..7).all { "${cell.chain}_$it" in collected }
        var chainBonusText = ""
        if (!wasFullChainBefore && isFullChainNow) {
            coins += 100
            chainBonusText = " 🎉 Full ${cell.chain.label} Chain +100 🪙!"
        }

        lastMergedCell = toIdx to now
        val comboTag = when {
            mega -> "MEGA x3!"
            isCombo -> "COMBO x2!"
            comboCount >= 2 -> "${comboCount}x Chain"
            else -> ""
        }
        triggerFloatingFx(toIdx, "+$coinGain 🪙", comboTag)
        emitSound("merge")

        val comboBanner = if (mega) " 🔥🔥 MEGA COMBO x3!" else if (isCombo) " 🔥 COMBO x2!" else ""
        setBanner(
            "✨ Merged L$nextLevel ${cell.chain.emoji(nextLevel)} ${cell.chain.itemName(nextLevel)}! +$coinGain 🪙$comboBanner$chainBonusText",
            "success"
        )
        save()
    }

    fun tapCell(index: Int) {
        if (index !in board.indices) return
        val cell = board[index]

        if (selectedOrder != null) {
            val ordIdx = selectedOrder!!
            if (ordIdx in orders.indices) {
                val ord = orders[ordIdx]
                if (cell is Cell.Item && cell.chain == ord.chain && cell.level == ord.level) {
                    serveOrder(ordIdx, index)
                    return
                } else if (cell is Cell.Generator) {
                    selectedOrder = null
                    tapGenerator(index)
                    return
                } else {
                    selectedOrder = null
                }
            } else {
                selectedOrder = null
            }
        }

        if (cell is Cell.Empty) {
            val sel = selectedCell
            if (sel != null && board[sel] is Cell.Item) {
                moveOrMerge(sel, index)
            }
            return
        }

        if (cell is Cell.Generator) {
            tapGenerator(index)
            return
        }

        if (cell is Cell.Item) {
            if (scissorMode) {
                takeSnapshot()
                scissorMode = false
                val nb = board.toMutableList()
                nb[index] = Cell.Empty
                board = nb
                selectedCell = null
                setBanner("✂️ Removed ${cell.chain.emoji(cell.level)} ${cell.chain.itemName(cell.level)}.", "info")
                save()
                return
            }

            // Check if this item matches a selected or ready order when double-tapped or tapped
            val sel = selectedCell
            if (sel == null) {
                selectedCell = index
                val matchingOrderIdx = orders.indexOfFirst { it.chain == cell.chain && it.level == cell.level }
                if (matchingOrderIdx != -1) {
                    setBanner(
                        "Selected L${cell.level} ${cell.chain.emoji(cell.level)} — Tap matching customer above to serve, or tap another ${cell.chain.emoji(cell.level)} to merge!",
                        "info"
                    )
                } else {
                    setBanner("Selected L${cell.level} ${cell.chain.emoji(cell.level)} ${cell.chain.itemName(cell.level)}. Tap another to merge!")
                }
                return
            }

            if (sel == index) {
                // If tapping the same item again and a customer wants it, serve it right away for snappy mobile feel!
                val matchingOrderIdx = orders.indexOfFirst { it.chain == cell.chain && it.level == cell.level }
                if (matchingOrderIdx != -1) {
                    serveOrder(matchingOrderIdx, index)
                } else {
                    selectedCell = null
                    setBanner("Item deselected.")
                }
                return
            }

            val first = board[sel]
            if (first is Cell.Item && first.chain == cell.chain && first.level == cell.level) {
                performMerge(sel, index, cell)
            } else {
                selectedCell = index
                setBanner("Selected L${cell.level} ${cell.chain.emoji(cell.level)} ${cell.chain.itemName(cell.level)}.")
            }
        }
    }

    fun serveOrder(orderIdx: Int, boardIdx: Int) {
        if (orderIdx !in orders.indices || boardIdx !in board.indices) return
        val ord = orders[orderIdx]
        takeSnapshot()
        val nb = board.toMutableList()
        nb[boardIdx] = Cell.Empty
        board = nb

        val prevStars = stars
        val prevLevel = prevStars / 3 + 1
        val urgentBonus = if (ord.expiresIn <= 30) 10 else 0
        val rush = eventActive()
        val totalCoinGain = ord.coins + urgentBonus
        val starGain = if (rush) 2 else 1

        coins += totalCoinGain
        stars += starGain
        score += ord.coins * 2 + 50
        totalServes += 1
        servesToday += 1
        selectedOrder = null
        selectedCell = null

        val maxOrderLvl = if (stars >= 10) 6 else if (stars >= 4) 5 else 4
        val newOrders = orders.toMutableList()
        newOrders[orderIdx] = createOrder(1, maxOrderLvl)
        orders = newOrders

        val newLevel = stars / 3 + 1
        var levelUpMsg = ""
        if (newLevel > prevLevel) {
            energy = minOf(50, energy + 20)
            levelUpMsg = " 🎉 LEVEL $newLevel! +20 ⚡!"
        }
        val unlockedStall = STALLS.firstOrNull { prevStars < it.starsRequired && stars >= it.starsRequired }
        val unlockMsg = if (unlockedStall != null) " 🎪 Rebuilt ${unlockedStall.title}!" else ""

        triggerFloatingFx(boardIdx, "+$totalCoinGain 🪙", "+$starGain ⭐")
        emitSound("serve")

        setBanner(
            "✅ Served ${ord.emoji} ${ord.name}! +$totalCoinGain 🪙 +$starGain ⭐${if (rush) " (2x Rush!)" else ""}$unlockMsg$levelUpMsg",
            "success"
        )
        save()
    }

    fun tick() {
        if (energy < 50) {
            refillCountdown -= 1
            if (refillCountdown <= 0) {
                energy += 1
                refillCountdown = 30
            }
        } else {
            refillCountdown = 30
        }

        var expired = false
        val maxOrderLvl = if (stars >= 10) 6 else if (stars >= 4) 5 else 4
        val newOrders = orders.map { ord ->
            val nextTime = ord.expiresIn - 1
            if (nextTime <= 0) {
                expired = true
                createOrder(1, maxOrderLvl)
            } else {
                ord.copy(expiresIn = nextTime)
            }
        }
        orders = newOrders
        if (expired) {
            setBanner("⏰ A customer left and a new order arrived! Serve fast for +10 🪙!", "warning")
        }
    }

    fun storyChapterTitle(): String {
        var idx = 1
        STORY.forEachIndexed { i, pair ->
            if (stars >= pair.first) idx = i + 1
        }
        return "Chapter $idx of ${STORY.size}"
    }

    fun storyText(): String {
        var s = STORY[0]
        STORY.forEach { if (stars >= it.first) s = it }
        return s.second
    }

    fun nextStoryStars(): Int? {
        return STORY.firstOrNull { it.first > stars }?.first
    }

    fun buyEnergy() {
        if (energy >= 50) {
            setBanner("⚡ Energy is already full (50/50)!", "info")
            return
        }
        if (coins < 50) {
            setBanner("Need 50 🪙 for +20 Energy.", "warning")
            return
        }
        coins -= 50
        energy = minOf(50, energy + 20)
        setBanner("⚡ Refilled +20 Energy for 50 🪙!", "success")
        save()
    }

    fun buyItem() {
        if (coins < 100) {
            setBanner("Need 100 🪙 for a Level 3 Mystery Box.", "warning")
            return
        }
        val empty = emptyIndices()
        if (empty.isEmpty()) {
            setBanner("Board is full! Merge or serve an item first.", "warning")
            return
        }
        val chain = Chain.values().random()
        val idx = empty.random()
        val nb = board.toMutableList()
        nb[idx] = Cell.Item(chain, 3)
        board = nb
        coins -= 100
        collected = collected + "${chain}_3"
        lastSpawnedCell = idx to System.currentTimeMillis()
        emitSound("spawn")
        setBanner("🎁 Unboxed L3 ${chain.emoji(3)} ${chain.itemName(3)}!", "success")
        save()
    }

    fun giftRemainingSeconds(): Long {
        return ((giftNext - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
    }

    fun claimGift(): String {
        val now = System.currentTimeMillis()
        if (now < giftNext) {
            val mins = ((giftNext - now) / 60000L + 1L).toInt()
            setBanner("🎁 Free gift ready in ~${mins}m!", "info")
            return ""
        }
        giftNext = now + 15 * 60000L
        return if (Random.nextBoolean()) {
            energy = minOf(50, energy + 15)
            emitSound("serve")
            setBanner("🎁 Free Gift Claimed: +15 ⚡ Energy!", "success")
            save()
            "energy"
        } else {
            coins += 35
            emitSound("serve")
            setBanner("🎁 Free Gift Claimed: +35 🪙 Coins!", "success")
            save()
            "coins"
        }
    }

    fun buyScissors() {
        if (scissorMode) {
            scissorMode = false
            setBanner("✂️ Scissors cancelled.", "info")
            return
        }
        if (coins < 30) {
            setBanner("Need 30 🪙 for Scissors.", "warning")
            return
        }
        coins -= 30
        scissorMode = true
        setBanner("✂️ Scissors active! Tap any item on the board to clear it.", "info")
        save()
    }

    fun buyDouble() {
        if (coins < 50) {
            setBanner("Need 50 🪙 for 2x Coins booster.", "warning")
            return
        }
        coins -= 50
        doubleMergesLeft += 5
        emitSound("serve")
        setBanner("✨ 2x Coins active for your next $doubleMergesLeft merges!", "success")
        save()
    }

    fun claimMission(id: String, reward: Int) {
        if (id in missionsClaimed) return
        missionsClaimed = missionsClaimed + id
        coins += reward
        emitSound("serve")
        setBanner("🎉 Daily Mission claimed! +$reward 🪙!", "success")
        save()
    }

    fun claimAchievement(id: String, reward: Int) {
        if (id in achievementsClaimed) return
        achievementsClaimed = achievementsClaimed + id
        coins += reward
        emitSound("serve")
        setBanner("🏆 Achievement unlocked! +$reward 🪙!", "success")
        save()
    }

    fun claimPass(tier: Int, coinsReward: Int, energyReward: Int) {
        if (tier in passClaimed) return
        passClaimed = passClaimed + tier
        coins += coinsReward
        energy = minOf(50, energy + energyReward)
        emitSound("serve")
        setBanner("🎟️ Bazaar Pass Tier ${tier + 1} reward claimed!", "success")
        save()
    }

    fun dismissWelcomeBack() {
        welcomeBackCoins = 0
    }

    fun checkDaily() {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = fmt.format(Date())
        if (missionDate != today) {
            missionDate = today
            mergesToday = 0
            servesToday = 0
            maxLevelToday = 1
            missionsClaimed = emptySet()
        }
        if (lastBonusDate != today) {
            val yesterday = fmt.format(Date(System.currentTimeMillis() - 86400000L))
            streak = if (lastBonusDate == yesterday) streak + 1 else 1
            lastBonusDate = today
            val bonus = minOf(30 + (streak - 1) * 10, 100)
            coins += bonus
            setBanner("🎁 Daily Streak Bonus: +$bonus 🪙! (🔥 $streak day streak)", "success")
        }
        if (lastSeen > 0L) {
            val elapsedMs = System.currentTimeMillis() - lastSeen
            val minutesAway = elapsedMs / 60000.0
            // Refill energy accrued while away (1 per 30s)
            val refilledEnergy = (elapsedMs / 30000L).toInt()
            if (refilledEnergy > 0 && energy < 50) {
                energy = minOf(50, energy + refilledEnergy)
            }
            val idleCoins = minOf(60, (minutesAway * 0.5).toInt())
            if (idleCoins >= 5) {
                coins += idleCoins
                welcomeBackCoins = idleCoins
                setBanner("👋 Welcome back! Your bazaar stalls earned +$idleCoins 🪙 while you were away!", "success")
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
        val orderStr = orders.joinToString(";") {
            "${it.chain.name}:${it.level}:${it.coins}:${it.avatar}:${it.expiresIn}"
        }
        prefs.edit()
            .putInt("score", score)
            .putInt("coins", coins)
            .putInt("stars", stars)
            .putInt("energy", energy)
            .putInt("streak", streak)
            .putInt("mergesToday", mergesToday)
            .putInt("servesToday", servesToday)
            .putInt("maxLevelToday", maxLevelToday)
            .putInt("totalMerges", totalMerges)
            .putInt("totalServes", totalServes)
            .putInt("doubleMergesLeft", doubleMergesLeft)
            .putLong("giftNext", giftNext)
            .putLong("lastSeen", System.currentTimeMillis())
            .putBoolean("muted", muted)
            .putString("board", boardStr)
            .putString("orders", orderStr)
            .putString("collected", collected.joinToString(","))
            .putString("passClaimed", passClaimed.joinToString(","))
            .putString("missionsClaimed", missionsClaimed.joinToString(","))
            .putString("achievementsClaimed", achievementsClaimed.joinToString(","))
            .putString("missionDate", missionDate)
            .putString("lastBonusDate", lastBonusDate)
            .apply()
    }

    private fun load(): Boolean {
        if (!prefs.contains("board")) return false
        return try {
            score = prefs.getInt("score", 0)
            coins = prefs.getInt("coins", 50)
            stars = prefs.getInt("stars", 0)
            energy = prefs.getInt("energy", 50)
            streak = prefs.getInt("streak", 1).coerceAtLeast(1)
            mergesToday = prefs.getInt("mergesToday", 0)
            servesToday = prefs.getInt("servesToday", 0)
            maxLevelToday = prefs.getInt("maxLevelToday", 1)
            totalMerges = prefs.getInt("totalMerges", 0)
            totalServes = prefs.getInt("totalServes", 0)
            doubleMergesLeft = prefs.getInt("doubleMergesLeft", 0)
            giftNext = prefs.getLong("giftNext", 0L)
            lastSeen = prefs.getLong("lastSeen", 0L)
            muted = prefs.getBoolean("muted", false)
            missionDate = prefs.getString("missionDate", "") ?: ""
            lastBonusDate = prefs.getString("lastBonusDate", "") ?: ""
            collected = (prefs.getString("collected", "") ?: "")
                .split(",").filter { it.isNotEmpty() }.toSet()
                .ifEmpty { setOf("FOOD_1", "CRAFT_1", "TOY_1") }
            passClaimed = (prefs.getString("passClaimed", "") ?: "")
                .split(",").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }.toSet()
            missionsClaimed = (prefs.getString("missionsClaimed", "") ?: "")
                .split(",").filter { it.isNotEmpty() }.toSet()
            achievementsClaimed = (prefs.getString("achievementsClaimed", "") ?: "")
                .split(",").filter { it.isNotEmpty() }.toSet()

            val b = MutableList<Cell>(30) { Cell.Empty }
            (prefs.getString("board", "") ?: "").split(",").forEachIndexed { i, s ->
                if (i >= 30) return@forEachIndexed
                val parts = s.split(":")
                b[i] = when (parts[0]) {
                    "G" -> Cell.Generator(Chain.valueOf(parts[1]))
                    "I" -> Cell.Item(Chain.valueOf(parts[1]), parts[2].toInt().coerceIn(1, 7))
                    else -> Cell.Empty
                }
            }
            // Ensure all 3 generators exist on board
            Chain.values().forEachIndexed { idx, chain ->
                if (b.none { it is Cell.Generator && it.chain == chain }) {
                    val slot = b.indexOfFirst { it is Cell.Empty }.takeIf { it != -1 } ?: (idx * 6)
                    b[slot] = Cell.Generator(chain)
                }
            }
            board = b

            orders = (prefs.getString("orders", "") ?: "")
                .split(";")
                .filter { it.isNotEmpty() }
                .map {
                    val p = it.split(":")
                    Order(
                        Chain.valueOf(p[0]),
                        p[1].toInt().coerceIn(1, 7),
                        p[2].toInt(),
                        p[3],
                        p[4].toInt().coerceAtLeast(10)
                    )
                }
                .ifEmpty { listOf(createOrder(1, 2), createOrder(2, 3), createOrder(2, 4)) }
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

package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import androidx.compose.ui.graphics.Color
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile

private const val PEER_COLOR_COUNT = 8

private val lightPeerColors = listOf(
    Color(0xFFE7F0FF), Color(0xFFE7F5EC), Color(0xFFFFF0DF), Color(0xFFF1E9FA),
    Color(0xFFFFE9EC), Color(0xFFE4F3F2), Color(0xFFFFF6D9), Color(0xFFECEEF2),
)

private val darkPeerColors = listOf(
    Color(0xFF26384F), Color(0xFF293F34), Color(0xFF493724), Color(0xFF3D304C),
    Color(0xFF492F37), Color(0xFF263F3E), Color(0xFF474124), Color(0xFF343840),
)

/** Assign colors only when multiple distinct browser joiners are represented in the list. */
internal fun lanSharePeerColorIndices(
    files: List<LanShareFile>,
    ownFileIds: Set<String>,
): Map<String, Int> {
    val senders = files.asSequence()
        .filter { it.id !in ownFileIds && it.sender.isLanShareBrowserSender() }
        .map(LanShareFile::sender)
        .distinct()
        .sorted()
        .toList()
    if (senders.size < 2) return emptyMap()
    return senders.mapIndexed { index, sender -> sender to index % PEER_COLOR_COUNT }.toMap()
}

internal fun lanSharePeerBubbleColor(index: Int, dark: Boolean): Color =
    (if (dark) darkPeerColors else lightPeerColors)[index.mod(PEER_COLOR_COUNT)]

private fun String.isLanShareBrowserSender(): Boolean =
    this == "browser" || startsWith("browser:")

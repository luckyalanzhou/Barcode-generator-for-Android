package com.luckyalanzhou.barcodegenerator.data.network.server

import java.security.MessageDigest

/** One random secret per host session. Query tokens support browsers and WebSocket handshakes. */
internal class LanShareAccessControl(private val accessToken: String) {
    fun allows(queryToken: String?, headerToken: String?): Boolean =
        listOfNotNull(queryToken, headerToken).any { candidate ->
            MessageDigest.isEqual(
                accessToken.toByteArray(Charsets.US_ASCII),
                candidate.toByteArray(Charsets.US_ASCII),
            )
        }
}

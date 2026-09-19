package com.luckyalanzhou.barcodegenerator.data.network.web

import com.luckyalanzhou.barcodegenerator.data.network.protocol.*

import com.luckyalanzhou.barcodegenerator.*

/** 浏览器端传输页总模板，只负责按顺序组装各部分。 */
internal object LanShareWebTemplates {
    fun page() = buildString {
        append(LanShareWebHead.render())
        append(LanShareWebStyles.render())
        append(LanShareWebBody.render())
        append(LanShareWebScript.render())
        append("</body></html>")
    }
}

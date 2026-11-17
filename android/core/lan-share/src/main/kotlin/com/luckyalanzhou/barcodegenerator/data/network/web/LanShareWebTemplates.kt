package com.luckyalanzhou.barcodegenerator.data.network.web

/** 浏览器端传输页总模板，只负责按顺序组装各部分。 */
internal object LanShareWebTemplates {
    fun page(
        locale: LanShareWebLocale = LanShareWebLocale.SIMPLIFIED_CHINESE,
        liquidGlassEnabled: Boolean = true,
    ) = buildString {
        append(LanShareWebHead.render(locale))
        append(LanShareWebStyles.render(liquidGlassEnabled))
        append(LanShareWebBody.render(locale))
        val strings = LanShareWebText.entries.associate { it.name to it.resolve(locale) }
        append("""<script type="application/json" id="lan-share-strings">""")
        append(lanShareScriptJson(strings))
        append("</script>")
        append(LanShareWebScript.render())
        append("</body></html>")
    }
}

internal fun escapeLanShareHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

internal fun lanShareScriptJson(strings: Map<String, String>): String =
    strings.entries.joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
        quoteScriptJsonString(key) + ":" + quoteScriptJsonString(value)
    }

private fun quoteScriptJsonString(value: String): String = buildString {
    append('"')
    for (character in value) {
        when {
            character == '"' || character == '\\' -> append('\\').append(character)
            // HTML parses closing script tags regardless of JSON quoting.
            character in "<>&\u2028\u2029" || character < ' ' || character.isSurrogate() ->
                append("\\u").append(character.code.toString(16).padStart(4, '0'))
            else -> append(character)
        }
    }
    append('"')
}

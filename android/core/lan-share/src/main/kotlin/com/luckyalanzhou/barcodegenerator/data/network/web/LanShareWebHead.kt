package com.luckyalanzhou.barcodegenerator.data.network.web

/** 浏览器传输页的 HTML 头部模板。 */
internal object LanShareWebHead {
    fun render(locale: LanShareWebLocale) =
        """<!doctype html><html lang="${locale.tag}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="no-referrer"><title>${escapeLanShareHtml(LanShareWebText.TITLE.resolve(locale))}</title>"""
}

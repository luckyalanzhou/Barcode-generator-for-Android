package com.luckyalanzhou.barcodegenerator.data.network.web

/** 浏览器传输页的 HTML 头部模板。 */
internal object LanShareWebHead {
    fun render() = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="no-referrer"><title>文件传输</title>"""
}

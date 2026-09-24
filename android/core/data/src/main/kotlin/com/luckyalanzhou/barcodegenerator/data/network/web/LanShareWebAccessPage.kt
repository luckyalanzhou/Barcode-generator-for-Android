package com.luckyalanzhou.barcodegenerator.data.network.web

/** Safe entry page for people typing the host's bare address into a browser. */
internal object LanShareWebAccessPage {
    fun render(errorMessage: String? = null): String {
        val alert = errorMessage?.let { "<p role=\"alert\" style=\"color:#b42318\">$it</p>" }.orEmpty()
        return """<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="no-referrer"><title>加入局域网分享</title><style>body{font-family:system-ui,sans-serif;max-width:420px;margin:15vh auto;padding:24px;color:#20252b}input,button{box-sizing:border-box;width:100%;font:inherit;padding:12px;margin-top:12px;border-radius:10px}input{border:1px solid #aaa}button{border:0;background:#1769df;color:white}</style></head><body><h2>加入局域网分享</h2><p>请输入分享设备上显示的四位数字与英文字母访问码。也可以扫描二维码或粘贴完整分享链接。</p>$alert<form method="post" action="/join"><label for="code">四位访问码</label><input id="code" name="code" pattern="[A-Za-z0-9]{4}" minlength="4" maxlength="4" autocomplete="off" autocapitalize="characters" spellcheck="false" required><button type="submit">加入</button></form></body></html>"""
    }
}

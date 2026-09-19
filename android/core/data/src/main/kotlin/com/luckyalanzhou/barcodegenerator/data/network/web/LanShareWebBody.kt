package com.luckyalanzhou.barcodegenerator.data.network.web

/** 浏览器传输页的页面结构模板。 */
internal object LanShareWebBody {
    fun render() = """<body><main><div class="bar"><span>文件传输</span><span id="connection-status" class="connection-status">○ 正在连接设备...</span></div><ul id="files"></ul><div class="bottom"><form id="upload-form"><button type="button" class="file-picker" id="attachment-button" aria-label="添加附件">📎</button><input id="camera-capture" type="file" accept="image/*" capture="environment" hidden><input id="gallery" type="file" accept="image/*" hidden><input id="file-picker" type="file" hidden><input id="message" class="message-input" placeholder="输入文字" autocomplete="off"><button type="submit" class="send-button" aria-label="发送或上传">↑</button></form></div><div id="attachment-sheet" class="attachment-sheet"><button data-picker="camera-capture">拍摄图片</button><button data-picker="gallery">照片图库</button><button data-picker="file-picker">选择文件</button><button class="sheet-close">取消</button></div></main>"""
}

package com.luckyalanzhou.barcodegenerator

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

/** 单次会话的统一传输记录；App 与浏览器均以该记录生成消息气泡。 */
data class LanShareFile(val id: String, val name: String, val size: Long, val modifiedAt: Long, val sender: String = "peer")
data class LanShareSession(val baseUrl: String)

/** 仅在同一局域网使用的临时文件房间；地址由随机端口标识当前会话。 */
class LanShareManager(private val context: Context) {
    companion object {
        const val MAX_FILE_BYTES = 5L * 1024L * 1024L * 1024L
        const val MAX_ROOM_BYTES = 100L * 1024L * 1024L * 1024L

        fun isPrivateLanHost(host: String?): Boolean = runCatching {
            (java.net.InetAddress.getByName(host) as? Inet4Address)?.let { !it.isLoopbackAddress && it.isSiteLocalAddress } == true
        }.getOrDefault(false)
    }
    private val folder = File(context.filesDir, "lan-share").apply { mkdirs() }
    private var server: LanShareServer? = null
    private var lastPort: Int? = null

    /** 分享服务只暴露在 Wi-Fi/以太网的私有 IPv4 局域网中，避免蜂窝网络误启动。 */
    fun isOnLocalNetwork(): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return false
        return connectivity.getLinkProperties(network)?.linkAddresses.orEmpty().any { address ->
            (address.address as? Inet4Address)?.let { !it.isLoopbackAddress && it.isSiteLocalAddress } == true
        }
    }

    fun start(): LanShareSession = start(clearSharedFiles = true)

    private fun start(clearSharedFiles: Boolean): LanShareSession {
        check(isOnLocalNetwork()) { "Error 当前不处于局域网" }
        stop()
        if (clearSharedFiles) clearFiles()
        val ports = (18080..28080).filter { it != lastPort }.shuffled() + listOfNotNull(lastPort)
        val running = ports.firstNotNullOfOrNull { port ->
            runCatching { LanShareServer(port, folder, "").also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) } }.getOrNull()
        } ?: error("无法启动局域网分享服务")
        server = running
        lastPort = running.listeningPort
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val address = connectivity.activeNetwork?.let(connectivity::getLinkProperties)?.linkAddresses.orEmpty()
            .mapNotNull { it.address as? Inet4Address }
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress ?: error("未连接到局域网")
        return LanShareSession("http://$address:${running.listeningPort}")
    }

    fun restart(): LanShareSession {
        return start(clearSharedFiles = false)
    }

    fun browserConnected() = server?.browserConnected() == true

    fun stop(clearSharedFiles: Boolean = false) { server?.stop(); server = null; if (clearSharedFiles) clearFiles() }
    fun localFiles() = listFiles(folder, "app")
    fun localFile(id: String): File? = sharedFile(folder, id)
    private fun clearFiles() { folder.listFiles().orEmpty().forEach { it.delete() } }

    fun list(session: LanShareSession) = request(session, "/api/files") { connection ->
        JSONArray(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }).let { json ->
            (0 until json.length()).map { index -> json.getJSONObject(index).let { LanShareFile(it.getString("id"), it.getString("name"), it.getLong("size"), it.getLong("modifiedAt"), it.optString("sender", "peer")) } }
        }
    }

    fun upload(session: LanShareSession, uri: Uri): String {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor -> cursor.moveToFirst(); cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) } ?: "附件"
        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (size < 0) error("无法确定文件大小，请先将文件保存到本机")
        require(size <= MAX_FILE_BYTES) { "单个文件不能超过 5 GB" }
        val boundary = "----BarcodeShare${System.currentTimeMillis()}"
        val header = "--$boundary\r\nContent-Disposition: form-data; name=\"attachment\"; filename=\"${multipartFileName(name)}\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray()
        val footer = "\r\n--$boundary--\r\n".toByteArray()
        val connection = (URL(session.baseUrl + "/api/upload").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 120_000; requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            // NanoHTTPD 对 chunked 大请求会尝试构造整段字符串；固定长度可让其直接落到临时文件。
            setFixedLengthStreamingMode(header.size.toLong() + size + footer.size)
        }
        try {
            connection.outputStream.buffered().use { output ->
                output.write(header)
                context.contentResolver.openInputStream(uri)?.use { it.copyTo(output, 16 * 1024) } ?: error("无法读取附件")
                output.write(footer)
            }
            if (connection.responseCode !in 200..299) error("上传失败：${connection.responseCode}")
            return name
        } finally { connection.disconnect() }
    }

    fun uploadText(session: LanShareSession, text: String): String {
        val name = text.trim().take(100).ifBlank { "消息" }
        val body = text.toByteArray(Charsets.UTF_8)
        val boundary = "----BarcodeShare${System.currentTimeMillis()}"
        val header = "--$boundary\r\nContent-Disposition: form-data; name=\"attachment\"; filename=\"${multipartFileName(name)}\"\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n".toByteArray()
        val footer = "\r\n--$boundary--\r\n".toByteArray()
        val connection = (URL(session.baseUrl + "/api/upload").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 30_000; requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setFixedLengthStreamingMode(header.size.toLong() + body.size + footer.size)
        }
        try {
            connection.outputStream.buffered().use { output -> output.write(header); output.write(body); output.write(footer) }
            if (connection.responseCode !in 200..299) error("发送失败：${connection.responseCode}")
            return name
        } finally { connection.disconnect() }
    }

    fun download(session: LanShareSession, id: String, destination: Uri) = request(session, "/api/download/${Uri.encode(id)}") { connection ->
        context.contentResolver.openOutputStream(destination)?.use { output -> connection.inputStream.use { it.copyTo(output) } } ?: error("无法写入文件")
    }

    fun downloadPreview(session: LanShareSession, id: String, destination: File) = request(session, "/api/download/${Uri.encode(id)}") { connection ->
        destination.parentFile?.mkdirs()
        destination.outputStream().use { output -> connection.inputStream.use { it.copyTo(output) } }
    }

    private fun <T> request(session: LanShareSession, path: String, output: Boolean = false, block: (HttpURLConnection) -> T): T {
        require(isPrivateLanHost(Uri.parse(session.baseUrl).host)) { "仅允许连接局域网设备" }
        val connection = (URL(session.baseUrl + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 30_000; requestMethod = if (output) "POST" else "GET"; doOutput = output
            if (output) setRequestProperty("Content-Type", "application/octet-stream")
        }
        return try { val value = block(connection); if (connection.responseCode !in 200..299) error("连接失败：${connection.responseCode}"); value } finally { connection.disconnect() }
    }

    private class LanShareServer(port: Int, private val folder: File, private val token: String) : NanoWSD(port) {
        @Volatile private var lastBrowserRequestAt = 0L
        @Volatile private var fileVersion = 0L
        private val webSockets = CopyOnWriteArraySet<NanoWSD.WebSocket>()
        private val uploadLock = Any()

        private fun uploadLimitError(size: Long): String? = when {
            size > MAX_FILE_BYTES -> "单个文件不能超过 5 GB"
            folder.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() } > MAX_ROOM_BYTES - size -> "房间文件总大小不能超过 100 GB"
            else -> null
        }

        fun browserConnected() = System.currentTimeMillis() - lastBrowserRequestAt < 4_500L

        override fun openWebSocket(handshake: IHTTPSession): NanoWSD.WebSocket = object : NanoWSD.WebSocket(handshake) {
            override fun onOpen() {
                webSockets.add(this)
                lastBrowserRequestAt = System.currentTimeMillis()
                // 新网页刚连上时补发当前版本，填补页面初始读取与 WebSocket 建连之间的文件事件。
                runCatching { send(JSONObject().put("type", "files").put("version", fileVersion).toString()) }
            }
            override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) { webSockets.remove(this) }
            override fun onMessage(message: NanoWSD.WebSocketFrame) {
                // 客户端在 onopen 后请求快照；此时浏览器已安装 onmessage，不会漏掉首批文件。
                if (message.textPayload == "sync") runCatching { send(fileSnapshotEvent()) }
            }
            override fun onPong(pong: NanoWSD.WebSocketFrame) = Unit
            override fun onException(exception: IOException) { webSockets.remove(this) }
        }

        private fun notifyFilesChanged(file: File? = null) {
            fileVersion++
            val event = JSONObject().put("type", "files").put("version", fileVersion).toString()
            val eventWithFile = file?.let { uploaded ->
                val record = toLanShareFile(uploaded, "peer")
                JSONObject(event).put("file", JSONObject()
                    .put("id", record.id)
                    .put("name", record.name)
                    .put("size", record.size)
                    .put("modifiedAt", record.modifiedAt)
                    .put("sender", record.sender)
                ).toString()
            } ?: event
            webSockets.toList().forEach { socket -> runCatching { if (socket.isOpen) socket.send(eventWithFile) }.onFailure { webSockets.remove(socket) } }
        }

        private fun fileSnapshotEvent(): String = JSONObject().put("type", "snapshot").put("files", JSONArray(
            listFiles(folder, "peer").map { record -> JSONObject()
                .put("id", record.id)
                .put("name", record.name)
                .put("size", record.size)
                .put("modifiedAt", record.modifiedAt)
                .put("sender", record.sender)
            }
        )).toString()

        override fun serveHttp(session: IHTTPSession): Response {
            if (session.headers["user-agent"].orEmpty().contains("Mozilla", ignoreCase = true)) lastBrowserRequestAt = System.currentTimeMillis()
            val requestPath = session.uri.substringBefore('?')
            return try {
                when {
                    session.method == Method.GET && requestPath == "/api/files" -> newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", JSONArray(listFiles(folder, "peer").map { JSONObject().put("id", it.id).put("name", it.name).put("size", it.size).put("modifiedAt", it.modifiedAt).put("sender", it.sender) }).toString()).apply { addHeader("Cache-Control", "no-store, no-cache, must-revalidate") }
                    session.method == Method.GET && requestPath == "/api/presence" -> newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok").apply { addHeader("Cache-Control", "no-store, no-cache, must-revalidate") }
                    session.method == Method.GET && requestPath == "/api/events" -> {
                        val since = session.parms["since"]?.toLongOrNull() ?: 0L
                        val deadline = System.currentTimeMillis() + 25_000L
                        while (fileVersion <= since && System.currentTimeMillis() < deadline) Thread.sleep(120L)
                        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", JSONObject().put("version", fileVersion).toString()).apply { addHeader("Cache-Control", "no-store, no-cache, must-revalidate") }
                    }
                    session.method == Method.GET && requestPath == "/" -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", browserPage()).apply { addHeader("Cache-Control", "no-store, no-cache, must-revalidate") }
                    session.method == Method.POST && requestPath == "/api/upload" -> {
                        val bodies = HashMap<String, String>(); session.parseBody(bodies)
                        val source = bodies["attachment"]?.let(::File) ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "未读取到附件")
                        val name = safeFileName(session.parms["attachment"].orEmpty().substringAfterLast('/'))
                        val target = File(folder, "app_${System.currentTimeMillis()}_$name")
                        val error = synchronized(uploadLock) { uploadLimitError(source.length()) ?: run { source.copyTo(target, overwrite = true); notifyFilesChanged(target); null } }
                        if (error == null) newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok") else newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, error)
                    }
                    session.method == Method.PUT && requestPath == "/upload" -> {
                        val submittedName = Uri.decode(session.parms["name"].orEmpty()).ifBlank { "附件" }
                        val clientId = safeBrowserClientId(session.parms["client"].orEmpty())
                        val name = safeFileName(submittedName)
                        val targetPrefix = System.currentTimeMillis()
                        val files = HashMap<String, String>(); session.parseBody(files)
                        val temporaryFile = files["content"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "未读取到上传内容")
                        val target = File(folder, "web_${targetPrefix}_${clientId}_$name")
                        val source = File(temporaryFile)
                        val error = synchronized(uploadLock) { uploadLimitError(source.length()) ?: run { source.copyTo(target, overwrite = true); notifyFilesChanged(target); null } }
                        if (error == null) newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok") else newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, error)
                    }
                    session.method == Method.GET && requestPath.startsWith("/api/download/") -> {
                        val file = sharedFile(folder, Uri.decode(requestPath.substringAfterLast('/')))
                        if (file == null) newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "未找到文件")
                        else newFixedLengthResponse(Response.Status.OK, mimeTypeForName(file.name), FileInputStream(file), file.length()).apply { addHeader("Content-Disposition", "${if (mimeTypeForName(file.name).startsWith("image/")) "inline" else "attachment"}; filename=\"${toLanShareFile(file, "peer").name}\"") }
                    }
                    else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
                }
            } catch (_: Exception) { newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "传输失败") }
        }

        private fun browserPage(): String {
            return """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>文件传输</title>
<style>
:root{color-scheme:light dark;--bg:#f4f6fb;--panel:#fff;--text:#172033;--line:#d9e0ea;--sub:#667085}@media(prefers-color-scheme:dark){:root{--bg:#000;--panel:#1d1d1f;--text:#f5f5f7;--line:#333;--sub:#8e8e93}}*{box-sizing:border-box}body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;margin:0;color:var(--text);background:var(--bg)}main{max-width:560px;margin:auto;min-height:100vh;padding-bottom:86px}.bar{display:flex;align-items:center;justify-content:space-between;padding:18px 20px 12px;font-size:20px;font-weight:700}.connection-status{font-size:13px;font-weight:500;color:var(--sub);white-space:nowrap}.connection-status.connected{color:#22c55e}ul{display:flex;flex-direction:column;align-items:flex-start;gap:10px;padding:0 14px;margin:0}li{display:flex;align-items:center;gap:12px;width:fit-content;max-width:78%;padding:10px 14px;border:1px solid rgba(255,255,255,.72);border-radius:18px;list-style:none;background:rgba(255,255,255,.62);box-shadow:0 10px 28px rgba(31,41,55,.08),inset 0 1px rgba(255,255,255,.8);backdrop-filter:blur(20px);-webkit-backdrop-filter:blur(20px)}li.mine{align-self:flex-end;background:rgba(10,132,255,.58);border-color:rgba(147,197,253,.58)}li.peer{align-self:flex-start}@media(prefers-color-scheme:dark){li,.file-picker,.message-input{background:rgba(44,44,46,.72);border-color:rgba(255,255,255,.16)}}li:not(.image-item)::before{content:'📎';font-size:22px;line-height:1;flex:0 0 auto}li>a:first-of-type{min-width:0;flex:1;overflow-wrap:anywhere;color:var(--text);text-decoration:none}small{display:block;color:var(--sub);margin-top:4px}.image-item{flex-direction:column;align-items:flex-start;padding:6px;gap:8px}.media-preview{width:auto;max-width:100%;height:auto;max-height:260px;object-fit:contain;align-self:center;border-radius:12px;background:rgba(0,0,0,.08)}.download{flex:0 0 auto;padding:8px 13px;border:1px solid rgba(255,255,255,.72);border-radius:999px;background:rgba(255,255,255,.55);color:#0a84ff!important;text-decoration:none;backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px)}.bottom{position:fixed;bottom:0;left:0;right:0;background:var(--panel);padding:14px}.bottom form{display:flex;align-items:center;gap:8px;max-width:560px;margin:auto}.file-picker,.send-button{display:flex;align-items:center;justify-content:center;width:44px;height:44px;border:1px solid var(--line);border-radius:22px;cursor:pointer;flex:0 0 44px}.file-picker{color:var(--sub);background:rgba(255,255,255,.45);backdrop-filter:blur(18px)}.file-picker svg,.send-button svg{width:21px;height:21px}.message-input{min-width:0;flex:1;height:44px;padding:0 14px;border:1px solid var(--line);border-radius:22px;background:rgba(255,255,255,.45);color:var(--text);font:inherit;outline:none;backdrop-filter:blur(18px)}.message-input:focus{border-color:#0a84ff}.send-button{padding:0;background:rgba(10,132,255,.78);color:white;border-color:rgba(255,255,255,.65);box-shadow:0 8px 24px rgba(10,132,255,.2);backdrop-filter:blur(18px)}.attachment-sheet{display:none;position:fixed;width:154px;max-width:calc(100vw - 32px);padding:6px;border:1px solid rgba(255,255,255,.72);border-radius:20px;background:rgba(255,255,255,.84);box-shadow:0 16px 40px rgba(31,41,55,.2);backdrop-filter:blur(24px);-webkit-backdrop-filter:blur(24px);z-index:10}.attachment-sheet.open{display:flex;flex-direction:column}.attachment-sheet button{width:100%;min-height:38px;border:0;padding:9px 6px;appearance:none;-webkit-appearance:none;background:transparent;color:#172033;font:inherit;font-size:15px}.attachment-sheet button+button{border-top:1px solid rgba(71,91,122,.2)}.sheet-close{color:#667085!important}
</style></head><body><main><div class="bar"><span>文件传输</span><span id="connection-status" class="connection-status">○ 正在连接设备...</span></div><ul id="files"></ul><div class="bottom"><form id="upload-form"><button type="button" class="file-picker" id="attachment-button" title="添加附件" aria-label="添加附件"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/></svg></button><input id="attachment" type="file" accept="*/*" hidden><input id="camera-capture" type="file" accept="image/*" capture="environment" hidden><input id="gallery" type="file" accept="image/*" hidden><input id="file-picker" type="file" accept="*/*" hidden><input id="message" class="message-input" placeholder="输入文字" autocomplete="off"><button type="submit" class="send-button" title="发送或上传" aria-label="发送或上传"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 16V4"/><path d="m7 9 5-5 5 5"/><path d="M5 20h14"/></svg></button></form></div><div id="attachment-sheet" class="attachment-sheet"><button type="button" data-picker="camera-capture">拍摄图片</button><button type="button" data-picker="gallery">照片图库</button><button type="button" data-picker="file-picker">选择文件</button><button type="button" class="sheet-close">取消</button></div></main>
<script>
const files=document.getElementById('files'),form=document.getElementById('upload-form'),attachment=document.getElementById('attachment'),message=document.getElementById('message'),status=document.getElementById('connection-status'),sheet=document.getElementById('attachment-sheet'),attachmentButton=document.getElementById('attachment-button');function cookie(name){const prefix=name+'=';return document.cookie.split(';').map(function(value){return value.trim();}).find(function(value){return value.indexOf(prefix)===0;})?.slice(prefix.length)||'';}let clientId=cookie('lanShareClientId');if(!/^c[a-zA-Z0-9_-]{8,63}$/.test(clientId)){clientId='c'+Date.now().toString(36)+Math.random().toString(36).slice(2,10);document.cookie='lanShareClientId='+clientId+';Path=/;Max-Age=31536000;SameSite=Lax';}
function isImage(name){return /\\.(jpg|jpeg|png|gif|webp|heic|heif)/i.test(name||'');}function sizeText(size){return size>=1048576?(size/1048576).toFixed(1)+' MB':Math.floor(size/1024)+' KB';}function setStatus(connected){status.textContent=connected?'● 已连接到设备':'○ 正在连接设备...';status.classList.toggle('connected',connected);}
function findFile(id){return Array.from(files.children).find(function(item){return item.dataset.fileId===id;});}function createItem(file,url){const item=document.createElement('li');item.dataset.fileId=file.id||'';item.classList.add(file.sender==='browser:'+clientId?'mine':'peer');const imageFile=isImage(file.name);if(imageFile){const image=document.createElement('img');image.src=url;image.className='media-preview';image.alt=file.name;item.classList.add('image-item');item.appendChild(image);}const link=document.createElement('a');link.href=url;link.textContent=file.name||'未命名';link.setAttribute('download','');const size=document.createElement('small');size.textContent=sizeText(file.size||0);item.appendChild(link);item.appendChild(size);if(!imageFile){const download=document.createElement('a');download.href=url;download.textContent='下载';download.className='download';download.setAttribute('download','');item.appendChild(download);}item.addEventListener('click',function(event){if(event.target.closest('a'))return;link.click();});return item;}function appendFile(file){if(file.id&&findFile(file.id))return;files.appendChild(createItem(file,'/api/download/'+encodeURIComponent(file.id)));}function reconcileFiles(list){list.forEach(appendFile);}
function refreshFiles(){fetch('/api/files?_='+Date.now(),{cache:'no-store'}).then(function(response){if(!response.ok)throw new Error('files');return response.json();}).then(reconcileFiles).catch(function(){setStatus(false);});}
function upload(file){const pending=createItem({name:file.name||'未命名',size:file.size,sender:'browser:'+clientId},URL.createObjectURL(file));files.appendChild(pending);fetch('/upload?name='+encodeURIComponent(file.name||message.value||'未命名')+'&client='+encodeURIComponent(clientId),{method:'PUT',headers:{'content-type':file.type||'application/octet-stream'},body:file}).then(function(response){if(!response.ok)throw new Error('upload');attachment.value='';message.value='';refreshFiles();}).catch(function(){pending.remove();setStatus(false);window.alert('发送失败，请刷新页面后重试');});}
form.addEventListener('submit',function(event){event.preventDefault();let file=attachment.files[0];if(!file){const text=message.value.trim();if(!text){message.focus();return;}file=new File([text],'消息.txt',{type:'text/plain'});}upload(file);});function selectFile(input){const file=input.files[0];if(!file)return;sheet.classList.remove('open');upload(file);input.value='';}
const apple=/iPad|iPhone|iPod/.test(navigator.userAgent)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);attachmentButton.addEventListener('click',function(){if(apple){attachment.click();return;}sheet.classList.add('open');const rect=attachmentButton.getBoundingClientRect();sheet.style.left=(rect.left+8)+'px';sheet.style.top=(rect.top-sheet.offsetHeight-16)+'px';});attachment.addEventListener('change',function(){selectFile(this);});['camera-capture','gallery','file-picker'].forEach(function(id){document.getElementById(id).addEventListener('change',function(){selectFile(this);});});sheet.addEventListener('click',function(event){const picker=event.target.getAttribute('data-picker');if(picker)document.getElementById(picker).click();else if(event.target.classList.contains('sheet-close'))sheet.classList.remove('open');});
function heartbeat(){fetch('/api/presence?_='+Date.now(),{cache:'no-store'}).then(function(response){setStatus(response.ok);}).catch(function(){setStatus(false);});}let socket;function connect(){try{socket=new WebSocket((location.protocol==='https:'?'wss://':'ws://')+location.host+'/ws');socket.onopen=function(){setStatus(true);socket.send('sync');};socket.onmessage=function(event){try{const packet=JSON.parse(event.data);if(packet.type==='snapshot')reconcileFiles(packet.files);else if(packet.type==='files'&&packet.file)appendFile(packet.file);else refreshFiles();}catch(_){}};socket.onclose=function(){setStatus(false);setTimeout(connect,1000);};}catch(_){setTimeout(connect,1000);}}heartbeat();refreshFiles();setInterval(heartbeat,2000);setInterval(refreshFiles,5000);connect();
</script></body></html>"""
        }

        private fun browserInitialFiles(): String = listFiles(folder, "peer").joinToString("") { file ->
            val fileName = html(file.name)
            val url = "/api/download/${Uri.encode(file.id)}"
            val image = if (mimeTypeForName(file.name).startsWith("image/")) "<img class='media-preview' src='$url' alt='$fileName'>" else ""
            val senderClass = if (file.sender == "browser") "mine" else "peer"
            val itemClass = " class='$senderClass${if (image.isEmpty()) "" else " image-item"}'"
            val download = if (image.isEmpty()) "<a class='download' href='$url' download>下载</a>" else ""
            "<li$itemClass data-file-id='${html(file.id)}'>$image<a href='$url' download>$fileName</a><small>${formatBrowserFileSize(file.size)}</small>$download</li>"
        }

        private fun formatBrowserFileSize(size: Long) = if (size >= 1_048_576L) "%.1f MB".format(java.util.Locale.US, size / 1_048_576.0) else "${size / 1024} KB"

        private fun html(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    }
}

private fun multipartFileName(value: String) = value.replace(Regex("[\r\n\"]"), "_")
private fun safeFileName(value: String) = value.replace(Regex("[\\\\/:*?\"<>|\r\n]"), "_").take(100).ifBlank { "附件" }
private fun safeBrowserClientId(value: String) = value.takeIf { it.matches(Regex("c[a-zA-Z0-9_-]{8,63}")) } ?: "clegacy"

/** 共享目录只允许直接子文件，拒绝编码后的 ../ 等路径穿越。 */
private fun sharedFile(folder: File, id: String): File? {
    val root = folder.canonicalFile
    val candidate = File(root, id).canonicalFile
    return candidate.takeIf { it.isFile && it.parentFile == root }
}

private fun mimeTypeForName(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "mp4" -> "video/mp4"
    else -> "application/octet-stream"
}

private fun listFiles(folder: File, sender: String) = folder.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.lastModified() }.map { file -> toLanShareFile(file, sender) }

private fun toLanShareFile(file: File, sender: String): LanShareFile {
    val browserMatch = Regex("^web_\\d+_(c[a-zA-Z0-9_-]{8,63})_(.*)$").matchEntire(file.name)
    val fromBrowser = file.name.startsWith("web_")
    val fromApp = file.name.startsWith("app_")
    val displayName = browserMatch?.groupValues?.get(2)
        ?: if (fromBrowser || fromApp) file.name.substringAfter('_').substringAfter('_', file.name) else file.name.substringAfter('_', file.name)
    val source = browserMatch?.groupValues?.get(1)?.let { "browser:$it" }
        ?: if (fromBrowser) "browser" else if (fromApp) "app" else sender
    return LanShareFile(file.name, displayName, file.length(), file.lastModified(), source)
}

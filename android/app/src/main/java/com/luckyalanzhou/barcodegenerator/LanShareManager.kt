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
import java.net.NetworkInterface
import java.net.URL
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

/** 单次会话的统一传输记录；App 与浏览器均以该记录生成消息气泡。 */
data class LanShareFile(val id: String, val name: String, val size: Long, val modifiedAt: Long, val sender: String = "peer")
data class LanShareSession(val baseUrl: String)

/** 仅在同一局域网使用的临时文件房间；地址由随机端口标识当前会话。 */
class LanShareManager(private val context: Context) {
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
        val address = NetworkInterface.getNetworkInterfaces().toList().asSequence()
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>().firstOrNull { !it.isLoopbackAddress }?.hostAddress
            ?: error("未连接到局域网")
        return LanShareSession("http://$address:${running.listeningPort}")
    }

    fun restart(): LanShareSession {
        return start(clearSharedFiles = false)
    }

    fun browserConnected() = server?.browserConnected() == true

    fun stop(clearSharedFiles: Boolean = false) { server?.stop(); server = null; if (clearSharedFiles) clearFiles() }
    fun localFiles() = listFiles(folder, "app")
    fun localFile(id: String): File? = File(folder, id).takeIf { it.isFile && it.parentFile == folder }
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

    private fun <T> request(session: LanShareSession, path: String, output: Boolean = false, block: (HttpURLConnection) -> T): T {
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
                        source.copyTo(target, overwrite = true)
                        notifyFilesChanged(target)
                        newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok")
                    }
                    session.method == Method.PUT && requestPath == "/upload" -> {
                        val submittedName = Uri.decode(session.parms["name"].orEmpty()).ifBlank { "附件" }
                        val name = safeFileName(submittedName)
                        val targetPrefix = System.currentTimeMillis()
                        val files = HashMap<String, String>(); session.parseBody(files)
                        val temporaryFile = files["content"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "未读取到上传内容")
                        val target = File(folder, "web_${targetPrefix}_$name")
                        File(temporaryFile).copyTo(target, overwrite = true)
                        notifyFilesChanged(target)
                        newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok")
                    }
                    session.method == Method.GET && requestPath.startsWith("/api/download/") -> {
                        val file = File(folder, Uri.decode(requestPath.substringAfterLast('/')))
                        if (!file.isFile || file.parentFile != folder) newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "未找到文件")
                        else newFixedLengthResponse(Response.Status.OK, mimeTypeForName(file.name), FileInputStream(file), file.length()).apply { addHeader("Content-Disposition", "${if (mimeTypeForName(file.name).startsWith("image/")) "inline" else "attachment"}; filename=\"${file.name.substringAfter('_')}\"") }
                    }
                    else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
                }
            } catch (_: Exception) { newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "传输失败") }
        }

        private fun browserPage(): String = browserPageRaw()
            .replace("</style>", ".attachment-sheet{display:none;position:fixed;left:0;top:0;width:154px;max-width:calc(100vw - 32px);box-sizing:border-box;padding:6px;border:1px solid rgba(255,255,255,.72);border-radius:20px;background:rgba(255,255,255,.84);box-shadow:0 16px 40px rgba(31,41,55,.2);backdrop-filter:blur(24px);-webkit-backdrop-filter:blur(24px);z-index:10}.attachment-sheet.open{display:flex;flex-direction:column;align-items:stretch;gap:0}.attachment-sheet button{display:block;width:100%;min-height:38px;box-sizing:border-box;border:0;border-radius:0;padding:9px 6px;appearance:none;-webkit-appearance:none;background:transparent!important;color:#172033!important;font:inherit;font-size:15px;text-align:center;box-shadow:none;outline:none}.attachment-sheet button+button{border-top:1px solid rgba(71,91,122,.2)}.sheet-close{color:#667085!important}ul{align-items:flex-start;padding:0 14px;gap:10px}li{width:fit-content;max-width:78%;padding:10px 14px;border-radius:18px}li.mine{align-self:flex-end;background:rgba(10,132,255,.58);border-color:rgba(147,197,253,.58)}li.peer{align-self:flex-start}.image-item{flex-direction:column;align-items:flex-start;padding:6px;gap:8px}.image-item .media-preview{width:auto;max-width:100%;height:auto;max-height:260px;object-fit:contain;border-radius:12px;margin:0;background:rgba(0,0,0,.08)}li:not(.image-item)::before{content:'📎';font-size:22px;line-height:1;flex:0 0 auto}.image-item .media-preview{align-self:center}.bar{display:flex;align-items:center;justify-content:space-between;text-align:left}.connection-status{font-size:13px;font-weight:500;color:var(--sub);white-space:nowrap}.connection-status.connected{color:#22c55e}</style>")
            .replace("<div class='bar'>文件传输</div>", "<div class='bar'><span>文件传输</span><span id='connection-status' class='connection-status'>○ 正在连接设备...</span></div>")
            .replace("<div class='status'>已连接到局域网分享房间</div>", "")
            .replace(Regex("<label class='file-picker' title='选择附件'>.*?</label>"), "<button type='button' class='file-picker' id='attachment-button' title='添加附件' aria-label='添加附件'><svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true'><path d='m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48'/></svg></button><input id='attachment' type='file' name='attachment' accept='*/*' style='position:absolute;width:1px;height:1px;opacity:0'><input id='camera-capture' type='file' accept='image/*' capture='environment' style='display:none'><input id='gallery' type='file' accept='image/*' style='display:none'><input id='file-picker' type='file' accept='*/*' style='display:none'><div id='attachment-sheet' class='attachment-sheet'><button type='button' data-picker='camera-capture'>拍摄图片</button><button type='button' data-picker='gallery'>照片图库</button><button type='button' data-picker='file'>选择文件</button><button type='button' class='sheet-close'>取消</button></div>")
            .replace("const message=document.getElementById('message');", "const message=document.getElementById('message');const status=document.getElementById('connection-status');function setStatus(connected){status.textContent=connected?'● 已连接到设备':'○ 正在连接设备...';status.classList.toggle('connected',connected);}const sheet=document.getElementById('attachment-sheet');const attachmentButton=document.getElementById('attachment-button');function addPendingFile(file){const item=document.createElement('li');const link=document.createElement('a');link.href=URL.createObjectURL(file);link.textContent=file.name||'未命名';link.setAttribute('download',file.name||'附件');const size=document.createElement('small');size.textContent=file.size>=1048576?(file.size/1048576).toFixed(1)+' MB':Math.floor(file.size/1024)+' KB';if(/^image\\//.test(file.type)){const image=document.createElement('img');image.src=link.href;image.className='media-preview';image.alt=link.textContent;item.classList.add('image-item');item.appendChild(image);}item.appendChild(link);item.appendChild(size);document.getElementById('files').appendChild(item);return item;}function addBroadcastFile(file){const list=document.getElementById('files');if(list.querySelector('[data-file-id=\\\"'+CSS.escape(file.id)+'\\\"]'))return;const item=document.createElement('li');item.dataset.fileId=file.id;item.classList.add(file.sender==='browser'?'mine':'peer');const link=document.createElement('a');link.href='/api/download/'+encodeURIComponent(file.id);link.textContent=file.name;link.setAttribute('download','');const size=document.createElement('small');size.textContent=file.size>=1048576?(file.size/1048576).toFixed(1)+' MB':Math.floor(file.size/1024)+' KB';const imageFile=/\\.(jpg|jpeg|png|gif|webp|heic|heif)$/i.test(file.name);if(imageFile){const image=document.createElement('img');image.src=link.href;image.className='media-preview';image.alt=file.name;item.classList.add('image-item');item.appendChild(image);}item.appendChild(link);item.appendChild(size);if(!imageFile){const download=document.createElement('a');download.href=link.href;download.textContent='下载';download.className='download';download.setAttribute('download','');item.appendChild(download);}item.addEventListener('click',function(event){if(event.target.closest('a'))return;link.click();});list.appendChild(item);}")
            .replace("form.addEventListener('submit',function(event){if(attachment.files.length)return;const text=message.value.trim();if(!text){event.preventDefault();message.focus();return;}const blob=new Blob([text],{type:'text/plain;charset=utf-8'});const file=new File([blob],'消息.txt',{type:'text/plain'});const transfer=new DataTransfer();transfer.items.add(file);attachment.files=transfer.files;});", "form.addEventListener('submit',function(event){event.preventDefault();if(!attachment.files.length){const text=message.value.trim();if(!text){message.focus();return;}const blob=new Blob([text],{type:'text/plain;charset=utf-8'});const file=new File([blob],'消息.txt',{type:'text/plain'});const transfer=new DataTransfer();transfer.items.add(file);attachment.files=transfer.files;}const file=attachment.files[0];if(!file)return;const pending=addPendingFile(file);fetch('/upload?name='+encodeURIComponent(file.name||message.value||'未命名'),{method:'PUT',headers:{'content-type':file.type||'application/octet-stream'},body:file}).then(function(response){if(response.ok)return;return response.text().then(function(detail){throw new Error(detail||'上传失败');});}).then(function(){attachment.value='';message.value='';window.location.reload();}).catch(function(error){pending.remove();setStatus(false);window.alert('发送失败：'+(error.message||'请刷新页面后重试'));});});")
            .replace("attachment.addEventListener('change',function(){const file=this.files[0];if(file&&!message.value)message.value=file.name;});", "function selectAttachment(input){const file=input.files[0];if(!file)return;if(!message.value)message.value=file.name;const transfer=new DataTransfer();transfer.items.add(file);attachment.files=transfer.files;sheet.classList.remove('open');form.requestSubmit();}const isAppleDevice=/iPad|iPhone|iPod/.test(navigator.userAgent)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);attachmentButton.addEventListener('click',function(){if(isAppleDevice){sheet.classList.remove('open');attachment.click();return;}sheet.classList.add('open');const rect=attachmentButton.getBoundingClientRect();sheet.style.left=(rect.left+8)+'px';sheet.style.top=(rect.top-sheet.offsetHeight-16)+'px';sheet.style.bottom='auto';});attachment.addEventListener('change',function(){selectAttachment(this);});sheet.addEventListener('click',function(event){const picker=event.target.getAttribute('data-picker');if(picker){document.getElementById(picker==='file'?'file-picker':picker).click();}else if(event.target.classList.contains('sheet-close'))sheet.classList.remove('open');});['camera-capture','gallery','file-picker'].forEach(function(id){document.getElementById(id).addEventListener('change',function(){selectAttachment(this);});});")
            .replace(".then(function(files){const list=document.getElementById('files');", ".then(function(files){const list=document.getElementById('files');")
            .replace("refreshFiles();setInterval(refreshFiles,1500);", "function heartbeat(){fetch('/api/presence?_='+Date.now(),{cache:'no-store'}).then(function(response){setStatus(response.ok);}).catch(function(){setStatus(false);});}heartbeat();setInterval(heartbeat,2000);const imageObserver=new MutationObserver(function(){document.querySelectorAll('#files li').forEach(function(item){if(item.dataset.preview||item.querySelector('.media-preview')){item.dataset.preview='1';return;}const link=item.querySelector('a');if(link&&/\\.(jpg|jpeg|png|gif|webp|heic|heif)(\\?|$)/i.test(link.textContent)){const image=document.createElement('img');image.src=link.href;image.className='media-preview';image.alt=link.textContent;item.classList.add('image-item');item.insertBefore(image,item.firstChild);}item.dataset.preview='1';});});imageObserver.observe(document.getElementById('files'),{childList:true});let fileVersion=0;function watchFiles(){fetch('/api/events?since='+fileVersion,{cache:'no-store'}).then(function(response){if(!response.ok)throw new Error('events');return response.json();}).then(function(event){fileVersion=event.version||fileVersion;refreshFiles();watchFiles();}).catch(function(){setTimeout(watchFiles,1000);});}refreshFiles();watchFiles();")
            // WebSocket 到达时立即刷新；Safari 可能在后台回收长连接，独立轮询确保不会再卡在旧记录。
            .replace("refreshFiles();watchFiles();", "let transferSocket;function connectTransferSocket(){try{transferSocket=new WebSocket((location.protocol==='https:'?'wss://':'ws://')+location.host+'/ws');transferSocket.onopen=function(){try{transferSocket.send('sync');}catch(_){}};transferSocket.onmessage=function(event){try{const packet=JSON.parse(event.data);if(packet.type==='snapshot'&&packet.files){packet.files.forEach(addBroadcastFile);}else if(packet.type==='files'&&packet.file)addBroadcastFile(packet.file);else if(packet.type==='files')refreshFiles();}catch(_){}};transferSocket.onclose=function(){setTimeout(connectTransferSocket,1000);};}catch(_){setTimeout(connectTransferSocket,1000);}}refreshFiles();watchFiles();connectTransferSocket();setInterval(refreshFiles,1000);")
            .replace("const download=document.createElement('a');download.href=link.href;download.textContent='下载';download.className='download';download.setAttribute('download','');item.appendChild(link);item.appendChild(size);item.appendChild(download);list.appendChild(item);", "const imageFile=/\\.(jpg|jpeg|png|gif|webp|heic|heif)(\\?|$)/i.test(file.name);if(imageFile){const image=document.createElement('img');image.src=link.href;image.className='media-preview';image.alt=file.name;item.classList.add('image-item');item.insertBefore(image,item.firstChild);}item.dataset.preview='1';link.setAttribute('download','');item.appendChild(link);item.appendChild(size);if(!imageFile){const download=document.createElement('a');download.href=link.href;download.textContent='下载';download.className='download';download.setAttribute('download','');item.appendChild(download);}item.addEventListener('click',function(event){if(event.target.closest('a'))return;link.click();});list.appendChild(item);")
            .replace("const item=document.createElement('li');const link=document.createElement('a');", "const item=document.createElement('li');item.dataset.fileId=file.id;item.classList.add(file.sender==='browser'?'mine':'peer');const link=document.createElement('a');")
            .replace("拍照或录像", "拍摄图片")
            .replace("?token=$token", "")
            // 旧页面脚本仍有个别 URL 片段引用 token；保留空兼容变量，局域网传输不再校验 token。
            .replace("const token='$token';", "const token='';")
            .replace("/api/files?token='+encodeURIComponent(token)+'&_", "/api/files?_")
            .replace("/api/download/'+encodeURIComponent(file.id)+'?token='+encodeURIComponent(token)", "/api/download/'+encodeURIComponent(file.id)")
            .replace("<ul id='files'></ul>", "<ul id='files'>${browserInitialFiles()}</ul>")

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

        private fun browserPageRaw(): String {
            return """<!doctype html><html><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>文件传输</title><style>:root{color-scheme:light dark;--bg:#f4f6fb;--panel:#fff;--text:#172033;--line:#d9e0ea;--sub:#667085}@media(prefers-color-scheme:dark){:root{--bg:#000;--panel:#1d1d1f;--text:#f5f5f7;--line:#333;--sub:#8e8e93}}*{box-sizing:border-box}body{font-family:-apple-system,sans-serif;margin:0;color:var(--text);background:var(--bg)}main{max-width:560px;margin:auto;min-height:100vh;padding-bottom:86px}.bar{margin:0;padding:18px 20px 12px;background:transparent;text-align:center;font-size:20px;font-weight:700}.status{text-align:center;color:var(--sub);padding:8px 14px 14px}.bottom{position:fixed;bottom:0;left:0;right:0;background:var(--panel);padding:14px}.bottom form{max-width:560px;margin:auto;display:flex;align-items:center;gap:8px}.file-picker{display:flex;align-items:center;justify-content:center;width:44px;height:44px;border:1px solid var(--line);border-radius:22px;color:var(--sub);cursor:pointer;flex:0 0 44px;background:rgba(255,255,255,.45);backdrop-filter:blur(18px)}.file-picker input{display:none}.file-picker svg{width:21px;height:21px}.message-input{min-width:0;flex:1;height:44px;padding:0 14px;border:1px solid var(--line);border-radius:22px;background:rgba(255,255,255,.45);color:var(--text);font:inherit;outline:none;backdrop-filter:blur(18px)}.message-input:focus{border-color:#0a84ff}.bottom button{display:flex;align-items:center;justify-content:center;width:44px;height:44px;padding:0;background:rgba(10,132,255,.78);color:white;border:1px solid rgba(255,255,255,.65);border-radius:22px;cursor:pointer;flex:0 0 44px;box-shadow:0 8px 24px rgba(10,132,255,.2);backdrop-filter:blur(18px)}.bottom button svg{width:21px;height:21px}ul{display:flex;flex-direction:column;gap:10px;padding:0 18px;margin:0}li{display:flex;align-items:center;gap:12px;padding:12px 12px 12px 16px;border:1px solid rgba(255,255,255,.72);border-radius:24px;list-style:none;background:rgba(255,255,255,.62);box-shadow:0 10px 28px rgba(31,41,55,.08),inset 0 1px rgba(255,255,255,.8);backdrop-filter:blur(20px);-webkit-backdrop-filter:blur(20px)}@media(prefers-color-scheme:dark){li,.file-picker,.message-input{background:rgba(44,44,46,.72);border-color:rgba(255,255,255,.16)}}li>a:first-child{min-width:0;flex:1;overflow-wrap:anywhere;color:var(--text);text-decoration:none}small{display:block;color:var(--sub);margin-top:4px}.download{flex:0 0 auto;padding:8px 13px;border:1px solid rgba(255,255,255,.72);border-radius:999px;background:rgba(255,255,255,.55);color:#0a84ff!important;text-decoration:none;backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px)}</style><main><div class='bar'>文件传输</div><div class='status'>已连接到局域网分享房间</div><ul id='files'></ul><div class='bottom'><form id='upload-form' action='/upload-browser?token=$token' method='post' enctype='multipart/form-data'><label class='file-picker' title='选择附件'><input id='attachment' type='file' name='attachment'><svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true'><path d='m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48'/></svg></label><input id='message' class='message-input' name='name' placeholder='输入文字' autocomplete='off'><button type='submit' title='发送或上传' aria-label='发送或上传'><svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true'><path d='M12 16V4'/><path d='m7 9 5-5 5 5'/><path d='M5 20h14'/></svg></button></form></div></main><script>const token='$token';const form=document.getElementById('upload-form');const attachment=document.getElementById('attachment');const message=document.getElementById('message');attachment.addEventListener('change',function(){const file=this.files[0];if(file&&!message.value)message.value=file.name;});form.addEventListener('submit',function(event){if(attachment.files.length)return;const text=message.value.trim();if(!text){event.preventDefault();message.focus();return;}const blob=new Blob([text],{type:'text/plain;charset=utf-8'});const file=new File([blob],'消息.txt',{type:'text/plain'});const transfer=new DataTransfer();transfer.items.add(file);attachment.files=transfer.files;});function refreshFiles(){fetch('/api/files?token='+encodeURIComponent(token)+'&_='+Date.now(),{cache:'no-store'}).then(function(response){if(!response.ok)throw new Error('network');return response.json();}).then(function(files){const list=document.getElementById('files');list.textContent='';files.forEach(function(file){const item=document.createElement('li');const link=document.createElement('a');link.href='/api/download/'+encodeURIComponent(file.id)+'?token='+encodeURIComponent(token);link.textContent=file.name;const size=document.createElement('small');size.textContent=file.size>=1048576?(file.size/1048576).toFixed(1)+' MB':Math.floor(file.size/1024)+' KB';const download=document.createElement('a');download.href=link.href;download.textContent='下载';download.className='download';download.setAttribute('download','');item.appendChild(link);item.appendChild(size);item.appendChild(download);list.appendChild(item);});}).catch(function(){});}refreshFiles();setInterval(refreshFiles,1500);</script></html>"""
        }
        private fun html(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}

private fun multipartFileName(value: String) = value.replace(Regex("[\r\n\"]"), "_")
private fun safeFileName(value: String) = value.replace(Regex("[\\\\/:*?\"<>|\r\n]"), "_").take(100).ifBlank { "附件" }
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
    val fromBrowser = file.name.startsWith("web_")
    val fromApp = file.name.startsWith("app_")
    val displayName = if (fromBrowser || fromApp) file.name.substringAfter('_').substringAfter('_', file.name) else file.name.substringAfter('_', file.name)
    return LanShareFile(file.name, displayName, file.length(), file.lastModified(), if (fromBrowser) "browser" else if (fromApp) "app" else sender)
}

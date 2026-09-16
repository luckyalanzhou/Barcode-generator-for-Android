package com.luckyalanzhou.barcodegenerator

/** 浏览器传输页的交互脚本模板。 */
internal object LanShareWebScript {
    fun render() = """<script>
const fileList = document.getElementById('files');
const uploadForm = document.getElementById('upload-form');
const messageInput = document.getElementById('message');
const connectionStatus = document.getElementById('connection-status');
const attachmentSheet = document.getElementById('attachment-sheet');
const attachmentButton = document.getElementById('attachment-button');
const clientIdKey = 'lanShareClientId';

let clientId = localStorage.getItem(clientIdKey);
if (!clientId) {
    clientId = 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
    localStorage.setItem(clientIdKey, clientId);
}

function isImageName(name) {
    return /\.(jpg|jpeg|png|gif|webp|heic|heif)$/i.test(name || '');
}

function formatSize(bytes) {
    const value = Number(bytes) || 0;
    if (value >= 1024 * 1024 * 1024) return (value / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
    if (value >= 1024 * 1024) return (value / (1024 * 1024)).toFixed(1) + ' MB';
    if (value >= 1024) return Math.floor(value / 1024) + ' KB';
    return value + ' B';
}

function setConnectionState(connected) {
    connectionStatus.textContent = connected ? '● 已连接到设备' : '○ 正在连接设备...';
    connectionStatus.classList.toggle('connected', connected);
}

function fileUrl(file) {
    return '/api/download/' + encodeURIComponent(file.id) + '?v=' + encodeURIComponent(file.modifiedAt || '');
}

function createFileItem(file) {
    const item = document.createElement('li');
    const url = fileUrl(file);
    item.dataset.fileId = file.id;
    item.className = file.sender === 'browser:' + clientId ? 'mine' : 'peer';

    if (isImageName(file.name)) {
        const preview = document.createElement('img');
        preview.src = url;
        preview.className = 'media-preview';
        preview.alt = file.name || '图片';
        preview.loading = 'lazy';
        preview.decoding = 'async';
        item.classList.add('image-item');
        item.appendChild(preview);
    }

    const link = document.createElement('a');
    link.href = url;
    link.download = file.name || '附件';
    link.textContent = file.name || '未命名';
    item.appendChild(link);

    const size = document.createElement('small');
    size.textContent = formatSize(file.size);
    item.appendChild(size);

    if (!isImageName(file.name)) {
        const download = document.createElement('a');
        download.href = url;
        download.download = file.name || '附件';
        download.className = 'download';
        download.textContent = '下载';
        item.appendChild(download);
    }

    item.addEventListener('click', event => {
        if (!event.target.closest('a')) link.click();
    });
    return item;
}

/** 用服务端快照对齐列表，删除已不存在的记录并保持服务端顺序。 */
function reconcileFiles(list) {
    const normalized = (Array.isArray(list) ? list : []).filter(file => file && file.id);
    const current = new Map(Array.from(fileList.children).map(item => [item.dataset.fileId, item]));
    const activeIds = new Set(normalized.map(file => file.id));

    current.forEach((item, id) => {
        if (!activeIds.has(id)) item.remove();
    });

    normalized.forEach(file => {
        const oldItem = current.get(file.id);
        const item = oldItem || createFileItem(file);
        if (oldItem) {
            item.className = file.sender === 'browser:' + clientId ? 'mine' : 'peer';
            if (isImageName(file.name)) item.classList.add('image-item');
            const link = item.querySelector('a');
            const size = item.querySelector('small');
            const url = fileUrl(file);
            if (link) {
                link.href = url;
                link.download = file.name || '附件';
                link.textContent = file.name || '未命名';
            }
            if (size) size.textContent = formatSize(file.size);
            const preview = item.querySelector('img');
            if (preview && preview.src !== new URL(url, location.href).href) preview.src = url;
        }
        fileList.appendChild(item);
    });
}

let refreshInFlight = false;
let refreshQueued = false;

async function refreshFiles() {
    if (refreshInFlight) {
        refreshQueued = true;
        return;
    }
    refreshInFlight = true;
    try {
        const response = await fetch('/api/files?_=' + Date.now(), { cache: 'no-store' });
        if (!response.ok) throw new Error('HTTP ' + response.status);
        reconcileFiles(await response.json());
    } catch (_) {
        setConnectionState(false);
    } finally {
        refreshInFlight = false;
        if (refreshQueued) {
            refreshQueued = false;
            refreshFiles();
        }
    }
}

async function uploadFile(file) {
    if (!file) return;
    try {
        const response = await fetch('/upload?name=' + encodeURIComponent(file.name || '消息.txt') + '&client=' + encodeURIComponent(clientId), {
            method: 'PUT',
            headers: {
                'content-type': file.type || 'application/octet-stream',
                // Safari/部分移动浏览器使用 chunked PUT，无法由网页设置 Content-Length。
                'x-file-size': String(file.size)
            },
            body: file
        });
        if (!response.ok) {
            const reason = await response.text();
            throw new Error(reason || ('HTTP ' + response.status));
        }
        if (messageInput) messageInput.value = '';
        await refreshFiles();
    } catch (error) {
        alert('发送失败：' + (error.message || '请刷新页面后重试'));
    }
}

uploadForm.onsubmit = event => {
    event.preventDefault();
    const text = messageInput.value;
    if (text.trim()) uploadFile(new File([text], '消息.txt', { type: 'text/plain' }));
    else messageInput.focus();
};

attachmentButton.onclick = event => {
    event.stopPropagation();
    attachmentSheet.classList.add('open');
    const buttonRect = attachmentButton.getBoundingClientRect();
    attachmentSheet.style.left = (buttonRect.left + 8) + 'px';
    attachmentSheet.style.top = (buttonRect.top - attachmentSheet.offsetHeight - 16) + 'px';
};

['camera-capture', 'gallery', 'file-picker'].forEach(id => {
    const picker = document.getElementById(id);
    if (!picker) return;
    picker.onchange = event => {
        const file = event.target.files && event.target.files[0];
        if (file) {
            attachmentSheet.classList.remove('open');
            uploadFile(file);
        }
        event.target.value = '';
    };
});

attachmentSheet.onclick = event => {
    if (event.target.closest('.sheet-close')) {
        attachmentSheet.classList.remove('open');
        return;
    }
    const pickerId = event.target.closest('[data-picker]')?.dataset.picker;
    if (pickerId) {
        const picker = document.getElementById(pickerId);
        if (picker) {
            // 先关闭菜单；用户取消系统选择器时也不会留下一个悬空菜单。
            attachmentSheet.classList.remove('open');
            picker.click();
        }
    }
};

document.addEventListener('click', event => {
    if (attachmentSheet.classList.contains('open') && !attachmentSheet.contains(event.target) && event.target !== attachmentButton) {
        attachmentSheet.classList.remove('open');
    }
});

async function heartbeat() {
    try {
        const response = await fetch('/api/presence?_=' + Date.now(), { cache: 'no-store' });
        setConnectionState(response.ok);
    } catch (_) {
        setConnectionState(false);
    }
}

let socket;
function connectSocket() {
    try {
        socket = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws');
        socket.onopen = () => {
            setConnectionState(true);
            socket.send('sync');
        };
        socket.onmessage = event => {
            try {
                const payload = JSON.parse(event.data);
                if (payload.type === 'snapshot') reconcileFiles(payload.files);
                else refreshFiles();
            } catch (_) {
                refreshFiles();
            }
        };
        socket.onclose = () => {
            setConnectionState(false);
            setTimeout(connectSocket, 1000);
        };
        socket.onerror = () => setConnectionState(false);
    } catch (_) {
        setTimeout(connectSocket, 1000);
    }
}

heartbeat();
refreshFiles();
setInterval(heartbeat, 2000);
setInterval(refreshFiles, 5000);
connectSocket();
</script>"""
}

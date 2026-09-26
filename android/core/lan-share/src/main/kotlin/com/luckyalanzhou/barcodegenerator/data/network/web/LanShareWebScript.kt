package com.luckyalanzhou.barcodegenerator.data.network.web

/** 浏览器传输页的交互脚本模板。 */
internal object LanShareWebScript {
    fun render() = """<script>
const fileList = document.getElementById('files');
const uploadForm = document.getElementById('upload-form');
const messageInput = document.getElementById('message');
const connectionStatus = document.getElementById('connection-status');
const attachmentSheet = document.getElementById('attachment-sheet');
const attachmentButton = document.getElementById('attachment-button');
const imageViewer = document.getElementById('image-viewer');
const imageViewerStage = document.getElementById('image-viewer-stage');
const imageViewerImage = document.getElementById('image-viewer-image');
const imageViewerTitle = document.getElementById('image-viewer-title');
const imageViewerClose = document.getElementById('image-viewer-close');
const isIOSDevice = /iPad|iPhone|iPod/.test(navigator.userAgent) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const clientIdKey = 'lanShareClientId';
const accessToken = new URL(location.href).searchParams.get('token') || '';

function authorizedUrl(path) {
    const url = new URL(path, location.origin);
    url.searchParams.set('token', accessToken);
    return url.pathname + url.search;
}

let clientId = localStorage.getItem(clientIdKey);
if (!clientId) {
    clientId = 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
    localStorage.setItem(clientIdKey, clientId);
}

function isImageName(name) {
    return /\.(jpg|jpeg|png|gif|webp|heic|heif)$/i.test(name || '');
}

function isOwnFile(file) {
    // Older browser uploads have no client ID, so the server can only label them "browser".
    return file.sender === 'browser:' + clientId || file.sender === 'browser';
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
    return authorizedUrl('/api/download/' + encodeURIComponent(file.id) + '?v=' + encodeURIComponent(file.modifiedAt || ''));
}

function createFileItem(file) {
    const item = document.createElement('li');
    const url = fileUrl(file);
    item.dataset.fileId = file.id;
    item.className = isOwnFile(file) ? 'mine' : 'peer';

    if (isImageName(file.name)) {
        const preview = document.createElement('img');
        preview.src = url;
        preview.className = 'media-preview';
        preview.alt = file.name || '图片';
        preview.loading = 'lazy';
        preview.decoding = 'async';
        preview.tabIndex = 0;
        preview.setAttribute('role', 'button');
        preview.setAttribute('aria-label', '预览图片：' + preview.alt);
        preview.addEventListener('keydown', event => {
            if (event.key !== 'Enter' && event.key !== ' ') return;
            event.preventDefault();
            preview.click();
        });
        item.classList.add('image-item');
        item.appendChild(preview);
    }

    const imageFile = isImageName(file.name);
    const name = document.createElement(imageFile ? 'a' : 'span');
    name.className = 'file-name';
    name.textContent = file.name || '未命名';
    if (imageFile) {
        name.href = url;
        name.download = file.name || '附件';
    }
    item.appendChild(name);

    const size = document.createElement('small');
    size.textContent = formatSize(file.size);
    item.appendChild(size);

    if (!imageFile) {
        const download = document.createElement('a');
        download.href = url;
        download.download = file.name || '附件';
        download.className = 'download';
        download.setAttribute('aria-label', '下载 ' + (file.name || '附件'));
        download.textContent = '下载';
        item.appendChild(download);
    }
    return item;
}

let previewScale = 1;
let previewOffsetX = 0;
let previewOffsetY = 0;
let previewOrigin = null;
let previewDrag = null;

function applyPreviewTransform() {
    imageViewerImage.style.transform = 'translate(' + previewOffsetX + 'px, ' + previewOffsetY + 'px) scale(' + previewScale + ')';
    imageViewerImage.classList.toggle('zoomed', previewScale > 1);
    imageViewerImage.classList.remove('dragging');
}

function resetPreviewTransform() {
    previewScale = 1;
    previewOffsetX = 0;
    previewOffsetY = 0;
    previewDrag = null;
    applyPreviewTransform();
}

function clampPreviewOffsets(baseWidth, baseHeight) {
    const maxX = Math.max(0, (baseWidth * previewScale - imageViewerStage.clientWidth) / 2);
    const maxY = Math.max(0, (baseHeight * previewScale - imageViewerStage.clientHeight) / 2);
    previewOffsetX = Math.max(-maxX, Math.min(maxX, previewOffsetX));
    previewOffsetY = Math.max(-maxY, Math.min(maxY, previewOffsetY));
}

function closeImageViewer() {
    imageViewer.hidden = true;
    document.body.classList.remove('preview-open');
    if (previewOrigin && previewOrigin.isConnected) previewOrigin.focus();
    previewOrigin = null;
    previewDrag = null;
}

fileList.addEventListener('click', event => {
    const image = event.target.closest('img.media-preview');
    if (!image) return;
    event.preventDefault();
    previewOrigin = image;
    imageViewerImage.src = image.currentSrc || image.src;
    imageViewerImage.alt = image.alt || '图片预览';
    imageViewerTitle.textContent = image.alt || '图片预览';
    resetPreviewTransform();
    imageViewer.hidden = false;
    document.body.classList.add('preview-open');
    imageViewerClose.focus();
});

imageViewerClose.addEventListener('click', closeImageViewer);
imageViewer.addEventListener('click', event => {
    if (event.target === imageViewer || event.target === imageViewerStage) closeImageViewer();
});
imageViewer.addEventListener('wheel', event => {
    if (imageViewer.hidden) return;
    event.preventDefault();
    const nextScale = Math.max(1, Math.min(5, previewScale * (event.deltaY < 0 ? 1.15 : 1 / 1.15)));
    if (nextScale === previewScale) return;
    const rect = imageViewerImage.getBoundingClientRect();
    const factor = nextScale / previewScale;
    const baseWidth = rect.width / previewScale;
    const baseHeight = rect.height / previewScale;
    previewOffsetX += (event.clientX - (rect.left + rect.width / 2)) * (1 - factor);
    previewOffsetY += (event.clientY - (rect.top + rect.height / 2)) * (1 - factor);
    previewScale = nextScale;
    if (previewScale === 1) {
        previewOffsetX = 0;
        previewOffsetY = 0;
    } else {
        clampPreviewOffsets(baseWidth, baseHeight);
    }
    applyPreviewTransform();
}, { passive: false });

imageViewerImage.addEventListener('pointerdown', event => {
    if (previewScale <= 1 || (event.pointerType === 'mouse' && event.button !== 0)) return;
    previewDrag = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, offsetX: previewOffsetX, offsetY: previewOffsetY };
    imageViewerImage.setPointerCapture(event.pointerId);
    imageViewerImage.classList.add('dragging');
    event.preventDefault();
});
imageViewerImage.addEventListener('pointermove', event => {
    if (!previewDrag || previewDrag.pointerId !== event.pointerId) return;
    const rect = imageViewerImage.getBoundingClientRect();
    previewOffsetX = previewDrag.offsetX + event.clientX - previewDrag.x;
    previewOffsetY = previewDrag.offsetY + event.clientY - previewDrag.y;
    clampPreviewOffsets(rect.width / previewScale, rect.height / previewScale);
    imageViewerImage.style.transform = 'translate(' + previewOffsetX + 'px, ' + previewOffsetY + 'px) scale(' + previewScale + ')';
});
function finishPreviewDrag(event) {
    if (!previewDrag || previewDrag.pointerId !== event.pointerId) return;
    previewDrag = null;
    imageViewerImage.classList.remove('dragging');
}
imageViewerImage.addEventListener('pointerup', finishPreviewDrag);
imageViewerImage.addEventListener('pointercancel', finishPreviewDrag);

document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !imageViewer.hidden) {
        event.preventDefault();
        closeImageViewer();
    }
});

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
            item.className = isOwnFile(file) ? 'mine' : 'peer';
            if (isImageName(file.name)) item.classList.add('image-item');
            const name = item.querySelector('.file-name');
            const download = item.querySelector('.download');
            const size = item.querySelector('small');
            const url = fileUrl(file);
            if (name) {
                name.textContent = file.name || '未命名';
                if (name.tagName === 'A') {
                    name.href = url;
                    name.download = file.name || '附件';
                }
            }
            if (download) {
                download.href = url;
                download.download = file.name || '附件';
                download.setAttribute('aria-label', '下载 ' + (file.name || '附件'));
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
        const response = await fetch(authorizedUrl('/api/files?_=' + Date.now()), { cache: 'no-store' });
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
        const response = await fetch(authorizedUrl('/upload?name=' + encodeURIComponent(file.name || '消息.txt') + '&client=' + encodeURIComponent(clientId)), {
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
    // iOS Safari already presents Photos, Camera, Files, and Cancel for a file input.
    // Open it directly to avoid stacking the web sheet over Safari's native chooser.
    if (isIOSDevice) {
        document.getElementById('file-picker').click();
        return;
    }
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
        const response = await fetch(authorizedUrl('/api/presence?_=' + Date.now()), { cache: 'no-store' });
        setConnectionState(response.ok);
    } catch (_) {
        setConnectionState(false);
    }
}

let socket;
function connectSocket() {
    try {
        socket = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + authorizedUrl('/ws'));
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

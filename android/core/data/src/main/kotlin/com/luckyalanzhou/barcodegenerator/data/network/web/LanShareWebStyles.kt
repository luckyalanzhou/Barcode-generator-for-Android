package com.luckyalanzhou.barcodegenerator.data.network.web

/** 浏览器传输页的 CSS 模板。 */
internal object LanShareWebStyles {
    fun render() = """<style>
:root{color-scheme:light dark;--bg:#f4f6fb;--panel:#fff;--text:#172033;--line:#d9e0ea;--sub:#667085;--bubble-peer:rgba(255,255,255,.72);--bubble-mine:rgba(10,132,255,.88);--bubble-border:rgba(71,91,122,.18);--bubble-mine-border:rgba(147,197,253,.78);--bubble-shadow:rgba(31,41,55,.1);--preview-bg:rgba(15,23,42,.08);--link:#075fbe;--accent:#0a84ff}
@media(prefers-color-scheme:dark){:root{--bg:#000;--panel:#1d1d1f;--text:#f5f5f7;--line:#3a3a3c;--sub:#a1a1a6;--bubble-peer:rgba(44,44,46,.94);--bubble-mine:rgba(10,132,255,.92);--bubble-border:rgba(255,255,255,.14);--bubble-mine-border:rgba(120,190,255,.72);--bubble-shadow:rgba(0,0,0,.38);--preview-bg:rgba(255,255,255,.08);--link:#72b7ff;--accent:#0a84ff}}
*{box-sizing:border-box}html,body{min-width:0}body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;margin:0;color:var(--text);background:var(--bg)}main{width:100%;max-width:100%;margin:auto;min-height:100vh;padding:0 0 86px}.bar{display:flex;align-items:center;justify-content:space-between;padding:16px 14px 10px;font-size:19px;font-weight:700}.connection-status{font-size:12px;font-weight:500;color:var(--sub);white-space:nowrap}.connection-status.connected{color:#22c55e}ul{display:flex;flex-direction:column;align-items:flex-start;gap:8px;padding:0 10px;margin:0}li{display:grid;align-items:center;gap:4px 9px;width:fit-content;max-width:calc(100% - 8px);padding:10px 12px;border:1px solid var(--bubble-border);border-radius:18px;list-style:none;background:var(--bubble-peer);box-shadow:0 8px 22px var(--bubble-shadow),inset 0 1px rgba(255,255,255,.32);backdrop-filter:blur(20px);-webkit-backdrop-filter:blur(20px)}li:not(.image-item){grid-template-columns:auto minmax(0,1fr) auto;grid-template-areas:"icon name download" "icon size download"}li.mine{align-self:flex-end;background:var(--bubble-mine);border-color:var(--bubble-mine-border);color:#fff}li.peer{align-self:flex-start}li.mine a:first-of-type,li.mine small{color:#fff}li:not(.image-item)::before{content:'📎';grid-area:icon;align-self:center;font-size:22px}li a:first-of-type{min-width:0;overflow:hidden;color:var(--link);white-space:nowrap;text-overflow:ellipsis;overflow-wrap:anywhere}li:not(.image-item) a:first-of-type{grid-area:name}small{display:block;grid-area:size;min-width:0;color:var(--sub);margin:0;font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.download{grid-area:download;align-self:center;padding:8px 12px;border:1px solid var(--line);border-radius:999px;color:var(--accent);text-decoration:none;background:rgba(255,255,255,.18);white-space:nowrap}.mine .download{color:#fff;border-color:rgba(255,255,255,.5);background:rgba(0,0,0,.12)}.image-item{display:flex;flex-direction:column;align-items:center;gap:7px;padding:7px 7px 9px;max-width:min(78vw,360px)}.image-item a{width:100%;text-align:center}.media-preview{display:block;width:auto;max-width:100%;height:auto;max-height:260px;object-fit:contain;align-self:center;border-radius:12px;background:var(--preview-bg)}.image-item small{max-width:100%;text-align:center}.bottom{position:fixed;bottom:0;left:0;right:0;background:var(--panel);padding:10px 10px max(10px,env(safe-area-inset-bottom));z-index:5}.bottom form{display:flex;align-items:center;gap:8px;width:100%;max-width:100%;margin:auto}.file-picker,.send-button{display:flex;align-items:center;justify-content:center;width:44px;height:44px;border:1px solid var(--line);border-radius:22px;cursor:pointer;flex:0 0 44px}.message-input{min-width:0;flex:1;height:44px;padding:0 14px;border:1px solid var(--line);border-radius:22px;background:transparent;color:var(--text);font:inherit}.send-button{background:var(--accent);color:white}.attachment-sheet{display:none;position:fixed;width:154px;padding:6px;border:1px solid var(--line);border-radius:20px;background:var(--panel);box-shadow:0 16px 40px var(--bubble-shadow);z-index:10}.attachment-sheet.open{display:flex;flex-direction:column}.attachment-sheet button{width:100%;min-height:38px;border:0;border-top:1px solid rgba(71,91,122,.2);background:transparent;color:var(--text);font:inherit}.attachment-sheet button:first-child{border-top:0}
/* Phone: default narrow single-column touch layout. */
@media (min-width:600px) and (max-width:1199px){main{max-width:820px;padding-bottom:96px}.bar{padding:22px 28px 14px;font-size:22px}.connection-status{font-size:14px}ul{gap:12px;padding:0 24px}li{max-width:72%;padding:12px 15px;border-radius:20px}.image-item{max-width:min(56vw,460px)}.media-preview{max-height:380px}.bottom{padding:14px 24px max(14px,env(safe-area-inset-bottom))}.bottom form{max-width:760px}.file-picker,.send-button{width:48px;height:48px;flex-basis:48px}.message-input{height:48px}}
/* PC: wide centered workspace with larger previews and comfortable controls. */
@media (min-width:1200px){body{font-size:16px}main{max-width:1180px;padding-bottom:110px}.bar{padding:28px 42px 18px;font-size:26px}.connection-status{font-size:15px}ul{gap:14px;padding:0 42px}li{max-width:58%;padding:14px 18px;border-radius:22px;gap:5px 12px}.image-item{max-width:520px;padding:10px 10px 12px}.media-preview{max-height:520px}.bottom{padding:18px 42px max(18px,env(safe-area-inset-bottom))}.bottom form{max-width:1080px;gap:12px}.file-picker,.send-button{width:52px;height:52px;flex-basis:52px}.message-input{height:52px;font-size:16px}.download{padding:9px 15px}}
</style>
<style>
/* Screenshot-aligned shell: full-width header and bottom composer. */
:root{--web-header-height:74px}
body{background:var(--bg)}
.bar{width:100%;height:var(--web-header-height);padding:0 24px;background:var(--panel);border-bottom:1px solid var(--line);font-size:24px;line-height:1}
.bar>span:first-child{font-weight:700;color:var(--text)}
main{width:100%;max-width:none;min-height:calc(100vh - var(--web-header-height));background:var(--bg);padding-bottom:110px}
.bottom{padding:14px 24px max(14px,env(safe-area-inset-bottom));background:var(--panel);border-top:1px solid var(--line)}
.bottom form{max-width:none}
.file-picker{width:44px;height:56px;flex-basis:44px;border:0;border-radius:0;background:transparent;color:var(--accent);font-size:34px;font-weight:500;line-height:1;padding:0}
.message-input{height:56px;border-radius:28px;border-color:var(--line);background:transparent;font-size:18px;padding:0 22px}
.send-button{width:88px;height:56px;flex-basis:88px;border:0;border-radius:28px;background:var(--accent);font-size:18px;font-weight:700}
@media (max-width:599px){.bar{height:62px;padding:0 16px;font-size:20px}.connection-status{font-size:12px}main{min-height:calc(100vh - 62px);padding-bottom:88px}.bottom{padding:10px 12px max(10px,env(safe-area-inset-bottom))}.message-input{height:48px;border-radius:24px;font-size:16px;padding:0 16px}.file-picker{height:48px;width:36px;flex-basis:36px;font-size:30px}.send-button{height:48px;width:68px;flex-basis:68px;border-radius:24px;font-size:16px}}
@media (min-width:600px) and (max-width:1199px){.bar{height:70px;padding:0 28px;font-size:22px}main{min-height:calc(100vh - 70px)}.bottom{padding:14px 28px max(14px,env(safe-area-inset-bottom))}.send-button{width:80px;flex-basis:80px}}
@media (min-width:1200px){.bar{height:74px;padding:0 24px;font-size:24px}main{min-height:calc(100vh - 74px)}.bottom{padding:14px 32px max(14px,env(safe-area-inset-bottom))}}
</style>
<style>
/* Non-image downloads use an explicit button; image names remain direct download links. */
.file-name{display:block;min-width:0;overflow:hidden;color:var(--link);white-space:nowrap;text-overflow:ellipsis;overflow-wrap:anywhere}
li:not(.image-item) .file-name{grid-area:name}
li:not(.image-item) a.download{grid-area:download;min-width:0;overflow:visible;text-overflow:clip}
li.mine .file-name{color:#fff}
li a.download{color:var(--accent)}
li.mine a.download{color:#fff}
.image-item .file-name{width:100%;text-align:center}
.image-item a.download{width:auto;text-align:center}
</style></head>"""
}

/**
 * 轻量手写签名：配合 fragments / 录分弹窗 signature-modal 与隐藏域 name=signature。
 * openSignatureModal(inputEl?) — 可传入目标隐藏域；预览/提示优先取同表单内
 * [data-sig-preview] / [data-sig-hint]，否则回退 #signaturePreview / #signatureHint。
 * WCAG 2.5.7：提供文字签名作为非拖拽等价操作。
 * 提交规则：必须有墨迹，或已点「使用文字签名」并绘入画板；验收呈现强制 30° 淡色水印。
 */
(function (global) {
    'use strict';

    let signatureModal = null;
    let sigCtx = null;
    let drawing = false;
    let targetInput = null;
    let canvasBound = false;
    /** 本次打开后是否已在画布上留下手写墨迹 */
    let hasInk = false;
    /** 是否已通过「使用文字签名」将文字绘入画板 */
    let textOnCanvas = false;

    const TEXT_SIG_PREFIX = 'TEXT_SIG:';
    const WM_COLOR = 'rgba(120, 140, 160, 0.18)';
    const WM_ANGLE = -Math.PI / 6; // 30°

    function resolvePreviewAndHint() {
        const form = targetInput && targetInput.closest ? targetInput.closest('form') : null;
        const preview = (form && form.querySelector('[data-sig-preview]'))
            || document.getElementById('signaturePreview');
        const hint = (form && form.querySelector('[data-sig-hint]'))
            || document.getElementById('signatureHint');
        return { preview: preview, hint: hint };
    }

    function pad2(n) {
        return n < 10 ? '0' + n : String(n);
    }

    function formatNowLocal() {
        const d = new Date();
        return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate())
            + ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes()) + ':' + pad2(d.getSeconds());
    }

    function resolveSignerUsername() {
        const modalEl = document.getElementById('signatureModal');
        if (modalEl && modalEl.getAttribute('data-signer-username')) {
            return String(modalEl.getAttribute('data-signer-username')).trim();
        }
        const meta = document.getElementById('groupMatchScoreModalMeta');
        if (meta && meta.dataset && meta.dataset.currentUsername) {
            return String(meta.dataset.currentUsername).trim();
        }
        return 'user';
    }

    /** 签字人用户 ID（雪花字符串）；无则空串 */
    function resolveSignerUserId() {
        const modalEl = document.getElementById('signatureModal');
        if (modalEl && modalEl.getAttribute('data-signer-user-id')) {
            return String(modalEl.getAttribute('data-signer-user-id')).trim();
        }
        const meta = document.getElementById('groupMatchScoreModalMeta');
        if (meta && meta.dataset && meta.dataset.currentUserId) {
            return String(meta.dataset.currentUserId).trim();
        }
        return '';
    }

    function newAcceptanceUuid() {
        if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
            return crypto.randomUUID();
        }
        return 'sig-' + Date.now().toString(36) + '-' + Math.random().toString(16).slice(2, 10);
    }

    function buildWatermarkMeta(overrides) {
        const o = overrides && typeof overrides === 'object' ? overrides : {};
        const username = (o.username != null && String(o.username).trim())
            ? String(o.username).trim()
            : (resolveSignerUsername() || 'user');
        const uuid = (o.uuid != null && String(o.uuid).trim())
            ? String(o.uuid).trim()
            : (resolveSignerUserId() || newAcceptanceUuid());
        const acceptedAt = (o.acceptedAt != null && String(o.acceptedAt).trim())
            ? String(o.acceptedAt).trim()
            : formatNowLocal();
        return { username: username, uuid: uuid, acceptedAt: acceptedAt };
    }

    /** 固定三元组：用户名、ID、验收时间（缺一补占位，保证 FIFO 三项齐全） */
    function watermarkCycleLabels(meta) {
        const wm = buildWatermarkMeta(meta);
        return [
            wm.username || 'user',
            wm.uuid || '-',
            wm.acceptedAt || '-'
        ];
    }

    /**
     * 在源图上叠加 30° 倾斜淡色水印。
     * @returns {string} data URL；失败返回原 dataUrl 或 ''
     */
    function bakeSignatureWatermark(sourceDataUrl, meta) {
        const labels = watermarkCycleLabels(meta);
        return new Promise((resolve) => {
            const img = new Image();
            img.onload = () => {
                try {
                    const w = Math.max(img.naturalWidth || img.width || 420, 120);
                    const h = Math.max(img.naturalHeight || img.height || 180, 60);
                    const c = document.createElement('canvas');
                    c.width = w;
                    c.height = h;
                    const ctx = c.getContext('2d');
                    if (!ctx) {
                        resolve(sourceDataUrl || '');
                        return;
                    }
                    ctx.fillStyle = '#fff';
                    ctx.fillRect(0, 0, w, h);
                    ctx.drawImage(img, 0, 0, w, h);
                    paintWatermarkGrid(ctx, w, h, labels);
                    resolve(c.toDataURL('image/png'));
                } catch (e) {
                    resolve(sourceDataUrl || '');
                }
            };
            img.onerror = () => resolve(sourceDataUrl || '');
            img.src = sourceDataUrl;
        });
    }

    /**
     * 密铺 30° 水印。每一行 FIFO 左移一项：
     * 行0: 用户名 → ID → 时间 → 用户名 → …
     * 行1: ID → 时间 → 用户名 → …
     * 行2: 时间 → 用户名 → ID → …
     */
    function paintWatermarkGrid(ctx, w, h, labels) {
        if (!ctx || !labels || !labels.length) return;
        const cycle = [];
        for (let i = 0; i < labels.length; i++) {
            const t = String(labels[i] == null ? '' : labels[i]).trim();
            cycle.push(t || '-');
        }
        while (cycle.length < 3) {
            cycle.push(cycle[cycle.length - 1] || '-');
        }
        const n = 3;
        const trio = cycle.slice(0, n);

        ctx.save();
        ctx.translate(w / 2, h / 2);
        ctx.rotate(WM_ANGLE);
        ctx.fillStyle = WM_COLOR;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        const fontSize = Math.max(10, Math.min(13, Math.floor(Math.min(w, h) / 14)));
        ctx.font = fontSize + 'px "Segoe UI", "Microsoft YaHei", "PingFang SC", sans-serif';

        let maxTw = 0;
        for (let i = 0; i < n; i++) {
            maxTw = Math.max(maxTw, ctx.measureText(trio[i]).width);
        }
        // 列距按最长文案收紧，行距略大于字号，铺满旋转后包围盒
        const colGap = Math.max(maxTw + fontSize * 1.4, fontSize * 7);
        const rowGap = fontSize * 1.75;
        const cover = Math.ceil(Math.sqrt(w * w + h * h) * 1.35);
        const rows = Math.ceil(cover / rowGap) + 3;
        const cols = Math.ceil(cover / colGap) + 3;

        for (let r = -rows; r <= rows; r++) {
            // 每行起点 FIFO 左移 1：r=0→用户名, r=1→ID, r=2→时间
            const rowOffset = ((r % n) + n) % n;
            for (let c = -cols; c <= cols; c++) {
                const colIndex = c + cols;
                const idx = (rowOffset + colIndex) % n;
                ctx.fillText(trio[idx], c * colGap, r * rowGap);
            }
        }
        ctx.restore();
    }

    /** 同步版：把已有 canvas 内容加水印后导出 */
    function bakeCanvasWatermarkSync(canvas, meta) {
        if (!canvas) return '';
        const labels = watermarkCycleLabels(meta);
        const w = canvas.width;
        const h = canvas.height;
        if (w < 2 || h < 2) return '';
        try {
            const out = document.createElement('canvas');
            out.width = w;
            out.height = h;
            const ctx = out.getContext('2d');
            if (!ctx) return canvas.toDataURL('image/png');
            ctx.fillStyle = '#fff';
            ctx.fillRect(0, 0, w, h);
            ctx.drawImage(canvas, 0, 0);
            paintWatermarkGrid(ctx, w, h, labels);
            return out.toDataURL('image/png');
        } catch (e) {
            try {
                return canvas.toDataURL('image/png');
            } catch (e2) {
                return '';
            }
        }
    }

    function isCanvasEffectivelyBlank(canvas) {
        if (!canvas) return true;
        try {
            const ctx = canvas.getContext('2d');
            if (!ctx) return true;
            const w = canvas.width;
            const h = canvas.height;
            if (w < 2 || h < 2) return true;
            const data = ctx.getImageData(0, 0, w, h).data;
            const step = Math.max(1, Math.floor((w * h) / 4000)) * 4;
            for (let i = 0; i < data.length; i += step) {
                const r = data[i];
                const g = data[i + 1];
                const b = data[i + 2];
                const a = data[i + 3];
                if (a > 8 && (r < 245 || g < 245 || b < 245)) {
                    return false;
                }
            }
            return true;
        } catch (e) {
            return !(hasInk || textOnCanvas);
        }
    }

    function openSignatureModal(inputEl) {
        targetInput = inputEl || document.getElementById('signature')
            || document.querySelector('input[name="signature"]');
        const modalEl = document.getElementById('signatureModal');
        if (!modalEl || typeof bootstrap === 'undefined') {
            alert('签名组件未加载');
            return;
        }
        if (!signatureModal) {
            signatureModal = new bootstrap.Modal(modalEl);
        }
        const textInput = document.getElementById('signatureTextFallback');
        if (textInput) {
            textInput.value = '';
        }
        hasInk = false;
        textOnCanvas = false;
        signatureModal.show();
        setTimeout(initSignatureCanvas, 50);
    }

    function initSignatureCanvas() {
        const canvas = document.getElementById('signatureCanvas');
        if (!canvas) return;
        const ratio = Math.max(window.devicePixelRatio || 1, 1);
        const displayW = canvas.clientWidth || 420;
        const displayH = 180;
        canvas.width = Math.floor(displayW * ratio);
        canvas.height = Math.floor(displayH * ratio);
        if (!canvas.hasAttribute('tabindex')) {
            canvas.setAttribute('tabindex', '0');
        }
        sigCtx = canvas.getContext('2d');
        sigCtx.setTransform(ratio, 0, 0, ratio, 0, 0);
        sigCtx.lineWidth = 2;
        sigCtx.lineCap = 'round';
        sigCtx.strokeStyle = '#111';
        sigCtx.fillStyle = '#fff';
        sigCtx.fillRect(0, 0, displayW, displayH);
        hasInk = false;
        textOnCanvas = false;

        if (canvasBound) return;
        canvasBound = true;

        const pos = (e) => {
            const r = canvas.getBoundingClientRect();
            const src = e.touches && e.touches[0] ? e.touches[0] : e;
            return { x: src.clientX - r.left, y: src.clientY - r.top };
        };
        const start = (e) => {
            drawing = true;
            const p = pos(e);
            sigCtx.beginPath();
            sigCtx.moveTo(p.x, p.y);
            e.preventDefault();
        };
        const move = (e) => {
            if (!drawing) return;
            const p = pos(e);
            sigCtx.lineTo(p.x, p.y);
            sigCtx.stroke();
            hasInk = true;
            textOnCanvas = false;
            e.preventDefault();
        };
        const end = () => { drawing = false; };
        canvas.addEventListener('mousedown', start);
        canvas.addEventListener('mousemove', move);
        canvas.addEventListener('mouseup', end);
        canvas.addEventListener('mouseleave', end);
        canvas.addEventListener('touchstart', start, { passive: false });
        canvas.addEventListener('touchmove', move, { passive: false });
        canvas.addEventListener('touchend', end);
    }

    function clearSignatureCanvas() {
        const canvas = document.getElementById('signatureCanvas');
        if (!canvas || !sigCtx) return;
        const displayW = canvas.clientWidth || 420;
        const displayH = 180;
        sigCtx.save();
        sigCtx.setTransform(1, 0, 0, 1, 0, 0);
        const ratio = Math.max(window.devicePixelRatio || 1, 1);
        sigCtx.setTransform(ratio, 0, 0, ratio, 0, 0);
        sigCtx.fillStyle = '#fff';
        sigCtx.fillRect(0, 0, displayW, displayH);
        sigCtx.restore();
        const textInput = document.getElementById('signatureTextFallback');
        if (textInput) textInput.value = '';
        hasInk = false;
        textOnCanvas = false;
    }

    /**
     * 将文字绘入签名画布（重置 transform），返回 data URL；失败返回 ''。
     */
    function paintTextSignatureToDataUrl(text) {
        const canvas = document.getElementById('signatureCanvas');
        if (!canvas || !text) return '';
        const cssW = Math.max(canvas.clientWidth || 420, 280);
        const cssH = 180;
        const ratio = Math.max(window.devicePixelRatio || 1, 1);
        canvas.width = Math.floor(cssW * ratio);
        canvas.height = Math.floor(cssH * ratio);
        const ctx = canvas.getContext('2d');
        if (!ctx) return '';
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        ctx.scale(ratio, ratio);
        ctx.fillStyle = '#fff';
        ctx.fillRect(0, 0, cssW, cssH);
        ctx.fillStyle = '#111';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        let fontSize = 36;
        const fontFamily = '"Segoe UI", "Microsoft YaHei", "PingFang SC", "Noto Sans SC", sans-serif';
        ctx.font = '600 ' + fontSize + 'px ' + fontFamily;
        while (fontSize > 14 && ctx.measureText(text).width > cssW - 24) {
            fontSize -= 2;
            ctx.font = '600 ' + fontSize + 'px ' + fontFamily;
        }
        ctx.fillText(text, cssW / 2, cssH / 2);
        sigCtx = ctx;
        textOnCanvas = true;
        hasInk = false;
        try {
            return canvas.toDataURL('image/png');
        } catch (err) {
            return '';
        }
    }

    function assertSignaturePresentOnCanvas() {
        const canvas = document.getElementById('signatureCanvas');
        if (!hasInk && !textOnCanvas) {
            alert('请先手写签名，或输入文字后点击「使用文字签名」');
            return false;
        }
        if (isCanvasEffectivelyBlank(canvas)) {
            alert('签名不能为空白，请手写或使用文字签名并呈现到画板');
            return false;
        }
        return true;
    }

    /**
     * 文字签名：必须点本按钮；绘入画板后带水印写入隐藏域（同时保留 TEXT_SIG: 文本便于表格识别）。
     */
    function applyTextSignatureFallback() {
        const textInput = document.getElementById('signatureTextFallback');
        const text = textInput ? String(textInput.value || '').trim() : '';
        if (!text) {
            alert('请先输入文字签名');
            if (textInput) textInput.focus();
            return;
        }
        const painted = paintTextSignatureToDataUrl(text);
        if (!painted || isCanvasEffectivelyBlank(document.getElementById('signatureCanvas'))) {
            alert('文字签名未能绘入画板，请重试');
            return;
        }
        const canvas = document.getElementById('signatureCanvas');
        const watermarked = bakeCanvasWatermarkSync(canvas, {
            username: resolveSignerUsername() || text
        });
        const data = watermarked || painted;
        if (targetInput) {
            // 最终呈现用带水印图；绘板失败时回退 TEXT_SIG: 文本
            targetInput.value = data || (TEXT_SIG_PREFIX + text);
        }
        if (typeof global.__cmSharedSignatureData === 'string' || '__cmSharedSignatureData' in global) {
            global.__cmSharedSignatureData = data || (TEXT_SIG_PREFIX + text);
        }
        const ui = resolvePreviewAndHint();
        if (ui.preview) {
            if (data) {
                ui.preview.src = data;
                ui.preview.classList.remove('d-none');
            } else {
                ui.preview.classList.add('d-none');
            }
        }
        if (ui.hint) {
            ui.hint.textContent = '已采集文字签名：' + text;
            ui.hint.classList.remove('text-danger');
            ui.hint.classList.add('text-success');
        }
        if (signatureModal) {
            signatureModal.hide();
        }
    }

    function saveSignatureCanvas() {
        const canvas = document.getElementById('signatureCanvas');
        if (!canvas) return;
        if (!assertSignaturePresentOnCanvas()) {
            return;
        }
        const watermarked = bakeCanvasWatermarkSync(canvas);
        const data = watermarked || canvas.toDataURL('image/png');
        if (!data || data.length < 32) {
            alert('签名导出失败，请重试');
            return;
        }
        if (targetInput) {
            targetInput.value = data;
        }
        if (typeof global.__cmSharedSignatureData === 'string' || '__cmSharedSignatureData' in global) {
            global.__cmSharedSignatureData = data;
        }
        const ui = resolvePreviewAndHint();
        if (ui.preview) {
            ui.preview.src = data;
            ui.preview.classList.remove('d-none');
        }
        if (ui.hint) {
            ui.hint.textContent = textOnCanvas ? '已采集文字签名' : '已采集手写签名';
            ui.hint.classList.remove('text-danger');
            ui.hint.classList.add('text-success');
        }
        if (signatureModal) {
            signatureModal.hide();
        }
    }

    function isBlankLookingSignature(sig) {
        const s = String(sig == null ? '' : sig).trim();
        if (!s) return true;
        if (s.startsWith(TEXT_SIG_PREFIX)) {
            return s.length <= TEXT_SIG_PREFIX.length || !s.slice(TEXT_SIG_PREFIX.length).trim();
        }
        if (s.startsWith('data:image/')) {
            // 空白白底 PNG 通常较短；再结合「全白」启发式不够可靠，长度阈值
            // 真正空白校验在提交前已靠画布像素完成；这里拦明显空串
            const b64 = s.indexOf(',') >= 0 ? s.slice(s.indexOf(',') + 1) : '';
            return b64.length < 24;
        }
        return false;
    }

    function requireSignatureBeforeSubmit(form) {
        if (!form) return true;
        const input = form.querySelector('input[name="signature"]');
        const sig = input ? String(input.value || '').trim() : '';
        if (!sig || isBlankLookingSignature(sig)) {
            alert('请先完成电子签名（手写或点击「使用文字签名」并呈现到画板）');
            return false;
        }
        return true;
    }

    /**
     * 展示用：去掉旧淡色水印像素，只保留深色笔迹，再按 FIFO 密铺新水印。
     */
    function rebakeCleanSignatureWatermark(sourceDataUrl, meta) {
        const labels = watermarkCycleLabels(meta);
        return new Promise((resolve) => {
            const img = new Image();
            img.onload = () => {
                try {
                    const w = Math.max(img.naturalWidth || img.width || 420, 120);
                    const h = Math.max(img.naturalHeight || img.height || 180, 60);
                    const c = document.createElement('canvas');
                    c.width = w;
                    c.height = h;
                    const ctx = c.getContext('2d');
                    if (!ctx) {
                        resolve(sourceDataUrl || '');
                        return;
                    }
                    ctx.fillStyle = '#fff';
                    ctx.fillRect(0, 0, w, h);
                    ctx.drawImage(img, 0, 0, w, h);
                    try {
                        const imageData = ctx.getImageData(0, 0, w, h);
                        const px = imageData.data;
                        for (let i = 0; i < px.length; i += 4) {
                            const r = px[i];
                            const g = px[i + 1];
                            const b = px[i + 2];
                            // 淡水印约 230+；手写/文字签名为深色
                            if (r > 200 && g > 200 && b > 200) {
                                px[i] = 255;
                                px[i + 1] = 255;
                                px[i + 2] = 255;
                                px[i + 3] = 255;
                            }
                        }
                        ctx.putImageData(imageData, 0, 0);
                    } catch (stripErr) {
                        /* cross-origin 等则跳过剥离，仍叠新水印 */
                    }
                    paintWatermarkGrid(ctx, w, h, labels);
                    resolve(c.toDataURL('image/png'));
                } catch (e) {
                    resolve(sourceDataUrl || '');
                }
            };
            img.onerror = () => resolve(sourceDataUrl || '');
            img.src = sourceDataUrl;
        });
    }

    /**
     * 将签名最终呈现为带水印图片（用于修改记录 / 验收表）。
     * el: 容器；sig: 原始签名；meta: {username,uuid,acceptedAt}
     */
    function presentSignatureWithWatermark(el, sig, meta) {
        if (!el) return;
        const raw = String(sig == null ? '' : sig).trim();
        el.innerHTML = '';
        if (!raw) {
            el.textContent = '无';
            el.classList.add('text-muted');
            return;
        }
        const labels = watermarkCycleLabels(meta);
        const finishImg = (dataUrl) => {
            const img = document.createElement('img');
            img.alt = '签名';
            img.className = 'cm-sig-present';
            img.style.maxHeight = '64px';
            img.style.background = '#fff';
            img.loading = 'lazy';
            img.src = dataUrl;
            el.appendChild(img);
        };
        if (raw.startsWith(TEXT_SIG_PREFIX)) {
            const text = raw.slice(TEXT_SIG_PREFIX.length).trim() || '(文字签名)';
            const c = document.createElement('canvas');
            c.width = 420;
            c.height = 180;
            const ctx = c.getContext('2d');
            if (ctx) {
                ctx.fillStyle = '#fff';
                ctx.fillRect(0, 0, 420, 180);
                ctx.fillStyle = '#111';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';
                ctx.font = '600 36px "Segoe UI", "Microsoft YaHei", "PingFang SC", sans-serif';
                ctx.fillText(text, 210, 90);
                paintWatermarkGrid(ctx, 420, 180, labels);
                finishImg(c.toDataURL('image/png'));
            } else {
                el.textContent = text;
            }
            return;
        }
        if (raw.startsWith('data:image/')) {
            rebakeCleanSignatureWatermark(raw, meta).then((out) => finishImg(out || raw));
            return;
        }
        el.textContent = raw;
    }

    function enhanceSignatureCells(root) {
        const scope = root || document;
        const cells = scope.querySelectorAll('[data-cm-sig]');
        cells.forEach((cell) => {
            const sig = cell.getAttribute('data-cm-sig') || '';
            presentSignatureWithWatermark(cell, sig, {
                username: cell.getAttribute('data-wm-user') || '',
                uuid: cell.getAttribute('data-wm-uuid') || '',
                acceptedAt: cell.getAttribute('data-wm-time') || ''
            });
        });
    }

    if (typeof document !== 'undefined') {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => enhanceSignatureCells(document));
        } else {
            enhanceSignatureCells(document);
        }
    }

    global.openSignatureModal = openSignatureModal;
    global.clearSignatureCanvas = clearSignatureCanvas;
    global.saveSignatureCanvas = saveSignatureCanvas;
    global.applyTextSignatureFallback = applyTextSignatureFallback;
    global.requireSignatureBeforeSubmit = requireSignatureBeforeSubmit;
    global.CMSignaturePad = {
        TEXT_SIG_PREFIX: TEXT_SIG_PREFIX,
        bakeSignatureWatermark: bakeSignatureWatermark,
        bakeCanvasWatermarkSync: bakeCanvasWatermarkSync,
        rebakeCleanSignatureWatermark: rebakeCleanSignatureWatermark,
        presentSignatureWithWatermark: presentSignatureWithWatermark,
        enhanceSignatureCells: enhanceSignatureCells,
        isCanvasEffectivelyBlank: isCanvasEffectivelyBlank,
        buildWatermarkMeta: buildWatermarkMeta,
        watermarkCycleLabels: watermarkCycleLabels,
        paintWatermarkGrid: paintWatermarkGrid
    };
})(window);

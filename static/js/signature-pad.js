/**
 * 轻量手写签名：配合 fragments :: signature-modal 与隐藏域 name=signature。
 * openSignatureModal(inputEl?) — 可传入目标隐藏域；预览/提示优先取同表单内
 * [data-sig-preview] / [data-sig-hint]，否则回退 #signaturePreview / #signatureHint。
 */
(function (global) {
    'use strict';

    let signatureModal = null;
    let sigCtx = null;
    let drawing = false;
    let targetInput = null;

    function resolvePreviewAndHint() {
        const form = targetInput && targetInput.closest ? targetInput.closest('form') : null;
        const preview = (form && form.querySelector('[data-sig-preview]'))
            || document.getElementById('signaturePreview');
        const hint = (form && form.querySelector('[data-sig-hint]'))
            || document.getElementById('signatureHint');
        return { preview: preview, hint: hint };
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
        sigCtx = canvas.getContext('2d');
        sigCtx.setTransform(ratio, 0, 0, ratio, 0, 0);
        sigCtx.lineWidth = 2;
        sigCtx.lineCap = 'round';
        sigCtx.strokeStyle = '#111';
        sigCtx.fillStyle = '#fff';
        sigCtx.fillRect(0, 0, displayW, displayH);

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
            e.preventDefault();
        };
        const end = () => { drawing = false; };
        canvas.onmousedown = start;
        canvas.onmousemove = move;
        canvas.onmouseup = end;
        canvas.onmouseleave = end;
        canvas.ontouchstart = start;
        canvas.ontouchmove = move;
        canvas.ontouchend = end;
    }

    function clearSignatureCanvas() {
        const canvas = document.getElementById('signatureCanvas');
        if (!canvas || !sigCtx) return;
        sigCtx.fillStyle = '#fff';
        sigCtx.fillRect(0, 0, canvas.clientWidth || 420, 180);
    }

    function saveSignatureCanvas() {
        const canvas = document.getElementById('signatureCanvas');
        if (!canvas) return;
        const data = canvas.toDataURL('image/png');
        if (targetInput) {
            targetInput.value = data;
        }
        const ui = resolvePreviewAndHint();
        if (ui.preview) {
            ui.preview.src = data;
            ui.preview.classList.remove('d-none');
        }
        if (ui.hint) {
            ui.hint.textContent = '已采集手写签名';
            ui.hint.classList.remove('text-danger');
            ui.hint.classList.add('text-success');
        }
        if (signatureModal) {
            signatureModal.hide();
        }
    }

    function requireSignatureBeforeSubmit(form) {
        if (!form) return true;
        const input = form.querySelector('input[name="signature"]');
        const sig = input ? String(input.value || '').trim() : '';
        if (!sig) {
            alert('请先完成电子签名');
            return false;
        }
        return true;
    }

    global.openSignatureModal = openSignatureModal;
    global.clearSignatureCanvas = clearSignatureCanvas;
    global.saveSignatureCanvas = saveSignatureCanvas;
    global.requireSignatureBeforeSubmit = requireSignatureBeforeSubmit;
})(window);

/**
 * 赛事详情页「单场比赛录分」弹窗与签名画布 — 与 {@code fragments-group-match-score-modal.html} 配套。
 * 依赖：Bootstrap 5、{@code /js/main.js} 中的 {@code copyPlainTextToClipboard}。
 */
(function () {
    'use strict';

    let groupMatchModal;
    let signatureModal;
    /** 与「取消资格」等共用同一签名画布时的 PNG data URL */
    if (typeof window.__cmSharedSignatureData !== 'string') {
        window.__cmSharedSignatureData = '';
    }
    let currentMatchPhaseCode = '';
    let drawing = false;
    let sigCtx = null;
    let _groupMatchScoreSubmitting = false;
    /** 签名按钮文案/颜色上下文（随打开场次刷新） */
    let _sigBtnContext = {
        matchLocked: false,
        selfAccepted: false
    };

    function getCsrf() {
        const input = document.querySelector('input[name="_csrf"]');
        if (!input) return null;
        return { name: input.name, value: input.value };
    }

    function getCurrentUserIdStr() {
        const meta = document.getElementById('groupMatchScoreModalMeta');
        return meta && meta.dataset && meta.dataset.currentUserId != null
            ? String(meta.dataset.currentUserId).trim()
            : '';
    }

    function getCurrentUsernameStr() {
        const meta = document.getElementById('groupMatchScoreModalMeta');
        return meta && meta.dataset && meta.dataset.currentUsername != null
            ? String(meta.dataset.currentUsername).trim()
            : '';
    }

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /** 与后端 calcNextHammerSide 一致：空白局也交换后手 */
    function calcNextHammerSide(currentHammerSide, player1Score, player2Score) {
        if (currentHammerSide !== 1 && currentHammerSide !== 2) return null;
        if (player1Score === 0 && player2Score === 0) {
            return currentHammerSide === 1 ? 2 : 1;
        }
        if (player1Score > player2Score) return 2;
        if (player2Score > player1Score) return 1;
        return currentHammerSide === 1 ? 2 : 1;
    }

    function parseScorePoints(token) {
        const s = String(token == null ? '' : token).trim().toUpperCase();
        if (s === '' || s === 'X') return 0;
        const n = Number(s);
        return Number.isFinite(n) ? n : 0;
    }

    /**
     * 按开局后手 + 各局比分推算每局后手方（1/2/null）。
     * 规则对齐后端：遇 X 后该局及之后比分按 0，且 X 之后局不再推进后手。
     */
    function computeHammerSides(firstEndHammer, p1Vals, p2Vals) {
        const raw = String(firstEndHammer == null ? '' : firstEndHammer).trim();
        let side = (raw === '1') ? 1 : (raw === '2' ? 2 : null);
        const n = Math.min(
            Array.isArray(p1Vals) ? p1Vals.length : 0,
            Array.isArray(p2Vals) ? p2Vals.length : 0
        );
        const sides = [];
        let cutoff = -1;
        for (let i = 0; i < n; i++) {
            const v1 = String(p1Vals[i] == null ? '' : p1Vals[i]).trim().toUpperCase();
            const v2 = String(p2Vals[i] == null ? '' : p2Vals[i]).trim().toUpperCase();
            if (cutoff < 0 && (v1 === 'X' || v2 === 'X')) cutoff = i;
        }
        for (let i = 0; i < n; i++) {
            sides.push(side);
            const forceZero = cutoff >= 0 && i >= cutoff;
            const s1 = forceZero ? 0 : parseScorePoints(p1Vals[i]);
            const s2 = forceZero ? 0 : parseScorePoints(p2Vals[i]);
            // 与后端一致：仅在 i > cutoff 时停止推进；X 当局仍推进
            if (cutoff >= 0 && i > cutoff) continue;
            if (side != null) side = calcNextHammerSide(side, s1, s2);
        }
        return sides;
    }

    function findXCutoffIndex(p1Vals, p2Vals) {
        const n = Math.min(
            Array.isArray(p1Vals) ? p1Vals.length : 0,
            Array.isArray(p2Vals) ? p2Vals.length : 0
        );
        for (let i = 0; i < n; i++) {
            const v1 = String(p1Vals[i] == null ? '' : p1Vals[i]).trim().toUpperCase();
            const v2 = String(p2Vals[i] == null ? '' : p2Vals[i]).trim().toUpperCase();
            if (v1 === 'X' || v2 === 'X') return i;
        }
        return -1;
    }

    function shortDisplayName(name) {
        const s = String(name == null ? '' : name).trim();
        if (!s) return '';
        return s.length <= 4 ? s : (s.slice(0, 3) + '…');
    }

    function findScoreEndsHeader(tbody) {
        const table = tbody ? tbody.closest('table') : null;
        return table ? table.querySelector('[data-score-ends-header]') : null;
    }

    function renderScoreEndsHeader(headerEl, n) {
        if (!headerEl) return;
        if (!Number.isInteger(n) || n < 1) {
            headerEl.innerHTML = '';
            return;
        }
        const items = [];
        for (let i = 1; i <= n; i++) {
            items.push(
                `<div class="score-end-item score-end-head-item" data-end-index="${i}">`
                + `<span class="score-end-num">${i}</span>`
                + `<span class="score-end-head-hammer" data-end-hammer-label></span>`
                + `</div>`
            );
        }
        headerEl.innerHTML = items.join('');
    }

    function markEndItem(item, cls, badgeHtml, title) {
        if (!item) return;
        item.classList.add(cls);
        const mark = item.querySelector('.score-end-cell-mark');
        if (mark && badgeHtml && !mark.querySelector('.score-end-mark-badge')) {
            mark.innerHTML = badgeHtml;
        }
        const sel = item.querySelector('select.score-input');
        if (sel && title) sel.title = title;
    }

    /**
     * 局号在表头；表头显示该局后手选手，格子高亮对应后手行。
     * X 当局：双方玫瑰色；X 之后局不再标后手。
     */
    function refreshEndHammerMarks(tbodyId, hammerSelectId, p1Class, p2Class) {
        const tbody = document.getElementById(tbodyId);
        if (!tbody) return;
        const hammer = document.getElementById(hammerSelectId);
        const p1Els = Array.from(tbody.querySelectorAll('.' + p1Class));
        const p2Els = Array.from(tbody.querySelectorAll('.' + p2Class));
        const n = Math.min(p1Els.length, p2Els.length);
        const p1Vals = p1Els.map((el) => el.value || '0');
        const p2Vals = p2Els.map((el) => el.value || '0');
        const xCutoff = findXCutoffIndex(p1Vals, p2Vals);
        const sides = computeHammerSides(hammer ? hammer.value : '', p1Vals, p2Vals);
        const p1Name = tbody.dataset.p1Name || '选手1';
        const p2Name = tbody.dataset.p2Name || '选手2';
        const headerEl = findScoreEndsHeader(tbody);

        tbody.querySelectorAll('.score-end-item').forEach((item) => {
            item.classList.remove('score-end-item--hammer', 'score-end-item--x');
            const mark = item.querySelector('.score-end-cell-mark');
            if (mark) mark.innerHTML = '';
            const sel = item.querySelector('select.score-input');
            if (sel) sel.removeAttribute('title');
        });
        if (headerEl) {
            headerEl.querySelectorAll('.score-end-head-item').forEach((item) => {
                item.classList.remove('score-end-head-item--hammer', 'score-end-head-item--x');
                const lab = item.querySelector('[data-end-hammer-label]');
                if (lab) {
                    lab.textContent = '';
                    lab.removeAttribute('title');
                }
            });
        }

        const endItemAt = (side, endIdx) => {
            const nameTh = tbody.querySelector('.score-player-name--tappable[data-hammer-side="' + side + '"]');
            const row = nameTh ? nameTh.closest('tr') : null;
            if (!row) return null;
            return row.querySelectorAll('.score-end-item')[endIdx] || null;
        };

        const setHeaderHammer = (endIdx, text, title, cls) => {
            if (!headerEl) return;
            const item = headerEl.querySelectorAll('.score-end-head-item')[endIdx];
            if (!item) return;
            if (cls) item.classList.add(cls);
            const lab = item.querySelector('[data-end-hammer-label]');
            if (lab) {
                lab.textContent = text || '';
                if (title) lab.title = title;
            }
        };

        // X 当局：双方玫瑰色；表头标 X
        if (xCutoff >= 0 && xCutoff < n) {
            markEndItem(
                endItemAt(1, xCutoff),
                'score-end-item--x',
                '<span class="score-end-mark-badge score-end-x-badge" title="本局结束（X）">X</span>',
                '本局结束（X）'
            );
            markEndItem(
                endItemAt(2, xCutoff),
                'score-end-item--x',
                '<span class="score-end-mark-badge score-end-x-badge" title="本局结束（X）">X</span>',
                '本局结束（X）'
            );
            setHeaderHammer(xCutoff, 'X', '本局结束（X）', 'score-end-head-item--x');
        }

        // 后手：表头写后手选手名，对应行格子琥珀色；X 当局及之后不再标后手
        const hammerLimit = xCutoff >= 0 ? xCutoff : n;
        for (let i = 0; i < hammerLimit; i++) {
            const side = sides[i];
            if (side !== 1 && side !== 2) continue;
            const fullName = side === 1 ? p1Name : p2Name;
            markEndItem(
                endItemAt(side, i),
                'score-end-item--hammer',
                '<span class="score-end-mark-badge score-end-hammer-badge" title="本局后手">后</span>',
                '本局后手：' + fullName
            );
            setHeaderHammer(
                i,
                shortDisplayName(fullName),
                '第' + (i + 1) + '局后手：' + fullName + '（另一人先手）',
                'score-end-head-item--hammer'
            );
        }
    }

    function bindEndHammerMarksSync(tbodyId, hammerSelectId, p1Class, p2Class) {
        const tbody = document.getElementById(tbodyId);
        const hammer = document.getElementById(hammerSelectId);
        if (!tbody || !p1Class || !p2Class) return;
        tbody.dataset.endHammerSelectId = hammerSelectId || '';
        tbody.dataset.endHammerP1 = p1Class;
        tbody.dataset.endHammerP2 = p2Class;
        const key = 'endHammerBound';
        if (tbody.dataset[key] !== '1') {
            tbody.addEventListener('change', (e) => {
                const t = e.target;
                if (!t || !t.classList) return;
                if (!t.classList.contains(p1Class) && !t.classList.contains(p2Class)) return;
                refreshEndHammerMarks(tbodyId, hammerSelectId, p1Class, p2Class);
            });
            tbody.dataset[key] = '1';
        }
        if (hammer && hammer.dataset.endHammerSelectBound !== '1') {
            hammer.addEventListener('change', () => {
                refreshEndHammerMarks(tbodyId, hammerSelectId, p1Class, p2Class);
            });
            hammer.dataset.endHammerSelectBound = '1';
        }
        refreshEndHammerMarks(tbodyId, hammerSelectId, p1Class, p2Class);
    }

    function afterGroupMatchSavedSuccess() {
        const cb = window.onGroupMatchScoreSaved;
        if (typeof cb === 'function') {
            try {
                cb();
            } catch (e) {
                console.error(e);
            }
        } else {
            location.reload();
        }
    }

    function scoreTotalText(values) {
        if (!values || values.length === 0) return '0';
        return String(values.reduce((sum, v) => {
            const s = String(v ?? '').trim();
            if (s.toUpperCase() === 'X') return sum;
            const num = Number(s);
            return sum + (Number.isFinite(num) ? num : 0);
        }, 0));
    }

    function scoreTotalPoints(values) {
        const n = Number(scoreTotalText(values));
        return Number.isFinite(n) ? n : 0;
    }

    /** 总分框胜负底色：p1胜淡蓝 / p2胜淡红 / 非0平淡绿 */
    function refreshTotalOutcomeHighlight(tbodyId, p1Class, p2Class, totalP1Class, totalP2Class) {
        const tbody = document.getElementById(tbodyId);
        if (!tbody || !p1Class || !p2Class) return;
        const p1Vals = Array.from(tbody.querySelectorAll('.' + p1Class)).map((i) => i.value || '0');
        const p2Vals = Array.from(tbody.querySelectorAll('.' + p2Class)).map((i) => i.value || '0');
        const n1 = scoreTotalPoints(p1Vals);
        const n2 = scoreTotalPoints(p2Vals);
        const t1 = tbody.querySelector('.' + totalP1Class);
        const t2 = tbody.querySelector('.' + totalP2Class);
        const clear = (el) => {
            if (!el) return;
            el.classList.remove('score-total-box--p1-win', 'score-total-box--p2-win', 'score-total-box--tie');
        };
        clear(t1);
        clear(t2);
        if (n1 > n2) {
            if (t1) t1.classList.add('score-total-box--p1-win');
        } else if (n2 > n1) {
            if (t2) t2.classList.add('score-total-box--p2-win');
        } else if (n1 !== 0 || n2 !== 0) {
            if (t1) t1.classList.add('score-total-box--tie');
            if (t2) t2.classList.add('score-total-box--tie');
        }
    }

    function scoreCellTokenFromRow(row, side) {
        if (!row) return '0';
        const isP1 = side === 1;
        const isX = isP1
            ? (row.player1IsX === true || row.player1_is_x === true || row.player1_is_x === 1)
            : (row.player2IsX === true || row.player2_is_x === true || row.player2_is_x === 1);
        if (isX) return 'X';
        const sc = isP1 ? row.player1Score : row.player2Score;
        return String(sc ?? 0);
    }

    function applyFirstEndHammerSelect(selectId, data) {
        const el = document.getElementById(selectId);
        if (!el) return;
        let raw = null;
        if (data && data.matchFirstEndHammer != null && data.matchFirstEndHammer !== '') {
            raw = data.matchFirstEndHammer;
        } else if (data && data.match) {
            raw = data.match.firstEndHammer != null ? data.match.firstEndHammer : data.match.first_end_hammer;
        }
        if (raw === null || raw === undefined || raw === '') {
            el.value = '';
            return;
        }
        const s = String(raw);
        el.value = (s === '1' || s === '2') ? s : '';
    }

    function buildScoreRow(label, values, n, inputClass, totalClass, hammerSide) {
        const options = ['0', '1', '2', '3', '4', '5', '6', '7', '8', 'X'];
        const items = [];
        for (let i = 1; i <= n; i++) {
            const valStr = String(values[i - 1] ?? '0');
            const opts = options.map(v => `<option value="${v}" ${valStr === v ? 'selected' : ''}>${v}</option>`).join('');
            items.push(
                `<div class="score-end-item" data-end-index="${i}">`
                + `<span class="score-end-cell-mark" aria-hidden="true"></span>`
                + `<select class="form-select score-input ${inputClass}" aria-label="第${i}局比分">${opts}</select>`
                + `</div>`
            );
        }
        return `<tr>
                <th class="score-player-name score-player-name--tappable" data-hammer-side="${hammerSide}" title="双击可设置/取消开局后手"><span class="score-player-name-inner">${label}</span></th>
                <td class="score-total-cell"><div class="score-total-cell-inner"><span class="score-total-box ${totalClass}">${scoreTotalText(values)}</span></div></td>
                <td class="score-ends-cell"><div class="score-ends-grid">${items.join('')}</div></td>
            </tr>`;
    }

    function bindScoreTotal(tbodyId, inputClass, totalClass) {
        const tbody = document.getElementById(tbodyId);
        if (!tbody || tbody.dataset.totalBound === '1') return;
        tbody.addEventListener('change', (e) => {
            if (!e.target.classList.contains(inputClass)) return;
            const values = Array.from(tbody.querySelectorAll(`.${inputClass}`)).map(i => i.value || '0');
            const total = tbody.querySelector(`.${totalClass}`);
            if (total) total.textContent = scoreTotalText(values);
            if (tbody.dataset.outcomeP1 && tbody.dataset.outcomeP2) {
                refreshTotalOutcomeHighlight(
                    tbodyId,
                    tbody.dataset.outcomeP1,
                    tbody.dataset.outcomeP2,
                    tbody.dataset.outcomeT1,
                    tbody.dataset.outcomeT2
                );
            }
        });
        tbody.dataset.totalBound = '1';
    }

    function bindTotalOutcomePair(tbodyId, p1Class, p2Class, totalP1Class, totalP2Class) {
        const tbody = document.getElementById(tbodyId);
        if (!tbody) return;
        tbody.dataset.outcomeP1 = p1Class;
        tbody.dataset.outcomeP2 = p2Class;
        tbody.dataset.outcomeT1 = totalP1Class;
        tbody.dataset.outcomeT2 = totalP2Class;
        refreshTotalOutcomeHighlight(tbodyId, p1Class, p2Class, totalP1Class, totalP2Class);
    }

    function bindXCutoffForceZeros(tbodyId, p1Class, p2Class, totalP1Class, totalP2Class) {
        const tbody = document.getElementById(tbodyId);
        if (!tbody || tbody.dataset.xCutoffBound === '1') return;
        tbody.addEventListener('change', (e) => {
            const p1Changed = e.target && e.target.classList && e.target.classList.contains(p1Class);
            const p2Changed = e.target && e.target.classList && e.target.classList.contains(p2Class);
            if (!p1Changed && !p2Changed) return;

            const p1Els = Array.from(tbody.querySelectorAll(`.${p1Class}`));
            const p2Els = Array.from(tbody.querySelectorAll(`.${p2Class}`));
            if (!p1Els.length || !p2Els.length) return;

            const n = Math.min(p1Els.length, p2Els.length);
            let cutoff = -1;
            for (let i = 0; i < n; i++) {
                const v1 = String(p1Els[i].value || '').trim().toUpperCase();
                const v2 = String(p2Els[i].value || '').trim().toUpperCase();
                if (v1 === 'X' || v2 === 'X') {
                    cutoff = i;
                    break;
                }
            }

            if (cutoff >= 0) {
                for (let j = cutoff; j < n; j++) {
                    p1Els[j].value = (j === cutoff) ? 'X' : '0';
                    p2Els[j].value = (j === cutoff) ? 'X' : '0';
                }
            }

            const p1Vals = p1Els.map(i => i.value || '0');
            const p2Vals = p2Els.map(i => i.value || '0');
            const t1 = tbody.querySelector(`.${totalP1Class}`);
            if (t1) t1.textContent = scoreTotalText(p1Vals);
            const t2 = tbody.querySelector(`.${totalP2Class}`);
            if (t2) t2.textContent = scoreTotalText(p2Vals);
            refreshTotalOutcomeHighlight(tbodyId, p1Class, p2Class, totalP1Class, totalP2Class);
            // 程序改值不冒泡 change：主动刷新局后手高亮
            if (tbody.dataset.endHammerSelectId) {
                refreshEndHammerMarks(
                    tbodyId,
                    tbody.dataset.endHammerSelectId,
                    tbody.dataset.endHammerP1,
                    tbody.dataset.endHammerP2
                );
            }
        });
        tbody.dataset.xCutoffBound = '1';
    }

    function refreshHammerNameHighlight(tbodyId, hammerSelectId) {
        const tbody = document.getElementById(tbodyId);
        const hammer = document.getElementById(hammerSelectId);
        if (!tbody || !hammer) return;
        const side = String(hammer.value || '');
        tbody.querySelectorAll('.score-player-name--tappable').forEach((el) => {
            el.classList.toggle('score-player-name--hammer', side !== '' && String(el.dataset.hammerSide || '') === side);
        });
    }

    function bindHammerHighlightSync(tbodyId, hammerSelectId) {
        const tbody = document.getElementById(tbodyId);
        const hammer = document.getElementById(hammerSelectId);
        if (!tbody || !hammer || hammer.dataset.hammerHighlightBound === '1') return;
        hammer.addEventListener('change', () => refreshHammerNameHighlight(tbodyId, hammerSelectId));
        hammer.dataset.hammerHighlightBound = '1';
    }

    function bindNameDoubleTapHammerToggle(tbodyId, hammerSelectId) {
        const tbody = document.getElementById(tbodyId);
        const hammer = document.getElementById(hammerSelectId);
        if (!tbody || !hammer || tbody.dataset.nameHammerBound === '1') return;
        const apply = (target) => {
            if (!target || !target.classList || !target.classList.contains('score-player-name--tappable')) return;
            if (hammer.disabled) return;
            const side = String(target.dataset.hammerSide || '').trim();
            if (side !== '1' && side !== '2') return;
            hammer.value = (hammer.value === side) ? '' : side;
            refreshHammerNameHighlight(tbodyId, hammerSelectId);
            hammer.dispatchEvent(new Event('change', { bubbles: true }));
        };
        tbody.addEventListener('dblclick', (e) => {
            apply(e.target.closest('.score-player-name--tappable'));
        });
        let lastTapAt = 0;
        let lastTapEl = null;
        tbody.addEventListener('touchend', (e) => {
            const el = e.target.closest('.score-player-name--tappable');
            if (!el) return;
            const now = Date.now();
            if (lastTapEl === el && (now - lastTapAt) < 350) {
                apply(el);
                lastTapAt = 0;
                lastTapEl = null;
            } else {
                lastTapAt = now;
                lastTapEl = el;
            }
        }, { passive: true });
        tbody.dataset.nameHammerBound = '1';
    }

    function renderGroupScoreRows(existing) {
        const tbody = document.getElementById('groupScoreRows');
        if (!tbody) return;
        const n = Number(document.getElementById('groupSetCount').value || 0);
        const headerEl = findScoreEndsHeader(tbody);
        if (!Number.isInteger(n) || n < 1) {
            tbody.innerHTML = '';
            renderScoreEndsHeader(headerEl, 0);
            return;
        }
        const p1Name = document.getElementById('groupHammerPlayer1Option').textContent || 'player1';
        const p2Name = document.getElementById('groupHammerPlayer2Option').textContent || 'player2';
        const p1Vals = [];
        const p2Vals = [];
        for (let i = 1; i <= n; i++) {
            p1Vals.push(existing && existing[i - 1] ? scoreCellTokenFromRow(existing[i - 1], 1) : '0');
            p2Vals.push(existing && existing[i - 1] ? scoreCellTokenFromRow(existing[i - 1], 2) : '0');
        }
        tbody.dataset.p1Name = p1Name;
        tbody.dataset.p2Name = p2Name;
        renderScoreEndsHeader(headerEl, n);
        tbody.innerHTML = buildScoreRow(p1Name, p1Vals, n, 'group-p1', 'group-total-p1', '1')
            + buildScoreRow(p2Name, p2Vals, n, 'group-p2', 'group-total-p2', '2');
        bindScoreTotal('groupScoreRows', 'group-p1', 'group-total-p1');
        bindScoreTotal('groupScoreRows', 'group-p2', 'group-total-p2');
        bindXCutoffForceZeros('groupScoreRows', 'group-p1', 'group-p2', 'group-total-p1', 'group-total-p2');
        bindTotalOutcomePair('groupScoreRows', 'group-p1', 'group-p2', 'group-total-p1', 'group-total-p2');
        const editable = typeof window._groupMatchModalEditable === 'boolean' ? window._groupMatchModalEditable : true;
        document.querySelectorAll('#groupScoreRows .group-p1, #groupScoreRows .group-p2').forEach(i => {
            i.disabled = !editable;
        });
        bindNameDoubleTapHammerToggle('groupScoreRows', 'groupFirstEndHammer');
        bindHammerHighlightSync('groupScoreRows', 'groupFirstEndHammer');
        refreshHammerNameHighlight('groupScoreRows', 'groupFirstEndHammer');
        bindEndHammerMarksSync('groupScoreRows', 'groupFirstEndHammer', 'group-p1', 'group-p2');
    }

    function applyGroupMatchModalEditMode(data) {
        const canEdit = data.canEditScore === true;
        const canAccept = data.canAcceptScore === true;
        const locked = data.match && (data.match.resultLocked === true || data.match.resultLocked === 1);
        const viewerIsSuperAdmin = data.viewerIsSuperAdmin === true;
        const editable = canEdit && (!locked || viewerIsSuperAdmin);
        window._groupMatchModalEditable = editable;
        const banner = document.getElementById('groupMatchReadOnlyHint');
        if (banner) {
            banner.classList.toggle('d-none', editable || canAccept);
        }
        const saveBtn = document.getElementById('groupSaveScoreBtn');
        const acceptBtn = document.getElementById('groupAcceptScoreBtn');
        const renderBtn = document.getElementById('groupRenderScoreRowsBtn');
        if (saveBtn) saveBtn.style.display = editable ? '' : 'none';
        if (acceptBtn) acceptBtn.style.display = (canAccept && !locked) ? '' : 'none';
        if (renderBtn) renderBtn.disabled = !editable;
        const hammer = document.getElementById('groupFirstEndHammer');
        const setCount = document.getElementById('groupSetCount');
        if (hammer) hammer.disabled = !editable;
        if (setCount) setCount.disabled = !editable;
        const sigOpen = document.getElementById('groupSignatureOpenBtn');
        if (sigOpen) {
            // 状态按钮始终可见；仅在可编辑/可验收时允许打开签名板
            const allowSignPad = editable || canAccept;
            sigOpen.classList.remove('d-none');
            sigOpen.disabled = !allowSignPad && !locked;
            // 已锁定时仍可点开查看（只读），或禁用签名板
            if (locked && !allowSignPad) {
                sigOpen.disabled = true;
            }
        }
        document.querySelectorAll('#groupScoreRows .group-p1, #groupScoreRows .group-p2').forEach(i => {
            i.disabled = !editable;
        });
        bindNameDoubleTapHammerToggle('groupScoreRows', 'groupFirstEndHammer');
        bindHammerHighlightSync('groupScoreRows', 'groupFirstEndHammer');
        refreshHammerNameHighlight('groupScoreRows', 'groupFirstEndHammer');
        bindEndHammerMarksSync('groupScoreRows', 'groupFirstEndHammer', 'group-p1', 'group-p2');
    }

    function resetMatchScoreHScroll(scrollId) {
        const el = document.getElementById(scrollId);
        if (!el) return;
        el.scrollLeft = 0;
        // 布局后再钉一次，避免弹窗刚打开时被其它滚动影响
        requestAnimationFrame(() => {
            el.scrollLeft = 0;
        });
    }

    function renderAcceptanceTable(acceptances, match, detailData) {
        const tbody = document.getElementById('groupAcceptanceTbody');
        const statusLine = document.getElementById('groupAcceptanceStatusLine');
        if (!tbody || !statusLine) return;
        tbody.innerHTML = '';
        if (!acceptances || acceptances.length === 0) {
            tbody.innerHTML = '<tr><td colspan="3" class="text-muted text-center">暂无</td></tr>';
            statusLine.textContent = '当前验收状态：暂无验收';
            resetMatchScoreHScroll('groupAcceptanceScroll');
            return;
        }
        const uidStr = String(detailData && detailData.viewerUserId != null && detailData.viewerUserId !== ''
            ? detailData.viewerUserId
            : getCurrentUserIdStr()).trim();
        const uname = getCurrentUsernameStr();
        const selfAccepted = acceptances.some((a) => {
            const aUid = String(a && a.userId != null ? a.userId : '').trim();
            const aName = String(a && a.username != null ? a.username : '').trim();
            return (uidStr && aUid === uidStr) || (uname && aName === uname);
        });
        const otherAccepted = acceptances.some((a) => {
            const aUid = String(a && a.userId != null ? a.userId : '').trim();
            const aName = String(a && a.username != null ? a.username : '').trim();
            const isSelf = (uidStr && aUid === uidStr) || (uname && aName === uname);
            return !isSelf;
        });
        if (uidStr || uname) {
            if (selfAccepted) {
                statusLine.textContent = otherAccepted ? '你已验收；对方已验收。' : '你已验收；对方未验收。';
            } else {
                statusLine.textContent = otherAccepted ? '你未验收；对方已验收。' : '你未验收；对方未验收。';
            }
        } else {
            statusLine.textContent = '当前验收状态：查看全部验收记录';
        }
        for (const a of acceptances) {
            const tr = document.createElement('tr');
            const tdUser = document.createElement('td');
            tdUser.textContent = a.username || '未知';
            const tdSig = document.createElement('td');
            tdSig.className = 'align-middle';
            const rawSig = a.signature != null ? String(a.signature).trim() : '';
            if (!rawSig) {
                const s = document.createElement('span');
                s.className = 'text-muted';
                s.textContent = '(无签名)';
                tdSig.appendChild(s);
            } else if (rawSig.startsWith('data:image/')) {
                const img = document.createElement('img');
                img.src = rawSig;
                img.alt = '电子签名';
                img.className = 'match-acceptance-signature-img';
                img.loading = 'lazy';
                tdSig.appendChild(img);
            } else {
                const span = document.createElement('span');
                span.className = 'text-break small';
                span.textContent = rawSig;
                tdSig.appendChild(span);
            }
            const tdTime = document.createElement('td');
            tdTime.textContent = (a.acceptedAt || '').replace('T', ' ');
            tr.appendChild(tdUser);
            tr.appendChild(tdSig);
            tr.appendChild(tdTime);
            tbody.appendChild(tr);
        }
        resetMatchScoreHScroll('groupAcceptanceScroll');
    }

    function renderEditLogTable(editLogs) {
        const tbody = document.getElementById('groupEditLogTbody');
        if (!tbody) return;
        tbody.innerHTML = '';
        if (!editLogs || editLogs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-muted text-center">暂无</td></tr>';
            resetMatchScoreHScroll('groupEditLogScroll');
            return;
        }
        for (const l of editLogs) {
            const oldScore = `${l.oldPlayer1IsX ? 'X' : (l.oldPlayer1Score ?? 0)} : ${l.oldPlayer2IsX ? 'X' : (l.oldPlayer2Score ?? 0)}`;
            const newScore = `${l.newPlayer1IsX ? 'X' : (l.newPlayer1Score ?? 0)} : ${l.newPlayer2IsX ? 'X' : (l.newPlayer2Score ?? 0)}`;
            const tr = document.createElement('tr');
            tr.innerHTML = `
                    <td>${l.setNumber != null ? l.setNumber : ''}</td>
                    <td>${l.editorUsername || '未知'}</td>
                    <td>${oldScore}</td>
                    <td><span class="badge bg-light text-dark border">${newScore}</span></td>
                    <td>${(l.editedAt || '').replace('T', ' ')}</td>
                `;
            tbody.appendChild(tr);
        }
        resetMatchScoreHScroll('groupEditLogScroll');
    }

    async function openGroupMatchModal(el) {
        if (!el || !el.dataset) return;
        const matchId = el.dataset.matchId;
        if (!matchId) return;
        const player1Name = el.dataset.player1Name || 'player1';
        const player2Name = el.dataset.player2Name || 'player2';
        if (!groupMatchModal) {
            groupMatchModal = new bootstrap.Modal(document.getElementById('groupMatchScoreModal'));
        }
        const res = await fetch(`/tournament/competition/match/detail/${encodeURIComponent(matchId)}`);
        const data = await res.json();
        if (!data.ok) {
            alert(data.message || '加载失败');
            return;
        }
        document.getElementById('groupMatchIdInput').value = String(matchId);
        document.getElementById('groupMatchTitle').textContent = (data.match.category || '') + ' / round ' + (data.match.round || '-');
        currentMatchPhaseCode = (data.match.phaseCode || '').toUpperCase();
        document.getElementById('groupHammerPlayer1Option').textContent = player1Name;
        document.getElementById('groupHammerPlayer2Option').textContent = player2Name;
        applyFirstEndHammerSelect('groupFirstEndHammer', data);
        const setScores = Array.isArray(data.setScores) ? data.setScores : [];
        const defSets = (data.defaultSetCount != null && Number(data.defaultSetCount) > 0)
            ? Number(data.defaultSetCount)
            : 8;
        document.getElementById('groupSetCount').value = setScores.length ? setScores.length : defSets;
        applyGroupMatchModalEditMode(data);
        renderGroupScoreRows(setScores);
        renderAcceptanceTable(data.acceptances || [], data.match, data);
        renderEditLogTable(data.editLogs || []);
        // 在按钮显示逻辑之后再还原签名色（优先 data.viewerSignature）
        restoreCurrentUserSignatureFromAcceptances(data);
        groupMatchModal.show();
    }

    function collectGroupMatchScorePayload() {
        const hammerEl = document.getElementById('groupFirstEndHammer');
        const hammer = hammerEl ? String(hammerEl.value || '').trim() : '';
        const tbody = document.getElementById('groupScoreRows');
        const p1 = tbody ? Array.from(tbody.querySelectorAll('.group-p1')).map(i => i.value || '0') : [];
        const p2 = tbody ? Array.from(tbody.querySelectorAll('.group-p2')).map(i => i.value || '0') : [];
        return { hammer, p1, p2 };
    }

    function appendGroupScoreFormFields(form, payload) {
        if (payload.hammer) form.append('firstEndHammer', payload.hammer);
        payload.p1.forEach(v => form.append('player1Scores', v));
        payload.p2.forEach(v => form.append('player2Scores', v));
    }

    async function saveGroupMatchScore() {
        if (_groupMatchScoreSubmitting) return;
        const matchId = document.getElementById('groupMatchIdInput').value;
        const { hammer, p1, p2 } = collectGroupMatchScorePayload();
        const hasX = [...p1, ...p2].some(v => String(v).toUpperCase() === 'X');
        const needSignature = currentMatchPhaseCode === 'QUALIFIER' || currentMatchPhaseCode === 'MAIN' || currentMatchPhaseCode === 'FINAL';
        const form = new URLSearchParams();
        appendGroupScoreFormFields(form, { hammer, p1, p2 });
        if (hasX) {
            const ok = confirm('检测到X。是否验收？验收后不可再修改比分。');
            if (!ok) return;
            if (needSignature && !window.__cmSharedSignatureData) {
                alert('该阶段验收需手写签名，请先点击“手写电子签名”');
                return;
            }
            form.append('autoAccept', 'true');
            if (window.__cmSharedSignatureData) form.append('signature', window.__cmSharedSignatureData);
        }
        const csrf = getCsrf();
        if (csrf) form.append(csrf.name, csrf.value);
        _groupMatchScoreSubmitting = true;
        const saveBtn = document.getElementById('groupSaveScoreBtn');
        const acceptBtn = document.getElementById('groupAcceptScoreBtn');
        if (saveBtn) saveBtn.disabled = true;
        if (acceptBtn) acceptBtn.disabled = true;
        try {
            const res = await fetch(`/tournament/competition/match/save/${matchId}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: form.toString()
            });
            const data = await res.json();
            if (!data.ok) {
                alert(data.message || '保存失败');
                return;
            }
            afterGroupMatchSavedSuccess();
        } finally {
            _groupMatchScoreSubmitting = false;
            if (saveBtn) saveBtn.disabled = false;
            if (acceptBtn) acceptBtn.disabled = false;
        }
    }

    async function acceptGroupMatchScore() {
        if (_groupMatchScoreSubmitting) return;
        const matchId = document.getElementById('groupMatchIdInput').value;
        const needSignature = currentMatchPhaseCode === 'QUALIFIER' || currentMatchPhaseCode === 'MAIN' || currentMatchPhaseCode === 'FINAL';
        const { hammer, p1, p2 } = collectGroupMatchScorePayload();
        const hasX = [...p1, ...p2].some(v => String(v).toUpperCase() === 'X');
        if (hasX) {
            const ok = confirm('检测到X。验收将锁定比分且不可再改，是否继续？');
            if (!ok) return;
        }
        if (needSignature && !window.__cmSharedSignatureData) {
            alert('该阶段验收需手写签名，请先点击“手写电子签名”');
            return;
        }
        const form = new URLSearchParams();
        appendGroupScoreFormFields(form, { hammer, p1, p2 });
        if (window.__cmSharedSignatureData) form.append('signature', window.__cmSharedSignatureData);
        const csrf = getCsrf();
        if (csrf) form.append(csrf.name, csrf.value);
        _groupMatchScoreSubmitting = true;
        const saveBtn = document.getElementById('groupSaveScoreBtn');
        const acceptBtn = document.getElementById('groupAcceptScoreBtn');
        if (saveBtn) saveBtn.disabled = true;
        if (acceptBtn) acceptBtn.disabled = true;
        try {
            const res = await fetch(`/tournament/competition/match/save-and-accept/${matchId}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: form.toString()
            });
            const data = await res.json();
            if (!data.ok) {
                alert(data.message || '验收失败');
                return;
            }
            afterGroupMatchSavedSuccess();
        } finally {
            _groupMatchScoreSubmitting = false;
            if (saveBtn) saveBtn.disabled = false;
            if (acceptBtn) acceptBtn.disabled = false;
        }
    }

    function openSignatureModal() {
        if (!signatureModal) signatureModal = new bootstrap.Modal(document.getElementById('signatureModal'));
        signatureModal.show();
        initSignatureCanvas();
    }

    function initSignatureCanvas() {
        const canvas = document.getElementById('signatureCanvas');
        if (!canvas) return;
        sigCtx = canvas.getContext('2d');
        sigCtx.lineWidth = 2;
        sigCtx.lineCap = 'round';
        sigCtx.strokeStyle = '#111';
        const start = (x, y) => {
            drawing = true;
            sigCtx.beginPath();
            sigCtx.moveTo(x, y);
        };
        const move = (x, y) => {
            if (!drawing) return;
            sigCtx.lineTo(x, y);
            sigCtx.stroke();
        };
        const end = () => {
            drawing = false;
        };
        canvas.onmousedown = (e) => start(e.offsetX, e.offsetY);
        canvas.onmousemove = (e) => move(e.offsetX, e.offsetY);
        canvas.onmouseup = end;
        canvas.onmouseleave = end;
        canvas.ontouchstart = (e) => {
            const r = canvas.getBoundingClientRect();
            const t = e.touches[0];
            start(t.clientX - r.left, t.clientY - r.top);
            e.preventDefault();
        };
        canvas.ontouchmove = (e) => {
            const r = canvas.getBoundingClientRect();
            const t = e.touches[0];
            move(t.clientX - r.left, t.clientY - r.top);
            e.preventDefault();
        };
        canvas.ontouchend = end;
    }

    function clearSignatureCanvas() {
        const canvas = document.getElementById('signatureCanvas');
        if (!canvas || !sigCtx) return;
        sigCtx.clearRect(0, 0, canvas.width, canvas.height);
    }

    function isHandwrittenSignatureData(sig) {
        const s = String(sig == null ? '' : sig).trim();
        if (!s) return false;
        if (s.startsWith('data:image/')) return true;
        // 兼容历史：仅存 base64、无 data URL 头
        return s.length > 80 && !s.includes(' ') && !s.includes('SYSTEM_') && !s.includes('SUPER_ADMIN')
            && /^[A-Za-z0-9+/=]+$/.test(s);
    }

    function normalizeHandwrittenSignatureData(sig) {
        const s = String(sig == null ? '' : sig).trim();
        if (!s) return '';
        if (s.startsWith('data:image/')) return s;
        if (isHandwrittenSignatureData(s)) return 'data:image/png;base64,' + s;
        return '';
    }

    function isSameViewerAcceptance(a, detailData) {
        if (!a) return false;
        const uid = String(detailData && detailData.viewerUserId != null && detailData.viewerUserId !== ''
            ? detailData.viewerUserId
            : getCurrentUserIdStr()).trim();
        const uname = getCurrentUsernameStr();
        const aUid = String(a.userId != null ? a.userId : '').trim();
        const aName = String(a.username != null ? a.username : '').trim();
        return (uid && aUid && aUid === uid) || (uname && aName && aName === uname);
    }

    function refreshSignatureButtonState() {
        const btn = document.getElementById('groupSignatureOpenBtn');
        const locked = !!(_sigBtnContext && _sigBtnContext.matchLocked);
        const selfAccepted = !!(_sigBtnContext && _sigBtnContext.selfAccepted);
        const selfSigned = isHandwrittenSignatureData(window.__cmSharedSignatureData);
        let label;
        let okVisual; // 深绿
        if (locked) {
            // 场次已验收（锁定）
            label = selfAccepted ? '已签名并验收' : '本场次已验收';
            okVisual = true;
        } else if (selfAccepted || selfSigned) {
            // 未锁死：本人已签字，或小组赛无需签字已验收
            label = '本人已验收';
            okVisual = true;
        } else {
            label = '请签名验收';
            okVisual = false;
        }
        if (btn) {
            btn.textContent = label;
            btn.classList.toggle('group-sig-btn--unsigned', !okVisual);
            btn.classList.toggle('group-sig-btn--signed', okVisual);
            btn.setAttribute('aria-pressed', okVisual ? 'true' : 'false');
            btn.title = label;
        }
        const dqStatus = document.getElementById('dqSignatureStatus');
        if (dqStatus) {
            dqStatus.textContent = selfSigned ? '已签名' : '未签名';
        }
    }

    /**
     * 还原当前登录选手手写签名，并刷新验收按钮状态。
     * 优先用后端 viewerSignature（服务端 Long 精确匹配）；勿依赖 JSON number 雪花 userId。
     */
    function restoreCurrentUserSignatureFromAcceptances(data) {
        window.__cmSharedSignatureData = '';
        const payload = data && typeof data === 'object' ? data : {};
        const match = payload.match || {};
        const acceptances = Array.isArray(payload.acceptances) ? payload.acceptances : [];
        const selfAcc = acceptances.find((a) => isSameViewerAcceptance(a, payload)) || null;
        _sigBtnContext = {
            matchLocked: match.resultLocked === true || match.resultLocked === 1,
            selfAccepted: !!selfAcc
        };

        const direct = normalizeHandwrittenSignatureData(payload.viewerSignature);
        if (direct) {
            window.__cmSharedSignatureData = direct;
            refreshSignatureButtonState();
            return;
        }
        if (selfAcc) {
            const normalized = normalizeHandwrittenSignatureData(selfAcc.signature);
            if (normalized) window.__cmSharedSignatureData = normalized;
        }
        refreshSignatureButtonState();
    }

    function saveSignatureCanvas() {
        const canvas = document.getElementById('signatureCanvas');
        if (!canvas) return;
        window.__cmSharedSignatureData = canvas.toDataURL('image/png');
        refreshSignatureButtonState();
        if (signatureModal) signatureModal.hide();
    }

    function formatMatchDetailsPlain(data) {
        const title = data && data.title ? data.title : '战绩明细';
        const matchDetails = (data && data.matchDetails) ? data.matchDetails : [];
        const out = [title];
        if (matchDetails.length > 0) {
            out.push('');
            out.push('===== 比赛明细 =====');
            for (const m of matchDetails) {
                out.push(`${m.category || ''}（${m.phaseCode || ''}） ${m.player1Name} vs ${m.player2Name} 总比分：${m.totalText || '-'}`);
                const sets = m.sets || [];
                for (const s of sets) {
                    out.push(`  第${s.setNumber}局 [后手:${s.hammer}] ${s.player1ScoreText}:${s.player2ScoreText}`);
                }
                const logs = m.editLogs || [];
                out.push('  修改记录：');
                if (logs.length === 0) {
                    out.push('    无');
                } else {
                    for (const l of logs) {
                        out.push(`    第${l.setNumber}局 ${l.editorUsername} ${l.oldScore} -> ${l.newScore} @ ${(l.editedAt || '').replace('T', ' ')}`);
                    }
                }
                const acc = m.acceptances || [];
                const sigForPlain = (sig) => {
                    const s = sig != null ? String(sig).trim() : '';
                    if (!s) return '无签名';
                    if (s.startsWith('data:image/')) return '[手写签名]';
                    return s.length > 60 ? s.slice(0, 60) + '…' : s;
                };
                const accLine = acc.length === 0
                    ? '无'
                    : acc.map(a => `${a.username}（${sigForPlain(a.signature)}）@${(a.acceptedAt || '').replace('T', ' ')}`).join('；');
                out.push(`  验收信息：${accLine}`);
                out.push('');
            }
        }
        return out.join('\n');
    }

    document.addEventListener('DOMContentLoaded', () => {
        const groupModalEl = document.getElementById('groupMatchScoreModal');
        if (groupModalEl) {
            groupModalEl.addEventListener('hidden.bs.modal', () => {
                const active = document.activeElement;
                if (active && groupModalEl.contains(active) && typeof active.blur === 'function') {
                    active.blur();
                }
            });
        }

        const groupMatchCopyBtn = document.getElementById('groupMatchCopyBtn');
        const groupMatchExportPdfBtn = document.getElementById('groupMatchExportPdfBtn');
        if (groupMatchCopyBtn) {
            groupMatchCopyBtn.addEventListener('click', async () => {
                const matchId = document.getElementById('groupMatchIdInput')?.value;
                if (!matchId) return;
                try {
                    const res = await fetch(`/ranking/api/match/${encodeURIComponent(matchId)}/performance`);
                    if (!res.ok) throw new Error('请求失败：' + res.status);
                    const pdata = await res.json();
                    await copyPlainTextToClipboard(formatMatchDetailsPlain(pdata));
                    if (typeof showAlert === 'function') showAlert('复制成功', 'success');
                    else alert('复制成功');
                } catch (e) {
                    if (typeof showAlert === 'function') showAlert(e.message || '复制失败', 'danger');
                    else alert(e.message || '复制失败');
                }
            });
        }
        if (groupMatchExportPdfBtn) {
            groupMatchExportPdfBtn.addEventListener('click', () => {
                const matchId = document.getElementById('groupMatchIdInput')?.value;
                if (!matchId) return;
                window.open(`/ranking/export/pdf/match/${encodeURIComponent(matchId)}/performance`);
            });
        }
    });

    window.openGroupMatchModal = openGroupMatchModal;
    window.openGroupMatchScoreModalForMatch = function (matchId, player1Name, player2Name) {
        const proxy = {
            dataset: {
                matchId: String(matchId),
                player1Name: player1Name || 'player1',
                player2Name: player2Name || 'player2'
            }
        };
        return openGroupMatchModal(proxy);
    };
    window.renderGroupScoreRows = renderGroupScoreRows;
    window.saveGroupMatchScore = saveGroupMatchScore;
    window.acceptGroupMatchScore = acceptGroupMatchScore;
    window.openSignatureModal = openSignatureModal;
    window.clearSignatureCanvas = clearSignatureCanvas;
    window.saveSignatureCanvas = saveSignatureCanvas;
    window.refreshSignatureButtonState = refreshSignatureButtonState;
    window.applyFirstEndHammerSelect = applyFirstEndHammerSelect;
    window.scoreCellTokenFromRow = scoreCellTokenFromRow;
    window.buildScoreRow = buildScoreRow;
    window.bindScoreTotal = bindScoreTotal;
    window.bindTotalOutcomePair = bindTotalOutcomePair;
    window.refreshTotalOutcomeHighlight = refreshTotalOutcomeHighlight;
    window.bindXCutoffForceZeros = bindXCutoffForceZeros;
    window.refreshHammerNameHighlight = refreshHammerNameHighlight;
    window.bindHammerHighlightSync = bindHammerHighlightSync;
    window.bindNameDoubleTapHammerToggle = bindNameDoubleTapHammerToggle;
    window.refreshEndHammerMarks = refreshEndHammerMarks;
    window.bindEndHammerMarksSync = bindEndHammerMarksSync;
    window.scoreTotalText = scoreTotalText;
    window.getCsrf = getCsrf;
})();

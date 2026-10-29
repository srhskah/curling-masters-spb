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
            items.push(`<div class="score-end-item"><span class="score-end-label">第${i}局</span><select class="form-select form-select-sm score-input ${inputClass}">${opts}</select></div>`);
        }
        return `<tr>
                <th class="score-player-name score-player-name--tappable" data-hammer-side="${hammerSide}" title="双击可设置/取消开局后手">${label}</th>
                <td class="score-total-cell"><span class="score-total-box ${totalClass}">${scoreTotalText(values)}</span></td>
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
        });
        tbody.dataset.totalBound = '1';
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
        if (!Number.isInteger(n) || n < 1) {
            tbody.innerHTML = '';
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
        tbody.innerHTML = buildScoreRow(p1Name, p1Vals, n, 'group-p1', 'group-total-p1', '1')
            + buildScoreRow(p2Name, p2Vals, n, 'group-p2', 'group-total-p2', '2');
        bindScoreTotal('groupScoreRows', 'group-p1', 'group-total-p1');
        bindScoreTotal('groupScoreRows', 'group-p2', 'group-total-p2');
        bindXCutoffForceZeros('groupScoreRows', 'group-p1', 'group-p2', 'group-total-p1', 'group-total-p2');
        const editable = typeof window._groupMatchModalEditable === 'boolean' ? window._groupMatchModalEditable : true;
        document.querySelectorAll('#groupScoreRows .group-p1, #groupScoreRows .group-p2').forEach(i => {
            i.disabled = !editable;
        });
        bindNameDoubleTapHammerToggle('groupScoreRows', 'groupFirstEndHammer');
        bindHammerHighlightSync('groupScoreRows', 'groupFirstEndHammer');
        refreshHammerNameHighlight('groupScoreRows', 'groupFirstEndHammer');
        bindHammerHighlightSync('groupScoreRows', 'groupFirstEndHammer');
        refreshHammerNameHighlight('groupScoreRows', 'groupFirstEndHammer');
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
        const sigRow = document.getElementById('groupSignatureRow');
        if (sigRow) {
            sigRow.classList.toggle('d-none', !editable && !canAccept);
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
        if (sigOpen) sigOpen.disabled = !editable && !canAccept;
        document.querySelectorAll('#groupScoreRows .group-p1, #groupScoreRows .group-p2').forEach(i => {
            i.disabled = !editable;
        });
        bindNameDoubleTapHammerToggle('groupScoreRows', 'groupFirstEndHammer');
        bindHammerHighlightSync('groupScoreRows', 'groupFirstEndHammer');
        refreshHammerNameHighlight('groupScoreRows', 'groupFirstEndHammer');
        bindHammerHighlightSync('groupScoreRows', 'groupFirstEndHammer');
        refreshHammerNameHighlight('groupScoreRows', 'groupFirstEndHammer');
    }

    function renderAcceptanceTable(acceptances, match) {
        const tbody = document.getElementById('groupAcceptanceTbody');
        const statusLine = document.getElementById('groupAcceptanceStatusLine');
        if (!tbody || !statusLine) return;
        tbody.innerHTML = '';
        if (!acceptances || acceptances.length === 0) {
            tbody.innerHTML = '<tr><td colspan="3" class="text-muted text-center">暂无</td></tr>';
            statusLine.textContent = '当前验收状态：暂无验收';
            return;
        }
        const acceptedIds = new Set(acceptances.map(a => String(a.userId ?? '')));
        const p1 = String(match.player1Id ?? '');
        const p2 = String(match.player2Id ?? '');
        const uidStr = getCurrentUserIdStr();
        const selfIsP1 = uidStr !== '' && uidStr === p1;
        const selfIsP2 = uidStr !== '' && uidStr === p2;
        if (selfIsP1 || selfIsP2) {
            const selfAccepted = acceptedIds.has(uidStr);
            const opponentId = selfIsP1 ? p2 : p1;
            const opponentAccepted = acceptedIds.has(opponentId);
            if (selfAccepted) {
                statusLine.textContent = opponentAccepted ? '你已验收；对方已验收。' : '你已验收；对方未验收。';
            } else {
                statusLine.textContent = opponentAccepted ? '你未验收；对方已验收。' : '你未验收；对方未验收。';
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
    }

    function renderEditLogTable(editLogs) {
        const tbody = document.getElementById('groupEditLogTbody');
        if (!tbody) return;
        tbody.innerHTML = '';
        if (!editLogs || editLogs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-muted text-center">暂无</td></tr>';
            return;
        }
        for (const l of editLogs) {
            const oldScore = `${l.oldPlayer1IsX ? 'X' : (l.oldPlayer1Score ?? 0)} : ${l.oldPlayer2IsX ? 'X' : (l.oldPlayer2Score ?? 0)}`;
            const newScore = `${l.newPlayer1IsX ? 'X' : (l.newPlayer1Score ?? 0)} : ${l.newPlayer2IsX ? 'X' : (l.newPlayer2Score ?? 0)}`;
            const tr = document.createElement('tr');
            tr.innerHTML = `
                    <td>第${l.setNumber}局</td>
                    <td>${l.editorUsername || '未知'}</td>
                    <td>${oldScore}</td>
                    <td><span class="badge bg-light text-dark border">${newScore}</span></td>
                    <td>${(l.editedAt || '').replace('T', ' ')}</td>
                `;
            tbody.appendChild(tr);
        }
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
        window.__cmSharedSignatureData = '';
        document.getElementById('groupSignatureStatus').textContent = '未签名';
        document.getElementById('groupHammerPlayer1Option').textContent = player1Name;
        document.getElementById('groupHammerPlayer2Option').textContent = player2Name;
        applyFirstEndHammerSelect('groupFirstEndHammer', data);
        const setScores = Array.isArray(data.setScores) ? data.setScores : [];
        document.getElementById('groupHammerTimeline').textContent = setScores.length > 0
            ? ('全场先后手：' + setScores.map(s => `第${s.setNumber}局-${s.hammerPlayerId === data.match.player1Id ? player1Name : (s.hammerPlayerId === data.match.player2Id ? player2Name : '未设置')}`).join('；'))
            : '全场先后手：未录入';
        const defSets = (data.defaultSetCount != null && Number(data.defaultSetCount) > 0)
            ? Number(data.defaultSetCount)
            : 8;
        document.getElementById('groupSetCount').value = setScores.length ? setScores.length : defSets;
        applyGroupMatchModalEditMode(data);
        renderGroupScoreRows(setScores);
        renderAcceptanceTable(data.acceptances || [], data.match);
        renderEditLogTable(data.editLogs || []);
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

    function saveSignatureCanvas() {
        const canvas = document.getElementById('signatureCanvas');
        if (!canvas) return;
        window.__cmSharedSignatureData = canvas.toDataURL('image/png');
        document.getElementById('groupSignatureStatus').textContent = window.__cmSharedSignatureData ? '已签名' : '未签名';
        const dqStatus = document.getElementById('dqSignatureStatus');
        if (dqStatus) dqStatus.textContent = window.__cmSharedSignatureData ? '已签名' : '未签名';
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
    window.applyFirstEndHammerSelect = applyFirstEndHammerSelect;
    window.scoreCellTokenFromRow = scoreCellTokenFromRow;
    window.buildScoreRow = buildScoreRow;
    window.bindScoreTotal = bindScoreTotal;
    window.bindXCutoffForceZeros = bindXCutoffForceZeros;
    window.refreshHammerNameHighlight = refreshHammerNameHighlight;
    window.bindHammerHighlightSync = bindHammerHighlightSync;
    window.bindNameDoubleTapHammerToggle = bindNameDoubleTapHammerToggle;
    window.scoreTotalText = scoreTotalText;
    window.getCsrf = getCsrf;
})();

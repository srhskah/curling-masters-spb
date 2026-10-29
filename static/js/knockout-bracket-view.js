/**
 * 淘汰赛对阵图（赛事详情 / 对阵图页）— 布局与交互参考 bracket_demo.html
 */
(function (global) {
  'use strict';

  const ROW_OF = [2, 3, 5, 6, 2, 3, 5, 6];
  const MOBILE_BP = 767.98;
  const dataCache = new Map();

  function useMobileBracketLayout() {
    return window.matchMedia(`(max-width: ${MOBILE_BP}px)`).matches;
  }

  function escapeHtml(s) {
    return (s || '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  function fromSlot(s) {
    if (!s || s.id == null) {
      return { id: null, p1: null, p2: null, n1: '待定', n2: '待定', sc1: null, sc2: null, w: null };
    }
    return {
      id: s.id,
      p1: s.player1Id,
      p2: s.player2Id,
      n1: s.player1Name || '待定',
      n2: s.player2Name || '待定',
      sc1: s.player1Score,
      sc2: s.player2Score,
      w: s.winnerId,
    };
  }

  function listFromPayload(arr, size) {
    const out = [];
    const src = Array.isArray(arr) ? arr : [];
    for (let i = 0; i < size; i++) {
      out.push(fromSlot(src[i]));
    }
    return out;
  }

  function labelsForStart(sr) {
    if (sr === 16 || sr === 8) {
      return { r16: '1/8 决赛', qf: '1/4 决赛', sf: '半决赛', third: '三四名决赛', fin: '决赛' };
    }
    if (sr === 4) {
      return { r16: '1/4 决赛', qf: '半决赛', sf: '半决赛', third: '三四名决赛', fin: '决赛' };
    }
    return { r16: '首轮', qf: '次轮', sf: '半决赛', third: '三四名决赛', fin: '决赛' };
  }

  function buildState(payload) {
    const sr = payload.knockoutStartRound || 8;
    const labels = labelsForStart(sr);
    const r16 = listFromPayload(payload.round16Matches, 8);
    const qf = listFromPayload(payload.round8Matches, 4);
    const sf = listFromPayload(payload.round4Matches, 2);
    const third = fromSlot(payload.bronzeMatch);
    const fin = fromSlot(payload.goldMatch);
    const qualMap = payload.qualifierByKoSlot || {};
    return { sr, labels, r16, qf, sf, third, fin, qualMap, progress: { done: payload.decidedMatchCount || 0, total: payload.totalMatchCount || 0 } };
  }

  function isLive(m) {
    return m.w != null || m.sc1 != null || m.sc2 != null;
  }

  function scoreHtml(m, pid) {
    const v = pid === m.p1 ? m.sc1 : m.sc2;
    const has = v != null && v !== '';
    return `<div class="score-pill ${has ? '' : 'empty'}">${has ? v : '–'}</div>`;
  }

  function entrantRow(m, side, pid, name) {
    if (pid == null && name === '待定') {
      return `<div class="entrant ${side}"><span class="nm tbd">待定</span><span class="tick">✔</span><div class="score-pill empty">–</div></div>`;
    }
    const win = m.w != null && pid != null && m.w === pid;
    const nm = name || '待定';
    return `<div class="entrant ${side} ${win ? 'win' : ''}" data-player-id="${pid == null ? '' : pid}">
      <span class="nm" title="${escapeHtml(nm)}">${escapeHtml(nm)}</span>
      <span class="tick">✔</span>
      ${scoreHtml(m, pid)}
    </div>`;
  }

  function matchCardHtml(roundKey, idx, m, tag, extraClass) {
    if (!m.id && m.p1 == null && m.p2 == null) {
      return '';
    }
    const live = isLive(m);
    const tagHtml = tag ? `<div class="round-tag"><span class="tg">${escapeHtml(tag)}</span></div>` : '';
    return `<div class="ko-mcard ${extraClass || ''} ${live ? 'live' : 'idle'}" data-m="${roundKey}-${idx}" data-match-id="${m.id || ''}">
      ${tagHtml}
      ${entrantRow(m, 'a', m.p1, m.n1)}
      ${entrantRow(m, 'b', m.p2, m.n2)}
    </div>`;
  }

  function updateBracketProgress(root, state) {
    const progText = root.querySelector('[data-ko-prog-text]');
    const progBar = root.querySelector('[data-ko-prog-bar]');
    const total = state.progress.total || 0;
    const done = state.progress.done || 0;
    if (progText) progText.textContent = total > 0 ? `${done}/${total} 场已决出` : '0/0 场已决出';
    if (progBar) progBar.style.width = (total > 0 ? (done / total) * 100 : 0) + '%';
  }

  function bindMatchCardClicks(container) {
    container.querySelectorAll('.ko-mcard[data-match-id]').forEach((card) => {
      card.addEventListener('click', () => {
        const mid = card.getAttribute('data-match-id');
        if (!mid) return;
        const rows = card.querySelectorAll('.entrant');
        const stub = document.createElement('div');
        stub.setAttribute('data-match-id', mid);
        if (rows[0]) {
          stub.dataset.player1Name = rows[0].querySelector('.nm')?.textContent?.trim() || '';
        }
        if (rows[1]) {
          stub.dataset.player2Name = rows[1].querySelector('.nm')?.textContent?.trim() || '';
        }
        if (typeof global.openGroupMatchModal === 'function') {
          global.openGroupMatchModal(stub);
        }
      });
    });
  }

  function buildFinalStackHtml(state, opts = {}) {
    const includeThird = opts.includeThird !== false;
    const stackClass = opts.stackClass || '';
    const T = state.third;
    const F = state.fin;
    const L = state.labels;
    const cardClass = stackClass ? 'ko-mcard--stack' : '';
    const champ = F.w;
    const runnerUp = champ != null && F.p1 != null ? (F.p1 === champ ? F.p2 : F.p1) : null;
    const thirdWin = T.w;
    const runnerName = runnerUp != null ? (runnerUp === F.p1 ? F.n1 : F.n2) : null;
    const thirdName = thirdWin != null ? (thirdWin === T.p1 ? T.n1 : T.n2) : null;
    const medalRow = opts.medalRow === true;
    let html = `<div class="final-stack ${stackClass}">`;
    if (medalRow) {
      const goldHtml = (F.id || F.p1 || F.p2)
          ? `<div class="ko-slot s-fin">${matchCardHtml('final', 0, F, L.fin, cardClass)}</div>` : '';
      const bronzeHtml = (T.id || T.p1 || T.p2)
          ? `<div class="ko-slot s-third">${matchCardHtml('third', 0, T, L.third, cardClass)}</div>` : '';
      if (goldHtml || bronzeHtml) {
        html += `<div class="ko-mob-medal-row">
          <div class="ko-mob-medal-col ko-mob-medal-gold">${goldHtml}</div>
          <div class="ko-mob-medal-col ko-mob-medal-bronze">${bronzeHtml}</div>
        </div>`;
      }
    } else {
      if (includeThird && (T.id || T.p1 || T.p2)) {
        html += `<div class="ko-slot s-third">${matchCardHtml('third', 0, T, L.third, cardClass)}</div>`;
      }
      html += `<div class="ko-slot s-fin">${matchCardHtml('final', 0, F, L.fin, cardClass)}</div>`;
    }
    html += `<div class="champ-hero ${champ != null ? 'lit' : ''}">
      <div class="champ-trophy">🏆</div>
      <div class="champ-name ${champ == null ? 'ph' : ''}">${champ != null ? escapeHtml(champ === F.p1 ? F.n1 : F.n2) : '虚位以待'}</div>
      <div class="champ-sub">CHAMPION · 冠军</div>
    </div>`;
    html += `<div class="podium-row">
      <div class="pd-col silver">
        <span class="pd-nm ${runnerUp == null ? 'tbd' : ''}">${runnerUp != null ? '🥈 ' + escapeHtml(runnerName) : '待定'}</span>
        <div class="pd-face" style="border:1.5px solid #c9d6e6;border-bottom:none;background:linear-gradient(160deg,#fbfdff,#e3eaf4);"><span style="font-size:11px;font-weight:800;color:#68798f;">亚军</span></div>
      </div>
      <div class="pd-col bronze">
        <span class="pd-nm ${thirdWin == null ? 'tbd' : ''}">${thirdWin != null ? '🥉 ' + escapeHtml(thirdName) : '待定'}</span>
        <div class="pd-face" style="border:1.5px solid #ddb98f;border-bottom:none;background:linear-gradient(160deg,#fdf6ee,#f2dcc2);"><span style="font-size:11px;font-weight:800;color:#96703c;">季军</span></div>
      </div>
    </div>`;
    html += '</div>';
    return html;
  }

  function mobMatchItem(roundKey, idx, m, tag, qualMap, half) {
    if (!m || (!m.id && m.p1 == null && m.p2 == null)) return '';
    const qual = qualMap && roundKey === 'r16' ? qualMap[String(idx)] : null;
    const mainHtml = matchCardHtml(roundKey, idx, m, tag, 'ko-mcard--stack');
    if (!qual) {
      return `<div class="ko-mob-item">${mainHtml}</div>`;
    }
    const qualHtml = matchCardHtml('qual', idx, fromSlot(qual), '资格赛', 'qual-mini s-qual ko-mcard--stack');
    const pairClass = half === 'lower' ? 'ko-mob-pair ko-mob-pair--lower' : 'ko-mob-pair ko-mob-pair--upper';
    const inner = half === 'lower' ? `${mainHtml}${qualHtml}` : `${qualHtml}${mainHtml}`;
    return `<div class="ko-mob-item ${pairClass}">${inner}</div>`;
  }

  function mobItemsHtml(roundKey, indices, list, qualMap, tag, half) {
    let html = '';
    indices.forEach((i) => {
      html += mobMatchItem(roundKey, i, list[i], tag, qualMap, half);
    });
    return html;
  }

  function mobRoundBlock(title, tone, itemsHtml) {
    if (!itemsHtml) return '';
    return `<div class="ko-mob-round ko-mob-tone-${tone}">
      <div class="ko-mob-round-hd">${escapeHtml(title)}</div>
      <div class="ko-mob-matches">${itemsHtml}</div>
    </div>`;
  }

  function mobFlow(label, dir = 'down') {
    const cls = dir === 'up' ? 'ko-mob-flow--up' : 'ko-mob-flow--down';
    return `<div class="ko-mob-flow ${cls}" aria-hidden="true"><span>${escapeHtml(label)}</span></div>`;
  }

  function renderMobileBracket(container, state) {
    const L = state.labels;
    const R = state.r16;
    const Q = state.qf;
    const S = state.sf;
    const qualMap = state.qualMap || {};

    const upperR16 = mobItemsHtml('r16', [0, 1, 2, 3], R, qualMap, '', 'upper');
    const upperQf = mobItemsHtml('qf', [0, 1], Q, null, L.qf, 'upper');
    const upperSf = mobMatchItem('sf', 0, S[0], `半决赛 · 上半区`, null, 'upper');

    const lowerR16 = mobItemsHtml('r16', [4, 5, 6, 7], R, qualMap, '', 'lower');
    const lowerQf = mobItemsHtml('qf', [2, 3], Q, null, L.qf, 'lower');
    const lowerSf = mobMatchItem('sf', 1, S[1], `半决赛 · 下半区`, null, 'lower');

    let html = '';
    html += `<section class="ko-mob-block ko-mob-upper" aria-label="上半区">
      <header class="ko-mob-block-hd"><span class="ko-mob-block-ic">⬆️</span><h3>上半区</h3></header>`;
    html += mobRoundBlock(L.r16, 'r16', upperR16);
    if (upperQf) {
      html += mobFlow('晋级');
      html += mobRoundBlock(L.qf, 'qf', upperQf);
    }
    if (upperSf) {
      html += mobFlow('晋级');
      html += `<div class="ko-mob-round ko-mob-tone-sf"><div class="ko-mob-matches">${upperSf}</div></div>`;
    }
    html += '</section>';

    html += mobFlow('决赛');
    html += `<section class="ko-mob-block ko-mob-center" aria-label="决赛">
      <header class="ko-mob-block-hd"><span class="ko-mob-block-ic">🏁</span><h3>决赛</h3></header>
      ${buildFinalStackHtml(state, { includeThird: true, stackClass: 'ko-mob-final-stack', medalRow: true })}
    </section>`;

    html += mobFlow('下半区');
    html += `<section class="ko-mob-block ko-mob-lower" aria-label="下半区">
      <header class="ko-mob-block-hd"><span class="ko-mob-block-ic">⬇️</span><h3>下半区</h3></header>`;
    if (lowerSf) {
      html += `<div class="ko-mob-round ko-mob-tone-sf"><div class="ko-mob-matches">${lowerSf}</div></div>`;
    }
    if (lowerQf) {
      html += mobFlow('晋级', 'up');
      html += mobRoundBlock(L.qf, 'qf', lowerQf);
    }
    if (lowerR16) {
      html += mobFlow('晋级', 'up');
      html += mobRoundBlock(L.r16, 'r16', lowerR16);
    }
    html += '</section>';

    container.innerHTML = html;
  }

  function renderDesktopBracket(bracket, state) {
    const R = state.r16;
    const Q = state.qf;
    const S = state.sf;
    const T = state.third;
    const F = state.fin;
    const L = state.labels;

    let html = '<svg class="wires" data-ko-wires xmlns="http://www.w3.org/2000/svg"></svg>';
    html += '<div class="zone za"></div><div class="zone zb"></div><div class="zone zc"></div><div class="zone zd"></div>';

    const heads = [[L.r16, 'h-r16'], [L.qf, 'h-qf'], ['半决赛', 'h-sf'], ['半决赛', 'h-sf'], [L.qf, 'h-qf'], [L.r16, 'h-r16']];
    heads.forEach(([t, c], i) => {
      html += `<div class="ch ${c}" style="grid-area:1/${i + 1}">${escapeHtml(t)}</div>`;
    });

    R.forEach((m, i) => {
      if (!m.id && m.p1 == null && m.p2 == null) return;
      const col = i < 4 ? 1 : 6;
      const qual = state.qualMap[String(i)];
      let inner = '';
      if (qual) {
        const qm = fromSlot(qual);
        inner += matchCardHtml('qual', i, qm, '资格赛', 'qual-mini s-qual');
      }
      inner += matchCardHtml('r16', i, m, '');
      html += `<div class="ko-slot s-r16" data-slot="${i}" style="grid-row:${ROW_OF[i]};grid-column:${col}">${inner}</div>`;
    });

    Q.forEach((m, i) => {
      if (!m.id && m.p1 == null && m.p2 == null) return;
      if (i >= 2) return;
      html += `<div class="ko-slot s-qf" style="grid-row:${i === 0 ? 2 : 5}/span 2;grid-column:2">
        ${matchCardHtml('qf', i, m, L.qf)}</div>`;
    });
    Q.forEach((m, i) => {
      if (!m.id && m.p1 == null && m.p2 == null) return;
      if (i < 2) return;
      html += `<div class="ko-slot s-qf" style="grid-row:${i === 2 ? 2 : 5}/span 2;grid-column:5">
        ${matchCardHtml('qf', i, m, L.qf)}</div>`;
    });

    S.forEach((m, i) => {
      if (!m.id && m.p1 == null && m.p2 == null) return;
      html += `<div class="ko-slot s-sf" style="grid-row:2/span 5;grid-column:${i === 0 ? 3 : 4}">
        ${matchCardHtml('sf', i, m, `半决赛 ${i + 1}`)}</div>`;
    });

    if (T.id || T.p1 || T.p2) {
      html += `<div class="third-row"><div class="ko-slot s-third">${matchCardHtml('third', 0, T, L.third)}</div></div>`;
    }

    html += `<div class="final-row">${buildFinalStackHtml(state, { includeThird: false })}</div>`;

    bracket.innerHTML = html;
  }

  function layoutDesktopBracket(root, state) {
    const wrap = root.querySelector('.bracket-wrap');
    const bracket = root.querySelector('.bracket');
    if (!wrap || !bracket) return;
    renderDesktopBracket(bracket, state);
    positionSemi(bracket);
    positionThird(bracket);
    positionFinal(bracket);
    fitBracket(wrap, bracket);
    drawWires(bracket, state);
    fitNames(bracket);
    bindMatchCardClicks(bracket);
  }

  function renderInto(root, state) {
    root.__koState = state;
    const wrap = root.querySelector('.bracket-wrap');
    const desktop = root.querySelector('.bracket');
    const mobile = root.querySelector('.ko-bracket-mobile');
    const scrollHint = root.querySelector('.ko-bracket-scroll-hint');
    const hintDesktop = root.querySelector('[data-ko-hint-desktop]');
    const hintMobile = root.querySelector('[data-ko-hint-mobile]');
    const legend = root.querySelector('.ko-bracket-legend');
    if (!wrap || !desktop) return;

    updateBracketProgress(root, state);
    const narrow = useMobileBracketLayout();
    root.classList.toggle('ko-layout-mobile', narrow);

    if (narrow && mobile) {
      desktop.hidden = true;
      mobile.hidden = false;
      if (scrollHint) scrollHint.hidden = true;
      if (hintDesktop) hintDesktop.hidden = true;
      if (hintMobile) hintMobile.hidden = false;
      if (legend) legend.classList.add('ko-legend--compact');
      wrap.style.height = '';
      wrap.classList.remove('ko-bracket-wrap--scroll');
      renderMobileBracket(mobile, state);
      bindMatchCardClicks(mobile);
      fitNames(mobile);
      return;
    }

    if (mobile) mobile.hidden = true;
    desktop.hidden = false;
    if (scrollHint) scrollHint.hidden = false;
    if (hintDesktop) hintDesktop.hidden = false;
    if (hintMobile) hintMobile.hidden = true;
    if (legend) legend.classList.remove('ko-legend--compact');
    layoutDesktopBracket(root, state);
  }

  function getMatch(state, id) {
    if (id === 'champ') return null;
    const [k, i] = id.split('-');
    const idx = +i || 0;
    if (k === 'r16') return state.r16[idx];
    if (k === 'qf') return state.qf[idx];
    if (k === 'sf') return state.sf[idx];
    if (k === 'third') return state.third;
    if (k === 'final') return state.fin;
    return null;
  }

  const EDGES = (() => {
    const e = [];
    for (let i = 0; i < 8; i++) e.push([`r16-${i}`, `qf-${i >> 1}`, i % 2 === 0 ? 'a' : 'b']);
    for (let i = 0; i < 4; i++) e.push([`qf-${i}`, `sf-${i >> 1}`, i % 2 === 0 ? 'a' : 'b']);
    e.push(['sf-0', 'final-0', 'va'], ['sf-1', 'final-0', 'vb']);
    e.push(['sf-0', 'third-0', 'ta'], ['sf-1', 'third-0', 'tb']);
    e.push(['final-0', 'champ', 'down']);
    return e;
  })();

  function positionSemi(br) {
    const sfSlots = [...br.querySelectorAll('.ko-slot.s-sf')];
    const qfCard = br.querySelector('.ko-slot.s-qf .ko-mcard');
    if (sfSlots.length < 1 || !qfCard) return;
    br.style.transform = 'none';
    const base = br.getBoundingClientRect();
    const k = base.width / (br.offsetWidth || 1) || 1;
    const qfTop = (qfCard.getBoundingClientRect().top - base.top) / k;
    const target = qfTop + qfCard.offsetHeight / 2 + 16;
    sfSlots.forEach((slot) => {
      slot.style.marginTop = '0px';
      slot.style.alignSelf = 'start';
      const slotTop = (slot.getBoundingClientRect().top - base.top) / k;
      slot.style.marginTop = Math.max(0, Math.round(target - slotTop)) + 'px';
    });
  }

  function positionThird(br) {
    const row = br.querySelector('.third-row');
    const sf = br.querySelector('.ko-slot.s-sf .ko-mcard');
    if (!row || !sf) return;
    br.style.transform = 'none';
    const base = br.getBoundingClientRect();
    const k = base.width / (br.offsetWidth || 1) || 1;
    const sfTop = (sf.getBoundingClientRect().top - base.top) / k;
    const card = row.querySelector('.ko-mcard');
    if (!card) return;
    row.style.top = Math.max(4, Math.round(sfTop - 12 - card.offsetHeight)) + 'px';
  }

  function positionFinal(br) {
    const row = br.querySelector('.final-row');
    if (!row) return;
    const stack = row.querySelector('.final-stack');
    if (!stack) return;
    br.style.transform = 'none';
    br.style.paddingTop = '';
    const base = br.getBoundingClientRect();
    const k = base.width / (br.offsetWidth || 1) || 1;

    let minT = Infinity;
    let maxB = -Infinity;
    let minL = Infinity;
    let maxR = -Infinity;
    const addRect = (el) => {
      if (!el || row.contains(el)) return;
      const r = el.getBoundingClientRect();
      if (!r.width && !r.height) return;
      const t = (r.top - base.top) / k;
      const b = (r.bottom - base.top) / k;
      const l = (r.left - base.left) / k;
      const rt = (r.right - base.left) / k;
      minT = Math.min(minT, t);
      maxB = Math.max(maxB, b);
      minL = Math.min(minL, l);
      maxR = Math.max(maxR, rt);
    };

    br.querySelectorAll('.ch').forEach(addRect);
    br.querySelectorAll('.ko-slot .ko-mcard').forEach(addRect);
    br.querySelectorAll('.third-row .ko-mcard').forEach(addRect);

    if (!Number.isFinite(minT) || !Number.isFinite(maxB)) return;

    const cx = (minL + maxR) / 2;
    const cy = (minT + maxB) / 2;
    const stackH = stack.offsetHeight;
    const top = Math.round(cy - stackH / 2);

    row.style.left = '0';
    row.style.right = '0';
    row.style.width = '';
    row.style.justifyContent = 'center';
    row.style.top = `${top}px`;

    const stackBottom = top + stackH;
    const curPad = parseFloat(getComputedStyle(br).paddingBottom) || 0;
    const contentH = br.offsetHeight - curPad;
    const extra = Math.max(0, stackBottom + 18 - contentH, 18 - top);
    br.style.paddingBottom = `${18 + extra}px`;
    if (top < 4) {
      br.style.paddingTop = `${4 - top}px`;
    }
  }

  function fitBracket(wrap, br, opts = {}) {
    br.style.transform = 'none';
    if (opts.export) {
      const natH = br.offsetHeight;
      wrap.style.height = Math.ceil((natH || 320) + 12) + 'px';
      wrap.style.overflow = 'visible';
      wrap.classList.remove('ko-bracket-wrap--scroll');
      return;
    }
    const avail = Math.max(wrap.clientWidth - 4, wrap.parentElement ? wrap.parentElement.clientWidth - 32 : 0);
    const natW = br.offsetWidth;
    const natH = br.offsetHeight;
    if (!natW || !natH) {
      wrap.style.height = '320px';
      return;
    }
    const narrow = window.innerWidth < 768;
    let s = (avail - 2) / natW;
    if (!Number.isFinite(s) || s <= 0) {
      s = narrow ? 0.62 : 0.4;
    }
    if (narrow) {
      s = Math.max(s, 0.62);
    } else {
      s = Math.min(1, s);
    }
    const scaledW = natW * s;
    const off = narrow && scaledW > avail ? 0 : Math.max(0, (avail - scaledW) / 2);
    br.style.transform = `translateX(${off}px) scale(${s})`;
    wrap.style.height = Math.ceil(natH * s + 8) + 'px';
    wrap.classList.toggle('ko-bracket-wrap--scroll', narrow && scaledW > avail + 2);
    const root = wrap.closest('.ko-bracket-root');
    if (root) root.classList.toggle('ko-bracket-root--narrow', narrow);
  }

  function drawWires(br, state) {
    const svg = br.querySelector('[data-ko-wires]');
    if (!svg) return;
    const base = br.getBoundingClientRect();
    const k = base.width / br.offsetWidth || 1;
    const W = br.offsetWidth;
    const H = br.offsetHeight;
    svg.setAttribute('width', W);
    svg.setAttribute('height', H);
    svg.setAttribute('viewBox', `0 0 ${W} ${H}`);

    const box = (el) => {
      const r = el.getBoundingClientRect();
      const l = (r.left - base.left) / k;
      const t = (r.top - base.top) / k;
      return { l, rt: l + r.width / k, t, b: t + r.height / k, cx: l + r.width / 2 / k, cy: t + r.height / 2 / k };
    };

    const HEAD = 7.5;
    const HALF = 4;
    const R = 8;
    const out = [];

    const elbow = (sx, sy, tx, ty, dir) => {
      const ex = tx - dir * HEAD;
      if (Math.abs(ty - sy) < 1.2) return `M ${sx} ${sy} H ${ex}`;
      const vdir = ty > sy ? 1 : -1;
      const span = Math.abs(tx - sx);
      const mx = sx + dir * Math.max(R + 4, Math.min(24, span * 0.55));
      const rr = Math.min(R, Math.abs(ty - sy) / 2, Math.abs(mx - sx));
      return `M ${sx} ${sy} H ${mx - dir * rr} Q ${mx} ${sy} ${mx} ${sy + vdir * rr} V ${ty - vdir * rr} Q ${mx} ${ty} ${mx + dir * rr} ${ty} H ${ex}`;
    };

    EDGES.forEach(([from, to, slot]) => {
      const srcCard = br.querySelector(`[data-m="${from}"]`);
      const dstCard = br.querySelector(`[data-m="${to}"]`);
      if (!srcCard || (!dstCard && slot !== 'down')) return;

      const sm = getMatch(state, from);
      const active = sm && sm.w != null;
      const s = box(srcCard);
      const sRows = [...srcCard.querySelectorAll('.entrant')];

      let sy = s.cy;
      if (sm && sm.w != null) {
        const wr = sRows.find((r) => {
          const pid = r.getAttribute('data-player-id');
          return pid && +pid === sm.w;
        });
        if (wr) sy = box(wr).cy;
      }

      const roundKey = from.split('-')[0];
      const cls = (active ? 'on ' : '') + (slot === 'ta' || slot === 'tb' ? 'third' : roundKey === 'final' ? 'fin' : roundKey);

      if (slot === 'down') {
        const champEl = br.querySelector('.champ-hero');
        if (!champEl) return;
        const t = box(champEl);
        const cx = s.cx;
        const y0 = s.b + 2;
        const y1 = t.t - 2;
        const ex = y1 - HEAD;
        out.push(`<path class="wire ${cls}" d="M ${cx} ${y0} V ${ex}"/>`);
        return;
      }

      if (!dstCard) return;
      const t = box(dstCard);
      const tRows = [...dstCard.querySelectorAll('.entrant')];

      if (slot === 'va' || slot === 'vb') {
        const left = slot === 'va';
        const tRow = tRows[left ? 0 : 1] || tRows[0];
        const ty = tRow ? box(tRow).cy : t.cy;
        const sx = s.cx;
        const y0 = s.b + 2;
        const tx = left ? t.l - 1 : t.rt + 1;
        const dir = left ? 1 : -1;
        const d = `M ${sx} ${y0} V ${ty} H ${tx - dir * HEAD}`;
        out.push(`<path class="wire ${cls}" d="${d}"/>`);
        return;
      }

      if (slot === 'ta' || slot === 'tb') {
        const left = slot === 'ta';
        const tRow = tRows[left ? 0 : 1] || tRows[0];
        const ty = tRow ? box(tRow).cy : t.cy;
        const sx = s.cx;
        const y0 = s.t - 2;
        const tx = left ? t.l - 1 : t.rt + 1;
        const dir = left ? 1 : -1;
        const d = `M ${sx} ${y0} V ${ty} H ${tx - dir * HEAD}`;
        out.push(`<path class="wire ${cls}" d="${d}"/>`);
        return;
      }

      const dir = t.l > s.rt ? 1 : -1;
      const sx = dir > 0 ? s.rt : s.l;
      const tx = dir > 0 ? t.l - 1 : t.rt + 1;
      const tRow = tRows[slot === 'b' ? 1 : 0] || tRows[0];
      const ty = tRow ? box(tRow).cy : t.cy;
      out.push(`<path class="wire ${cls}" d="${elbow(sx, sy, tx, ty, dir)}"/>`);
    });

    svg.innerHTML = out.join('');
  }

  function fitNames(br) {
    br.querySelectorAll('.entrant .nm, .pd-col .pd-nm').forEach((nm) => {
      nm.style.whiteSpace = '';
      if (nm.scrollWidth > nm.clientWidth + 0.5) nm.style.whiteSpace = 'normal';
    });
  }

  function shellHtml() {
    return `
      <div class="ko-bracket-panel-hd">
        <h2>淘汰赛对阵图</h2>
        <span class="sub">点击场次卡片可查看比分与录入；加粗连线表示已晋级队伍</span>
        <div class="ko-bracket-progress"><span data-ko-prog-text>加载中…</span><div class="bar"><i data-ko-prog-bar></i></div></div>
        <div class="ko-bracket-toolbar ms-md-auto d-flex flex-wrap gap-2">
          <button type="button" class="btn btn-sm btn-outline-secondary" data-ko-export-text title="复制纯文本对阵表">
            <i class="bi bi-clipboard"></i> 复制文本
          </button>
          <button type="button" class="btn btn-sm btn-outline-primary" data-ko-export-pdf title="打印宽屏对阵图，在对话框中选「另存为 PDF」">
            <i class="bi bi-printer"></i> 打印 / PDF
          </button>
        </div>
      </div>
      <div class="bracket-wrap">
        <div class="bracket"></div>
        <div class="ko-bracket-mobile" hidden></div>
        <p class="ko-bracket-scroll-hint" aria-hidden="true">左右滑动查看完整对阵图</p>
      </div>
      <div class="ko-bracket-hint" data-ko-hint-desktop>左半区 → 半决赛 → 决赛 · 半决赛负者 → 三四名决赛 · 下方为冠军与领奖台</div>
      <div class="ko-bracket-hint ko-bracket-hint--mob" data-ko-hint-mobile hidden>自上而下浏览：上半区 → 决赛 → 下半区；点击场次卡片可查看比分</div>
      <div class="ko-bracket-legend">
        <span><i class="sw" style="border-color:#3b82f6"></i>1/8 决赛</span>
        <span><i class="sw" style="border-color:#8b5cf6"></i>1/4 决赛</span>
        <span><i class="sw" style="border-color:#f97316"></i>半决赛</span>
        <span><i class="sw" style="border-color:#16a34a"></i>三四名决赛</span>
        <span><i class="sw" style="border-color:#e11d48"></i>决赛</span>
        <span><i class="sw hot" style="border-color:#cf9c7c"></i>加粗连线：已晋级</span>
      </div>`;
  }

  function payloadHasBracketData(payload) {
    if (!payload) return false;
    if (payload.hasRound16 || payload.hasRound8 || payload.hasRound4 || payload.hasFinal || payload.hasQualifiers) {
      return true;
    }
    const hasSlot = (arr) => Array.isArray(arr) && arr.some((x) => x && x.id != null);
    return hasSlot(payload.round16Matches)
        || hasSlot(payload.round8Matches)
        || hasSlot(payload.round4Matches)
        || (payload.goldMatch && payload.goldMatch.id != null)
        || (payload.bronzeMatch && payload.bronzeMatch.id != null)
        || (payload.qualifierByKoSlot && Object.keys(payload.qualifierByKoSlot).length > 0);
  }

  function exportTitle(root) {
    const t = root.getAttribute('data-export-title');
    if (t && t.trim()) return t.trim();
    return '淘汰赛对阵图';
  }

  function formatMatchTextLine(m) {
    if (!m || m.id == null) return null;
    const sc1 = m.sc1 != null && m.sc1 !== '' ? m.sc1 : '–';
    const sc2 = m.sc2 != null && m.sc2 !== '' ? m.sc2 : '–';
    const n1 = m.n1 || '待定';
    const n2 = m.n2 || '待定';
    const mark1 = m.w != null && m.p1 != null && m.w === m.p1 ? ' ✓' : '';
    const mark2 = m.w != null && m.p2 != null && m.w === m.p2 ? ' ✓' : '';
    return `${n1}${mark1}  ${sc1}:${sc2}  ${n2}${mark2}`;
  }

  function buildBracketText(state, title) {
    const L = state.labels;
    const lines = [];
    lines.push(title || '淘汰赛对阵图');
    const total = state.progress.total || 0;
    const done = state.progress.done || 0;
    lines.push(total > 0 ? `进度：${done}/${total} 场已决出` : '进度：暂无场次');
    lines.push(`导出时间：${new Date().toLocaleString('zh-CN')}`);
    lines.push('');

    const pushSection = (heading, items, labelEach) => {
      const rows = [];
      items.forEach((m, i) => {
        const line = formatMatchTextLine(m);
        if (!line) return;
        const tag = labelEach ? labelEach(i) : `第${i + 1}场`;
        rows.push(`  ${tag}  ${line}`);
      });
      if (!rows.length) return;
      lines.push(`【${heading}】`);
      lines.push(...rows);
      lines.push('');
    };

    const qualMap = state.qualMap || {};
    const qualLines = [];
    Object.keys(qualMap).sort((a, b) => +a - +b).forEach((key) => {
      const line = formatMatchTextLine(fromSlot(qualMap[key]));
      if (line) qualLines.push(`  挂载首轮卡位 ${+key + 1}  ${line}`);
    });
    if (qualLines.length) {
      lines.push('【挂载资格赛】');
      lines.push(...qualLines);
      lines.push('');
    }

    pushSection(L.r16, state.r16);
    pushSection(L.qf, state.qf);
    pushSection('半决赛', state.sf);
    pushSection(L.third, [state.third]);
    pushSection(L.fin, [state.fin]);

    const F = state.fin;
    const champ = F.w;
    if (champ != null) {
      const name = champ === F.p1 ? F.n1 : F.n2;
      lines.push(`冠军：${name || '—'}`);
    }
    return lines.join('\n');
  }

  async function copyBracketText(root) {
    const state = root.__koState;
    if (!state) {
      if (typeof global.showAlert === 'function') global.showAlert('对阵数据尚未加载', 'warning');
      return;
    }
    const text = buildBracketText(state, exportTitle(root));
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(text);
      } else {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.left = '-9999px';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        ta.remove();
      }
      if (typeof global.showAlert === 'function') global.showAlert('对阵文本已复制到剪贴板', 'success');
    } catch (e) {
      if (typeof global.showAlert === 'function') global.showAlert('复制失败，请重试', 'danger');
    }
  }

  function buildPrintStageHtml(title, meta) {
    return `
      <div class="ko-bracket-print-stage">
        <div class="ko-bracket-export-hd">
          <h2>${escapeHtml(title)}</h2>
          <p class="ko-bracket-export-meta">${escapeHtml(meta)}</p>
        </div>
        <div class="ko-bracket-root ko-bracket-export-root ko-pdf-simple">
          <div class="bracket-wrap"><div class="bracket"></div></div>
        </div>
      </div>`;
  }

  function layoutDesktopBracketExport(host, state) {
    const wrap = host.querySelector('.bracket-wrap');
    const bracket = host.querySelector('.bracket');
    if (!wrap || !bracket) return;
    renderDesktopBracket(bracket, state);
    positionSemi(bracket);
    positionThird(bracket);
    positionFinal(bracket);
    fitBracket(wrap, bracket, { export: true });
    drawWires(bracket, state);
    fitNames(bracket);
    host.querySelectorAll('[style]').forEach((el) => {
      const s = el.getAttribute('style') || '';
      if (/gradient|color-mix/i.test(s)) el.removeAttribute('style');
    });
  }

  function cleanupPrintStage(stage, btn) {
    document.body.classList.remove('ko-bracket-printing');
    stage?.remove();
    if (btn) btn.disabled = false;
  }

  async function exportBracketPdf(root) {
    const state = root.__koState;
    if (!state) {
      if (typeof global.showAlert === 'function') global.showAlert('对阵数据尚未加载', 'warning');
      return;
    }
    const btn = root.querySelector('[data-ko-export-pdf]');
    if (btn) btn.disabled = true;

    const title = exportTitle(root);
    const total = state.progress.total || 0;
    const done = state.progress.done || 0;
    const meta = total > 0
        ? `进度 ${done}/${total} 场已决出 · ${new Date().toLocaleString('zh-CN')}`
        : new Date().toLocaleString('zh-CN');

    document.getElementById('ko-bracket-print-stage')?.remove();
    const stage = document.createElement('div');
    stage.id = 'ko-bracket-print-stage';
    stage.innerHTML = buildPrintStageHtml(title, meta);
    document.body.appendChild(stage);

    const exportRoot = stage.querySelector('.ko-bracket-export-root');
    layoutDesktopBracketExport(exportRoot, state);
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));

    document.body.classList.add('ko-bracket-printing');
    if (typeof global.showAlert === 'function') {
      global.showAlert('请在打印窗口的目标打印机中选择「另存为 PDF」或「Microsoft Print to PDF」', 'info');
    }

    const onAfterPrint = () => {
      window.removeEventListener('afterprint', onAfterPrint);
      cleanupPrintStage(stage, btn);
    };
    window.addEventListener('afterprint', onAfterPrint);

    setTimeout(() => {
      try {
        global.print();
      } catch (e) {
        console.error(e);
        cleanupPrintStage(stage, btn);
        if (typeof global.showAlert === 'function') global.showAlert('无法打开打印对话框', 'danger');
        return;
      }
      setTimeout(() => {
        if (document.getElementById('ko-bracket-print-stage')) {
          cleanupPrintStage(stage, btn);
        }
      }, 8000);
    }, 400);
  }

  function bindExportControls(root) {
    root.querySelector('[data-ko-export-text]')?.addEventListener('click', () => copyBracketText(root));
    root.querySelector('[data-ko-export-pdf]')?.addEventListener('click', () => exportBracketPdf(root));
  }

  async function fetchData(url) {
    if (dataCache.has(url)) return dataCache.get(url);
    const res = await fetch(url, { credentials: 'same-origin' });
    if (!res.ok) throw new Error('对阵数据加载失败');
    const json = await res.json();
    dataCache.set(url, json);
    return json;
  }

  async function init(root, fetchUrl) {
    if (!root || !fetchUrl) return;
    if (!root.getAttribute('data-fetch-url')) {
      root.setAttribute('data-fetch-url', fetchUrl);
    }
    root.innerHTML = shellHtml();
    try {
      const payload = await fetchData(fetchUrl);
      if (!payloadHasBracketData(payload)) {
        root.innerHTML = '<div class="ko-bracket-empty">暂无淘汰赛对阵，请先生成淘汰赛首轮。</div>';
        return;
      }
      const state = buildState(payload);
      renderInto(root, state);
      bindExportControls(root);
      const onResize = () => {
        const cached = root.__koState;
        if (!cached) return;
        const mode = useMobileBracketLayout() ? 'mobile' : 'desktop';
        if (root.__koLayoutMode !== mode) {
          root.__koLayoutMode = mode;
          renderInto(root, cached);
          return;
        }
        if (mode === 'mobile') {
          const mobile = root.querySelector('.ko-bracket-mobile');
          if (mobile) fitNames(mobile);
          return;
        }
        const wrap = root.querySelector('.bracket-wrap');
        const br = root.querySelector('.bracket');
        if (!wrap || !br) return;
        positionSemi(br);
        positionThird(br);
        positionFinal(br);
        fitBracket(wrap, br);
        drawWires(br, cached);
        fitNames(br);
      };
      root.__koLayoutMode = useMobileBracketLayout() ? 'mobile' : 'desktop';
      root.__koReflow = onResize;
      window.addEventListener('resize', () => {
        clearTimeout(root.__koRsz);
        root.__koRsz = setTimeout(onResize, 120);
      });
      root.dataset.koInited = '1';
      const scheduleReflow = (attempt) => {
        requestAnimationFrame(() => {
          onResize();
          if (root.__koLayoutMode === 'mobile') return;
          const brEl = root.querySelector('.bracket');
          if (attempt < 8 && brEl && (!brEl.offsetWidth || !brEl.offsetHeight)) {
            scheduleReflow(attempt + 1);
          }
        });
      };
      scheduleReflow(0);
      root.__koRefresh = () => {
        dataCache.delete(fetchUrl);
        root.dataset.koInited = '0';
        return init(root, fetchUrl);
      };
    } catch (e) {
      root.dataset.koInited = '0';
      root.innerHTML = `<div class="ko-bracket-empty text-danger">${escapeHtml(e.message || '加载失败')}</div>`;
    }
  }

  function ensure(root) {
    if (!root) return;
    const url = root.getAttribute('data-fetch-url')
        || root.closest('#sharedKoBracketPanel')?.getAttribute('data-fetch-url');
    if (!url) return;
    if (root.dataset.koInited === '1') {
      if (typeof root.__koReflow === 'function') {
        requestAnimationFrame(() => requestAnimationFrame(root.__koReflow));
      }
      return;
    }
    if (root.dataset.koLoading === '1') return;
    root.dataset.koLoading = '1';
    init(root, url).finally(() => {
      root.dataset.koLoading = '0';
    });
  }

  global.KnockoutBracketView = {
    init,
    ensure,
    invalidateCache: () => dataCache.clear(),
    copyText: copyBracketText,
    exportPdf: exportBracketPdf,
    buildText: buildBracketText,
  };
})(window);

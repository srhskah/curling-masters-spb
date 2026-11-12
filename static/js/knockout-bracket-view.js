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

  function readHalf(s) {
    if (!s) return null;
    const h = s.knockoutHalf;
    return h === 0 || h === 1 ? h : null;
  }

  function fromSlot(s) {
    const half = readHalf(s);
    if (!s || s.id == null) {
      return { id: null, p1: null, p2: null, n1: '待定', n2: '待定', sc1: null, sc2: null, w: null, half };
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
      half,
    };
  }

  function listFromPayload(arr, fallbackSize) {
    const out = [];
    const src = Array.isArray(arr) ? arr : [];
    const size = src.length > 0 ? src.length : (fallbackSize || 0);
    for (let i = 0; i < size; i++) {
      out.push(fromSlot(src[i]));
    }
    return out;
  }

  function hasMatch(m) {
    return !!(m && (m.id || m.p1 != null || m.p2 != null));
  }

  function splitHalfIndices(list) {
    const n = list.length;
    const cut = Math.max(1, Math.floor(n / 2));
    const upper = [];
    const lower = [];
    let markedU = 0;
    let markedL = 0;
    for (let i = 0; i < n; i++) {
      if (!hasMatch(list[i])) continue;
      const h = list[i].half;
      if (h === 1) markedL += 1;
      else if (h === 0) markedU += 1;
    }
    const useHalf = markedU > 0 && markedL > 0;
    for (let i = 0; i < n; i++) {
      if (!hasMatch(list[i])) continue;
      if (useHalf) {
        const h = list[i].half;
        if (h === 1) lower.push(i);
        else if (h === 0) upper.push(i);
        else if (i < cut) upper.push(i);
        else lower.push(i);
      } else if (i < cut) {
        upper.push(i);
      } else {
        lower.push(i);
      }
    }
    return { upper, lower, cut };
  }

  /**
   * 视觉行号按「整轮卡位」落区，不用「本半区已出场次序号」。
   * 否则稀疏晋级时（例如只有 slot=1 的 1/4）会被挤到上半区格子，和生成它的 1/8 不在同一色块。
   */
  function halfBandSize(roundLen) {
    return Math.max(1, Math.floor((roundLen || 0) / 2));
  }

  function localSlotInHalf(arrayIndex, roundLen) {
    const cut = halfBandSize(roundLen);
    if (arrayIndex < cut) return Math.max(0, arrayIndex);
    return Math.max(0, arrayIndex - cut);
  }

  function r16RowForIndex(arrayIndex, roundLen) {
    const local = localSlotInHalf(arrayIndex, roundLen);
    return ROW_OF[Math.min(local, ROW_OF.length - 1)] || 2;
  }

  function qfRowForIndex(arrayIndex, roundLen) {
    const local = localSlotInHalf(arrayIndex, roundLen);
    return local <= 0 ? 2 : 5;
  }

  function sfHalfLabel(m, fallbackIdx) {
    return fallbackIdx === 1 ? '半决赛 · 下半区' : '半决赛 · 上半区';
  }

  function labelsForStart(sr) {
    if (sr === 16 || sr === 8) {
      return { r16: '1/8 决赛', qf: '1/4 决赛', sf: '半决赛', third: '三四名决赛', fin: '决赛' };
    }
    if (sr === 4) {
      return { r16: '1/8 决赛', qf: '1/4 决赛', sf: '半决赛', third: '三四名决赛', fin: '决赛' };
    }
    if (sr === 2) {
      return { r16: '1/8 决赛', qf: '1/4 决赛', sf: '半决赛', third: '三四名决赛', fin: '决赛' };
    }
    return { r16: '首轮', qf: '次轮', sf: '半决赛', third: '三四名决赛', fin: '决赛' };
  }

  /**
   * 后端把「淘汰赛第一轮」放进 round16Matches。
   * 若首轮就是 1/4（startRound=4）或半决赛（=2），要挪到对应视觉轮次，
   * 否则 8 人签表会占着 1/8 列、半决赛列留空，上下半区也对不齐。
   */
  function mapRoundsFromPayload(payload) {
    const sr = payload.knockoutStartRound || 8;
    if (sr === 4) {
      return {
        r16: listFromPayload([], 0),
        qf: listFromPayload(payload.round16Matches, 4),
        sf: listFromPayload(payload.round8Matches, 2),
      };
    }
    if (sr === 2) {
      return {
        r16: listFromPayload([], 0),
        qf: listFromPayload([], 0),
        sf: listFromPayload(payload.round16Matches, 2),
      };
    }
    return {
      r16: listFromPayload(payload.round16Matches, 8),
      qf: listFromPayload(payload.round8Matches, 4),
      sf: listFromPayload(payload.round4Matches, 2),
    };
  }

  function buildState(payload) {
    const sr = payload.knockoutStartRound || 8;
    const labels = labelsForStart(sr);
    const rounds = mapRoundsFromPayload(payload);
    const third = fromSlot(payload.bronzeMatch);
    const fin = fromSlot(payload.goldMatch);
    const qualMap = payload.qualifierByKoSlot || {};
    return { sr, labels, r16: rounds.r16, qf: rounds.qf, sf: rounds.sf, third, fin, qualMap, progress: { done: payload.decidedMatchCount || 0, total: payload.totalMatchCount || 0 } };
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

  function qualCardHtml(idx, qual) {
    if (!qual) return '';
    return matchCardHtml('qual', idx, fromSlot(qual), '资格赛', 'qual-mini s-qual');
  }

  function firstRoundInnerHtml(roundKey, idx, m, tag, qual, outerLeft) {
    const main = matchCardHtml(roundKey, idx, m, tag);
    const q = qualCardHtml(idx, qual);
    if (!q) return main;
    return outerLeft ? `${q}${main}` : `${main}${q}`;
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

  function hasSlot(m) {
    return !!(m && (m.id || m.p1 || m.p2));
  }

  function buildPodiumHtml(state) {
    const T = state.third;
    const F = state.fin;
    const champ = F.w;
    const runnerUp = champ != null && F.p1 != null ? (F.p1 === champ ? F.p2 : F.p1) : null;
    const thirdWin = T.w;
    const runnerName = runnerUp != null ? (runnerUp === F.p1 ? F.n1 : F.n2) : null;
    const thirdName = thirdWin != null ? (thirdWin === T.p1 ? T.n1 : T.n2) : null;
    return `<div class="champ-hero ${champ != null ? 'lit' : ''}">
      <div class="champ-trophy">🏆</div>
      <div class="champ-name ${champ == null ? 'ph' : ''}">${champ != null ? escapeHtml(champ === F.p1 ? F.n1 : F.n2) : '虚位以待'}</div>
      <div class="champ-sub">CHAMPION · 冠军</div>
    </div>
    <div class="podium-row">
      <div class="pd-col silver">
        <span class="pd-nm ${runnerUp == null ? 'tbd' : ''}">${runnerUp != null ? '🥈 ' + escapeHtml(runnerName) : '待定'}</span>
        <div class="pd-face" style="border:1.5px solid #c9d6e6;border-bottom:none;background:linear-gradient(160deg,#fbfdff,#e3eaf4);"><span style="font-size:11px;font-weight:800;color:#68798f;">亚军</span></div>
      </div>
      <div class="pd-col bronze">
        <span class="pd-nm ${thirdWin == null ? 'tbd' : ''}">${thirdWin != null ? '🥉 ' + escapeHtml(thirdName) : '待定'}</span>
        <div class="pd-face" style="border:1.5px solid #ddb98f;border-bottom:none;background:linear-gradient(160deg,#fdf6ee,#f2dcc2);"><span style="font-size:11px;font-weight:800;color:#96703c;">季军</span></div>
      </div>
    </div>`;
  }

  function buildFinalStackHtml(state, opts = {}) {
    const includeThird = opts.includeThird !== false;
    const stackClass = opts.stackClass || '';
    const T = state.third;
    const F = state.fin;
    const L = state.labels;
    const cardClass = stackClass ? 'ko-mcard--stack' : '';
    const medalRow = opts.medalRow === true;
    let html = `<div class="final-stack ${stackClass}">`;
    if (medalRow) {
      const goldHtml = hasSlot(F)
          ? `<div class="ko-slot s-fin">${matchCardHtml('final', 0, F, L.fin, cardClass)}</div>` : '';
      const bronzeHtml = hasSlot(T)
          ? `<div class="ko-slot s-third">${matchCardHtml('third', 0, T, L.third, cardClass)}</div>` : '';
      if (goldHtml || bronzeHtml) {
        html += `<div class="ko-mob-medal-row">
          <div class="ko-mob-medal-col ko-mob-medal-gold">${goldHtml}</div>
          <div class="ko-mob-medal-col ko-mob-medal-bronze">${bronzeHtml}</div>
        </div>`;
      }
    } else {
      if (includeThird && hasSlot(T)) {
        html += `<div class="ko-slot s-third">${matchCardHtml('third', 0, T, L.third, cardClass)}</div>`;
      }
      html += `<div class="ko-slot s-fin">${matchCardHtml('final', 0, F, L.fin, cardClass)}</div>`;
    }
    html += buildPodiumHtml(state);
    html += '</div>';
    return html;
  }

  function mobQualAdvance(dir) {
    const up = dir === 'up';
    const cls = up ? 'ko-mob-qual-advance ko-mob-qual-advance--up' : 'ko-mob-qual-advance';
    const mark = up ? '↑ ' : '↓ ';
    return `<div class="${cls}" aria-hidden="true"><span>${mark}晋级</span></div>`;
  }

  function mobMatchItem(roundKey, idx, m, tag, qualMap, half) {
    if (!m || (!m.id && m.p1 == null && m.p2 == null)) return '';
    const qual = qualMap ? qualMap[String(idx)] : null;
    const mainHtml = matchCardHtml(roundKey, idx, m, tag, 'ko-mcard--stack');
    if (!qual) {
      return `<div class="ko-mob-item">${mainHtml}</div>`;
    }
    const qualHtml = matchCardHtml('qual', idx, fromSlot(qual), '资格赛', 'qual-mini s-qual ko-mcard--stack');
    const lower = half === 'lower';
    const stack = lower
      ? `${mainHtml}${mobQualAdvance('up')}${qualHtml}`
      : `${qualHtml}${mobQualAdvance('down')}${mainHtml}`;
    const pairClass = lower ? 'ko-mob-pair ko-mob-pair--lower' : 'ko-mob-pair ko-mob-pair--upper';
    return `<div class="ko-mob-item ${pairClass}">${stack}</div>`;
  }

  function mobItemsHtml(roundKey, indices, list, qualMap, tag, half) {
    let html = '';
    indices.forEach((i) => {
      html += mobMatchItem(roundKey, i, list[i], tag, qualMap, half);
    });
    return html;
  }

  function mobRoundBlock(title, tone, itemsHtml, extraClass) {
    if (!itemsHtml) return '';
    const extra = extraClass ? ` ${extraClass}` : '';
    return `<div class="ko-mob-round ko-mob-tone-${tone}${extra}">
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

    const hasR16 = R.some(hasMatch);
    const hasQf = Q.some(hasMatch);
    const firstKey = hasR16 ? 'r16' : (hasQf ? 'qf' : 'sf');
    const r16Qual = firstKey === 'r16' ? qualMap : null;
    const qfQual = firstKey === 'qf' ? qualMap : null;
    const sfQual = firstKey === 'sf' ? qualMap : null;
    const firstUpperCls = 'ko-mob-round--first ko-mob-round--first-upper';
    const firstLowerCls = 'ko-mob-round--first ko-mob-round--first-lower';

    const r16H = splitHalfIndices(R);
    const qfH = splitHalfIndices(Q);
    const sfH = splitHalfIndices(S);
    const upperR16 = mobItemsHtml('r16', r16H.upper, R, r16Qual, '', 'upper');
    const upperQf = mobItemsHtml('qf', qfH.upper, Q, qfQual, L.qf, 'upper');
    let upperSf = '';
    sfH.upper.forEach((i) => {
      upperSf += mobMatchItem('sf', i, S[i], sfHalfLabel(S[i], 0), sfQual, 'upper');
    });

    const lowerR16 = mobItemsHtml('r16', r16H.lower, R, r16Qual, '', 'lower');
    const lowerQf = mobItemsHtml('qf', qfH.lower, Q, qfQual, L.qf, 'lower');
    let lowerSf = '';
    sfH.lower.forEach((i) => {
      lowerSf += mobMatchItem('sf', i, S[i], sfHalfLabel(S[i], 1), sfQual, 'lower');
    });

    let html = '';
    html += `<section class="ko-mob-block ko-mob-upper" aria-label="上半区">
      <header class="ko-mob-block-hd"><span class="ko-mob-block-ic">⬆️</span><h3>上半区</h3></header>`;
    html += mobRoundBlock(L.r16, 'r16', upperR16, firstKey === 'r16' ? firstUpperCls : '');
    if (upperQf) {
      html += mobFlow('晋级');
      html += mobRoundBlock(L.qf, 'qf', upperQf, firstKey === 'qf' ? firstUpperCls : '');
    }
    if (upperSf) {
      html += mobFlow('晋级');
      if (firstKey === 'sf') {
        html += mobRoundBlock(L.sf, 'sf', upperSf, firstUpperCls);
      } else {
        html += `<div class="ko-mob-round ko-mob-tone-sf"><div class="ko-mob-matches">${upperSf}</div></div>`;
      }
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
      if (firstKey === 'sf') {
        html += mobRoundBlock(L.sf, 'sf', lowerSf, firstLowerCls);
      } else {
        html += `<div class="ko-mob-round ko-mob-tone-sf"><div class="ko-mob-matches">${lowerSf}</div></div>`;
      }
    }
    if (lowerQf) {
      html += mobFlow('晋级', 'up');
      html += mobRoundBlock(L.qf, 'qf', lowerQf, firstKey === 'qf' ? firstLowerCls : '');
    }
    if (lowerR16) {
      html += mobFlow('晋级', 'up');
      html += mobRoundBlock(L.r16, 'r16', lowerR16, firstKey === 'r16' ? firstLowerCls : '');
    }
    html += '</section>';

    container.innerHTML = html;
  }

  function desktopColumnPlan(state) {
    const hasR16 = state.r16.some(hasMatch);
    const hasQf = state.qf.some(hasMatch);
    const hasSf = state.sf.some(hasMatch);
    const left = [];
    if (hasR16) left.push('r16');
    if (hasQf) left.push('qf');
    if (hasSf) left.push('sf');
    const right = left.slice().reverse();
    const keys = left.concat(['medal'], right);
    const col = { medal: left.length + 1 };
    left.forEach((key, i) => { col[key + 'L'] = i + 1; });
    right.forEach((key, i) => { col[key + 'R'] = left.length + 2 + i; });
    const qualOuter = !!(state.qualMap && Object.keys(state.qualMap).length);
    const screen = keys.map((key, i) => {
      const outer = i === 0 || i === keys.length - 1;
      if (key === 'medal') return 'minmax(0, 0.9fr)';
      if (outer && qualOuter) return 'minmax(0, 2.05fr)';
      return 'minmax(0, 1.16fr)';
    }).join(' ');
    const print = keys.map((key, i) => {
      const outer = i === 0 || i === keys.length - 1;
      if (key === 'medal') return '200px';
      if (outer && qualOuter) return '340px';
      return '228px';
    }).join(' ');
    return { hasR16, hasQf, hasSf, left, keys, col, screen, print, qualOuter };
  }

  function renderDesktopBracket(bracket, state) {
    const R = state.r16;
    const Q = state.qf;
    const S = state.sf;
    const T = state.third;
    const F = state.fin;
    const L = state.labels;
    const plan = desktopColumnPlan(state);
    const columns = plan.col;

    bracket.classList.toggle('ko-bracket--no-r16', !plan.hasR16);
    bracket.classList.toggle('ko-bracket--no-qf', !plan.hasQf);
    bracket.classList.toggle('ko-bracket--no-sf', !plan.hasSf);
    bracket.style.setProperty('--ko-cols', plan.screen);
    bracket.style.setProperty('--ko-cols-print', plan.print);

    const headOf = {
      r16: [L.r16, 'h-r16'],
      qf: [L.qf, 'h-qf'],
      sf: ['半决赛', 'h-sf'],
      medal: ['奖牌赛', 'h-medal']
    };

    let html = '<svg class="wires" data-ko-wires xmlns="http://www.w3.org/2000/svg"></svg>';
    if (plan.left.length) {
      const span = plan.left.length;
      const rightStart = columns.medal + 1;
      html += `<div class="zone za" style="grid-column:1 / span ${span};grid-row:2 / span 2"></div>`;
      html += `<div class="zone zb" style="grid-column:1 / span ${span};grid-row:5 / span 2"></div>`;
      html += `<div class="zone zc" style="grid-column:${rightStart} / span ${span};grid-row:2 / span 2"></div>`;
      html += `<div class="zone zd" style="grid-column:${rightStart} / span ${span};grid-row:5 / span 2"></div>`;
    }

    plan.keys.forEach((key, i) => {
      const head = headOf[key];
      html += `<div class="ch ${head[1]}" style="grid-area:1/${i + 1}">${escapeHtml(head[0])}</div>`;
    });

    const hasAnyQual = !!(state.qualMap && Object.keys(state.qualMap).length);
    bracket.classList.toggle('ko-bracket--has-qual', hasAnyQual);

    const r16H = splitHalfIndices(R);
    R.forEach((m, i) => {
      if (!hasMatch(m)) return;
      const upper = r16H.upper.includes(i);
      const col = upper ? columns.r16L : columns.r16R;
      const row = r16RowForIndex(i, R.length);
      const qual = state.qualMap[String(i)];
      const edge = upper ? ' ko-side-l' : ' ko-side-r';
      const side = (qual ? (upper ? ' has-qual ko-qual-outer-l' : ' has-qual ko-qual-outer-r') : '') + edge;
      html += `<div class="ko-slot s-r16${side}" data-slot="${i}" style="grid-row:${row};grid-column:${col}">${firstRoundInnerHtml('r16', i, m, '', qual, upper)}</div>`;
    });

    const qfH = splitHalfIndices(Q);
    Q.forEach((m, i) => {
      if (!hasMatch(m)) return;
      const upper = qfH.upper.includes(i);
      const col = upper ? columns.qfL : columns.qfR;
      const row = qfRowForIndex(i, Q.length);
      const qual = plan.hasR16 ? null : state.qualMap[String(i)];
      const edge = upper ? ' ko-side-l' : ' ko-side-r';
      const side = (qual ? (upper ? ' has-qual ko-qual-outer-l' : ' has-qual ko-qual-outer-r') : '') + edge;
      html += `<div class="ko-slot s-qf${side}" style="grid-row:${row}/span 2;grid-column:${col}">${firstRoundInnerHtml('qf', i, m, L.qf, qual, upper)}</div>`;
    });

    const sfH = splitHalfIndices(S);
    const qfHasMatch = Q.some(hasMatch);
    S.forEach((m, i) => {
      if (!hasMatch(m)) return;
      const upper = sfH.upper.includes(i);
      const col = upper ? columns.sfL : columns.sfR;
      const qual = (!plan.hasR16 && !qfHasMatch) ? state.qualMap[String(i)] : null;
      const edge = upper ? ' ko-side-l' : ' ko-side-r';
      const side = (qual ? (upper ? ' has-qual ko-qual-outer-l' : ' has-qual ko-qual-outer-r') : '') + edge;
      html += `<div class="ko-slot s-sf${side}" style="grid-row:2/span 5;grid-column:${col}">${firstRoundInnerHtml('sf', i, m, sfHalfLabel(m, upper ? 0 : 1), qual, upper)}</div>`;
    });

    html += `<div class="ko-center-medals" style="grid-column:${columns.medal};grid-row:2/span 5">`;
    if (hasSlot(T)) {
      html += `<div class="ko-slot s-third">${matchCardHtml('third', 0, T, L.third)}</div>`;
    }
    if (hasSlot(F)) {
      html += `<div class="ko-slot s-fin">${matchCardHtml('final', 0, F, L.fin)}</div>`;
    }
    html += '</div>';
    html += `<div class="ko-podium-block">${buildPodiumHtml(state)}</div>`;

    bracket.innerHTML = html;
  }

  function layoutDesktopBracket(root, state) {
    const wrap = root.querySelector('.bracket-wrap');
    const bracket = root.querySelector('.bracket');
    if (!wrap || !bracket) return;
    renderDesktopBracket(bracket, state);
    resetDesktopCardVars(bracket);
    positionSemi(bracket);
    centerSemisInHalves(bracket);
    positionCenterMedals(bracket);
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

  function positionCenterMedals(br) {
    const stack = br.querySelector('.ko-center-medals');
    if (!stack) return;
    br.style.transform = 'none';
    stack.style.marginTop = '0px';
    stack.style.alignSelf = 'start';
    const medalCards = [...stack.querySelectorAll('.ko-mcard')];
    const sfCards = [...br.querySelectorAll('.ko-slot.s-sf .ko-mcard')];
    if (!medalCards.length || !sfCards.length) return;
    const base = br.getBoundingClientRect();
    const k = base.width / (br.offsetWidth || 1) || 1;
    let mMin = Infinity;
    let mMax = -Infinity;
    medalCards.forEach((el) => {
      const y = yRel(el, base, k);
      mMin = Math.min(mMin, y.t);
      mMax = Math.max(mMax, y.b);
    });
    let sMin = Infinity;
    let sMax = -Infinity;
    sfCards.forEach((el) => {
      const y = yRel(el, base, k);
      sMin = Math.min(sMin, y.t);
      sMax = Math.max(sMax, y.b);
    });
    if (!Number.isFinite(mMin) || !Number.isFinite(sMin)) return;
    stack.style.marginTop = `${Math.round((sMin + sMax) / 2 - (mMin + mMax) / 2)}px`;
  }

  function yRel(el, base, k) {
    const r = el.getBoundingClientRect();
    return { t: (r.top - base.top) / k, b: (r.bottom - base.top) / k };
  }

  function centerSemisInHalves(br) {
    const sfSlots = [...br.querySelectorAll('.ko-slot.s-sf')];
    const sfCards = [...br.querySelectorAll('.ko-slot.s-sf .ko-mcard')];
    const zones = [...br.querySelectorAll('.zone')];
    if (!sfSlots.length || !sfCards.length) return;
    br.style.transform = 'none';
    const base = br.getBoundingClientRect();
    const k = base.width / (br.offsetWidth || 1) || 1;

    let cMin = Infinity;
    let cMax = -Infinity;
    sfCards.forEach((el) => {
      const y = yRel(el, base, k);
      cMin = Math.min(cMin, y.t);
      cMax = Math.max(cMax, y.b);
    });

    let mMin = Infinity;
    let mMax = -Infinity;
    const modules = zones.length
      ? zones
      : [...br.querySelectorAll('.ko-slot.s-qf .ko-mcard, .ko-slot.s-r16 .ko-mcard')];
    modules.forEach((el) => {
      const r = el.getBoundingClientRect();
      if (!r.height) return;
      const y = yRel(el, base, k);
      mMin = Math.min(mMin, y.t);
      mMax = Math.max(mMax, y.b);
    });
    if (!Number.isFinite(cMin) || !Number.isFinite(mMin)) return;

    const delta = Math.round((mMin + mMax) / 2 - (cMin + cMax) / 2);
    if (!delta) return;
    sfSlots.forEach((slot) => {
      const mt = parseFloat(slot.style.marginTop) || 0;
      slot.style.marginTop = `${mt + delta}px`;
    });
  }

  function desktopAvailWidth(wrap) {
    const parent = wrap.parentElement;
    return Math.max(wrap.clientWidth - 8, parent ? parent.clientWidth - 24 : 0, 280);
  }

  function resetDesktopCardVars(br) {
    br.style.removeProperty('--ko-card-w');
    br.style.removeProperty('--ko-qual-w');
    br.style.removeProperty('--ko-gap');
  }

  function fitBracket(wrap, br, opts = {}) {
    br.style.transform = 'none';
    if (opts.export) {
      resetDesktopCardVars(br);
      const natH = br.offsetHeight;
      wrap.style.height = Math.ceil((natH || 320) + 12) + 'px';
      wrap.style.overflow = 'visible';
      wrap.classList.remove('ko-bracket-wrap--scroll');
      return;
    }
    const avail = desktopAvailWidth(wrap);
    const natW = br.offsetWidth;
    const natH = br.offsetHeight;
    if (!natW || !natH) {
      wrap.style.height = '320px';
      return;
    }
    const narrow = window.innerWidth < 768;
    let s = (avail - 2) / natW;
    if (!Number.isFinite(s) || s <= 0) {
      s = narrow ? 0.62 : 0.85;
    }
    if (narrow) {
      s = Math.max(s, 0.62);
    } else {
      s = 1;
    }
    const scaledW = natW * s;
    const off = narrow && scaledW > avail ? 0 : Math.max(0, (avail - scaledW) / 2);
    br.style.transform = narrow ? `translateX(${off}px) scale(${s})` : 'none';
    wrap.style.height = Math.ceil(natH * (narrow ? s : 1) + 8) + 'px';
    wrap.style.overflow = narrow ? '' : 'hidden';
    wrap.classList.toggle('ko-bracket-wrap--scroll', narrow && scaledW > avail + 2);
    const root = wrap.closest('.ko-bracket-root');
    if (root) root.classList.toggle('ko-bracket-root--narrow', narrow);
  }

  function drawWires(br, state, opts = {}) {
    const svg = br.querySelector('[data-ko-wires]');
    if (!svg) return;
    // 优先用 offset 链（打印预览/PDF 引擎对 getBoundingClientRect 常不准）
    const box = (el) => offsetBoxIn(br, el);
    const W = Math.max(br.offsetWidth, Math.ceil(br.scrollWidth) || 0);
    const H = Math.max(br.offsetHeight, Math.ceil(br.scrollHeight) || 0);
    if (!W || !H) return;
    svg.setAttribute('width', W);
    svg.setAttribute('height', H);
    svg.setAttribute('viewBox', `0 0 ${W} ${H}`);
    svg.style.width = W + 'px';
    svg.style.height = H + 'px';

    const HEAD = 7.5;
    const R = 8;
    const out = [];
    const ortho = !!opts.ortho;

    const elbow = (sx, sy, tx, ty, dir) => {
      const ex = tx - dir * HEAD;
      if (Math.abs(ty - sy) < 1.2) return `M ${sx} ${sy} H ${ex}`;
      const span = Math.abs(tx - sx);
      const mx = sx + dir * Math.max(R + 4, Math.min(28, span * 0.55));
      // PDF/打印引擎对 Q 曲线易断成漂浮线段；导出用直角折线，网页仍可用圆角肘线
      if (ortho) {
        return `M ${sx} ${sy} H ${mx} V ${ty} H ${ex}`;
      }
      const vdir = ty > sy ? 1 : -1;
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

      if (slot === 'va' || slot === 'vb' || slot === 'ta' || slot === 'tb') {
        const left = slot === 'va' || slot === 'ta';
        const tRow = tRows[left ? 0 : 1] || tRows[0];
        const ty = tRow ? box(tRow).cy : t.cy;
        const dir = left ? 1 : -1;
        const tx = left ? t.l - 1 : t.rt + 1;
        const sx = left ? s.rt : s.l;
        const fromY = slot === 'va' || slot === 'vb' ? sy : s.cy;
        // 始终用肘线：上下半区与奖牌区错位时，竖线+横线容易画进空白
        out.push(`<path class="wire ${cls}" d="${elbow(sx, fromY, tx, ty, dir)}"/>`);
        return;
      }

      const dir = t.l > s.rt ? 1 : -1;
      const sx = dir > 0 ? s.rt : s.l;
      const tx = dir > 0 ? t.l - 1 : t.rt + 1;
      const tRow = tRows[slot === 'b' ? 1 : 0] || tRows[0];
      const ty = tRow ? box(tRow).cy : t.cy;
      // 同源同列却目标在上/下方时也走肘线，避免「竖线扎进空白」
      out.push(`<path class="wire ${cls}" d="${elbow(sx, sy, tx, ty, dir)}"/>`);
    });

    svg.innerHTML = out.join('');
  }

  /** 相对 bracket 根的盒子坐标（offset 累加，避免打印态 getBoundingClientRect 错位） */
  function offsetBoxIn(root, el) {
    if (!root || !el) return { l: 0, rt: 0, t: 0, b: 0, cx: 0, cy: 0 };
    const rootRect = root.getBoundingClientRect();
    const r = el.getBoundingClientRect();
    const k = rootRect.width / (root.offsetWidth || 1) || 1;
    // 打印预览时若 rect 退化，退回 offsetParent 累加
    if (!r.width && !r.height) {
      let l = 0;
      let t = 0;
      let node = el;
      while (node && node !== root) {
        l += node.offsetLeft || 0;
        t += node.offsetTop || 0;
        node = node.offsetParent;
        if (node === root || (node && root.contains(node) && node.offsetParent === null)) break;
        if (!node || !root.contains(node)) break;
      }
      const w = el.offsetWidth || 0;
      const h = el.offsetHeight || 0;
      return { l, rt: l + w, t, b: t + h, cx: l + w / 2, cy: t + h / 2 };
    }
    const l = (r.left - rootRect.left) / k;
    const t = (r.top - rootRect.top) / k;
    const w = r.width / k;
    const h = r.height / k;
    return { l, rt: l + w, t, b: t + h, cx: l + w / 2, cy: t + h / 2 };
  }

  function fitNames(br) {
    const mobile = !!(br.classList.contains('ko-bracket-mobile')
      || br.closest('.ko-bracket-root')?.classList.contains('ko-layout-mobile'));
    const pdf = !!br.closest('.ko-pdf-simple, .ko-bracket-export-root');
    br.querySelectorAll('.entrant .nm, .pd-col .pd-nm').forEach((nm) => {
      nm.style.whiteSpace = 'nowrap';
      nm.style.display = '';
      nm.style.webkitLineClamp = '';
      if (nm.scrollWidth > nm.clientWidth + 0.5) {
        // 窄屏 / 导出：允许两行，避免「阿…」看不清
        if (mobile || pdf) {
          nm.style.whiteSpace = 'normal';
          nm.style.display = '-webkit-box';
          nm.style.webkitBoxOrient = 'vertical';
          nm.style.webkitLineClamp = '2';
          nm.style.overflow = 'hidden';
        }
      }
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

  function exportMetaFromRoot(root) {
    const attr = (...keys) => {
      for (const k of keys) {
        const v = (root.getAttribute(k) || '').trim();
        if (v) return v;
      }
      return '';
    };
    const season = attr('data-season-label', 'data-export-season');
    const series = attr('data-series-label', 'data-export-series');
    const level = attr('data-level-label', 'data-export-level');
    const editionRaw = attr('data-edition-label', 'data-export-edition');
    const edition = editionRaw && /^\d+$/.test(editionRaw) ? `第${editionRaw}届` : editionRaw;
    const logo = attr('data-export-logo') || '/images/Logo%20Trsp%20Stripe.png';
    return { season, series, level, edition, logo };
  }

  function buildExportHeadline(root) {
    const m = exportMetaFromRoot(root);
    const parts = [];
    if (m.season) parts.push(m.season);
    if (m.series) parts.push(m.series);
    const eventBits = [];
    if (m.level) eventBits.push(m.level);
    if (m.edition) eventBits.push(m.edition);
    if (eventBits.length) parts.push(eventBits.join(' · '));
    parts.push('淘汰赛对阵图');
    return parts.join(' · ');
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

  function buildBracketText(state, title, metaInfo) {
    const L = state.labels;
    const lines = [];
    lines.push(title || '淘汰赛对阵图');
    if (metaInfo?.season) lines.push(`赛季：${metaInfo.season}`);
    if (metaInfo?.series) lines.push(`系列：${metaInfo.series}`);
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
    const text = buildBracketText(state, buildExportHeadline(root) || exportTitle(root), exportMetaFromRoot(root));
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

  function buildPrintStageHtml(opts) {
    const title = opts.title || '淘汰赛对阵图';
    const meta = opts.meta || '';
    const season = opts.season || '';
    const series = opts.series || '';
    const logo = opts.logo || '/images/Logo%20Trsp%20Stripe.png';
    const subParts = [];
    if (season) subParts.push(`赛季：${season}`);
    if (series) subParts.push(`系列：${series}`);
    if (meta) subParts.push(meta);
    const subLine = subParts.join('  ·  ');
    return `
      <div class="ko-bracket-print-stage">
        <div class="ko-bracket-export-hd meta">
          <img class="logo" src="${escapeHtml(logo)}" alt="Curling Masters Logo"/>
          <div class="ko-bracket-export-hd-text">
            <h2>${escapeHtml(title)}</h2>
            <p class="ko-bracket-export-meta">${escapeHtml(subLine)}</p>
          </div>
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
    resetDesktopCardVars(bracket);
    positionSemi(bracket);
    centerSemisInHalves(bracket);
    positionCenterMedals(bracket);
    fitBracket(wrap, bracket, { export: true });
    // 导出用直角折线（拓扑与网页一致；避免 Q 曲线在 PDF 引擎里断成漂浮线段）
    drawWires(bracket, state, { ortho: true });
    fitNames(bracket);
    host.querySelectorAll('[style]').forEach((el) => {
      const s = el.getAttribute('style') || '';
      if (/gradient|color-mix/i.test(s)) el.removeAttribute('style');
    });
  }

  function redrawExportWires(exportRoot, state) {
    const bracket = exportRoot && exportRoot.querySelector('.bracket');
    if (!bracket || !state) return;
    positionSemi(bracket);
    centerSemisInHalves(bracket);
    positionCenterMedals(bracket);
    drawWires(bracket, state, { ortho: true });
  }

  function cleanupPrintStage(stage, btn, prevTitle, unbind) {
    if (typeof unbind === 'function') unbind();
    document.body.classList.remove('ko-bracket-printing');
    if (prevTitle != null) document.title = prevTitle;
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

    const info = exportMetaFromRoot(root);
    const title = buildExportHeadline(root) || exportTitle(root);
    const total = state.progress.total || 0;
    const done = state.progress.done || 0;
    const meta = total > 0
        ? `进度 ${done}/${total} 场已决出 · 导出时间 ${new Date().toLocaleString('zh-CN')}`
        : `导出时间 ${new Date().toLocaleString('zh-CN')}`;

    document.getElementById('ko-bracket-print-stage')?.remove();
    const stage = document.createElement('div');
    stage.id = 'ko-bracket-print-stage';
    stage.innerHTML = buildPrintStageHtml({
      title,
      meta,
      season: info.season,
      series: info.series,
      logo: info.logo,
    });
    document.body.appendChild(stage);

    const exportRoot = stage.querySelector('.ko-bracket-export-root');
    const prevTitle = document.title;
    document.title = title;

    // 先进入打印态再量坐标，避免屏外布局与打印 CSS 不一致导致连线漂浮
    document.body.classList.add('ko-bracket-printing');
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
    layoutDesktopBracketExport(exportRoot, state);
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
    redrawExportWires(exportRoot, state);

    if (typeof global.showAlert === 'function') {
      global.showAlert('请在打印窗口选择「横向 / Landscape」，目标可选「Microsoft Print to PDF」或「WPS PDF」', 'info');
    }

    const onBeforePrint = () => redrawExportWires(exportRoot, state);
    const onAfterPrint = () => cleanupPrintStage(stage, btn, prevTitle, unbind);
    const unbind = () => {
      window.removeEventListener('beforeprint', onBeforePrint);
      window.removeEventListener('afterprint', onAfterPrint);
    };
    window.addEventListener('beforeprint', onBeforePrint);
    window.addEventListener('afterprint', onAfterPrint);

    setTimeout(() => {
      try {
        redrawExportWires(exportRoot, state);
        global.print();
      } catch (e) {
        console.error(e);
        cleanupPrintStage(stage, btn, prevTitle, unbind);
        if (typeof global.showAlert === 'function') global.showAlert('无法打开打印对话框', 'danger');
        return;
      }
      setTimeout(() => {
        if (document.getElementById('ko-bracket-print-stage')) {
          cleanupPrintStage(stage, btn, prevTitle, unbind);
        }
      }, 8000);
    }, 120);
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
        if (root.__koFitting) return;
        const cached = root.__koState;
        if (!cached) return;
        root.__koFitting = true;
        try {
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
          resetDesktopCardVars(br);
          positionSemi(br);
          centerSemisInHalves(br);
          positionCenterMedals(br);
          fitBracket(wrap, br);
          drawWires(br, cached);
          fitNames(br);
        } finally {
          root.__koFitting = false;
        }
      };
      root.__koLayoutMode = useMobileBracketLayout() ? 'mobile' : 'desktop';
      root.__koReflow = onResize;
      window.addEventListener('resize', () => {
        clearTimeout(root.__koRsz);
        root.__koRsz = setTimeout(onResize, 120);
      });
      window.addEventListener('scroll', () => {
        if (useMobileBracketLayout()) return;
        clearTimeout(root.__koScr);
        root.__koScr = setTimeout(onResize, 80);
      }, { passive: true });
      if (window.visualViewport) {
        window.visualViewport.addEventListener('resize', () => {
          clearTimeout(root.__koRsz);
          root.__koRsz = setTimeout(onResize, 80);
        });
      }
      root.dataset.koInited = '1';
      const scheduleReflow = (attempt) => {
        requestAnimationFrame(() => {
          onResize();
          if (root.__koLayoutMode === 'mobile') return;
          if (attempt < 10) scheduleReflow(attempt + 1);
        });
      };
      scheduleReflow(0);
      setTimeout(onResize, 220);
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

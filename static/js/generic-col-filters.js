/**
 * 通用表头多选复选框筛选（与 generic-list 宽屏列筛选同款交互）。
 * 用法：GenericColFilters.init({ table, rows, cards?, searchInput?, skipCols?, multiFilterCols?, rowSearchMatch?, onApply? })
 */
(function (global) {
    'use strict';

    const MD = 768;

    function norm(s) {
        return (s || '').toString().toLowerCase().replace(/\s+/g, ' ').trim();
    }

    function init(options) {
        if (!options || !options.table) return null;
        const table = typeof options.table === 'string'
            ? document.querySelector(options.table)
            : options.table;
        if (!table) return null;

        const rows = Array.from(
            options.rows
                ? (typeof options.rows === 'string' ? document.querySelectorAll(options.rows) : options.rows)
                : table.querySelectorAll('tbody tr')
        );
        const cards = Array.from(
            options.cards
                ? (typeof options.cards === 'string' ? document.querySelectorAll(options.cards) : options.cards)
                : []
        );
        const searchInput = options.searchInput
            ? (typeof options.searchInput === 'string'
                ? document.getElementById(options.searchInput) || document.querySelector(options.searchInput)
                : options.searchInput)
            : null;
        const skipCols = new Set((options.skipCols || []).map(String));
        const multiFilterCols = new Set((options.multiFilterCols || []).map(String));
        const rowSearchMatch = typeof options.rowSearchMatch === 'function'
            ? options.rowSearchMatch
            : null;
        const onApply = typeof options.onApply === 'function' ? options.onApply : null;
        const isWide = () => window.innerWidth >= MD;

        const colCount = () => table.querySelectorAll('thead tr:first-child th').length;

        const actionColIndex = () => {
            const ths = Array.from(table.querySelectorAll('thead tr:first-child th'));
            const last = ths[ths.length - 1];
            if (last && (last.textContent || '').trim() === '操作') return ths.length - 1;
            return -1;
        };

        const cellText = (tr, colIdx) => {
            if (!tr || colIdx == null) return '';
            const td = tr.querySelector('td[data-col="' + colIdx + '"]') || tr.children[colIdx];
            return td ? (td.textContent || '').replace(/\s+/g, ' ').trim() : '';
        };

        const rowLevels = (tr) => {
            const raw = (tr.getAttribute('data-levels') || '').trim();
            if (!raw) return [];
            return raw.split('|').map((s) => s.trim()).filter(Boolean);
        };

        const rowTokensForCol = (tr, col) => {
            if (multiFilterCols.has(String(col))) {
                return rowLevels(tr);
            }
            const t = cellText(tr, col);
            return t ? [t] : [];
        };

        const getMultiFilterMenu = (wrap) => {
            if (!wrap) return null;
            if (wrap._gfMenu && document.contains(wrap._gfMenu)) return wrap._gfMenu;
            const col = wrap.dataset.col;
            return wrap.querySelector('.generic-multi-filter-menu')
                || document.querySelector('body > .generic-multi-filter-menu[data-gf-col="' + col + '"]');
        };

        const updateMultiFilterButton = (wrap) => {
            const btn = wrap.querySelector('.generic-col-filter-multi-btn');
            if (!btn) return;
            const menu = getMultiFilterMenu(wrap);
            const checked = menu
                ? Array.from(menu.querySelectorAll('.generic-multi-filter-options input[type="checkbox"]:checked'))
                    .map((c) => c.value)
                    .filter(Boolean)
                : [];
            const mode = (wrap.dataset.mode || 'and') === 'or' ? '或' : '和';
            if (!checked.length) {
                btn.textContent = '全部';
                btn.title = '全部（多选默认「' + mode + '」）';
                return;
            }
            const label = checked.length <= 2
                ? checked.join('、')
                : (checked.slice(0, 2).join('、') + ' +' + (checked.length - 2));
            btn.textContent = label + ' · ' + mode;
            btn.title = checked.join('、') + '（' + mode + '）';
        };

        const createMultiFilter = (colIdx) => {
            const wrap = document.createElement('div');
            wrap.className = 'dropdown generic-multi-filter';
            wrap.dataset.col = String(colIdx);
            wrap.dataset.mode = 'and';
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn btn-sm dropdown-toggle w-100 text-start generic-col-filter-multi-btn';
            btn.setAttribute('data-bs-toggle', 'dropdown');
            btn.setAttribute('data-bs-auto-close', 'outside');
            btn.setAttribute('data-bs-popper-config', JSON.stringify({ strategy: 'fixed' }));
            btn.setAttribute('aria-expanded', 'false');
            btn.setAttribute('aria-label', '多选筛选');
            btn.textContent = '全部';
            const menu = document.createElement('div');
            menu.className = 'dropdown-menu p-2 generic-multi-filter-menu';
            menu.dataset.gfCol = String(colIdx);
            wrap._gfMenu = menu;
            menu.addEventListener('click', (e) => e.stopPropagation());
            wrap.addEventListener('show.bs.dropdown', () => {
                if (menu.parentElement !== document.body) {
                    document.body.appendChild(menu);
                }
            });
            wrap.addEventListener('hidden.bs.dropdown', () => {
                if (menu.parentElement === document.body) {
                    wrap.appendChild(menu);
                }
            });

            const modeBox = document.createElement('div');
            modeBox.className = 'generic-multi-filter-mode mb-2';
            const modeLabel = document.createElement('div');
            modeLabel.className = 'small text-muted mb-1';
            modeLabel.textContent = '多选关系';
            const modeGroup = document.createElement('div');
            modeGroup.className = 'btn-group btn-group-sm w-100';
            modeGroup.setAttribute('role', 'group');
            const andId = 'gf-mode-and-' + colIdx + '-' + Math.random().toString(36).slice(2, 7);
            const orId = 'gf-mode-or-' + colIdx + '-' + Math.random().toString(36).slice(2, 7);
            const andInput = document.createElement('input');
            andInput.type = 'radio';
            andInput.className = 'btn-check';
            andInput.name = 'gf-mode-' + andId;
            andInput.id = andId;
            andInput.value = 'and';
            andInput.checked = true;
            const andLab = document.createElement('label');
            andLab.className = 'btn btn-outline-secondary';
            andLab.htmlFor = andId;
            andLab.textContent = '和';
            const orInput = document.createElement('input');
            orInput.type = 'radio';
            orInput.className = 'btn-check';
            orInput.name = 'gf-mode-' + andId;
            orInput.id = orId;
            orInput.value = 'or';
            const orLab = document.createElement('label');
            orLab.className = 'btn btn-outline-secondary';
            orLab.htmlFor = orId;
            orLab.textContent = '或';
            const onModeChange = () => {
                wrap.dataset.mode = andInput.checked ? 'and' : 'or';
                updateMultiFilterButton(wrap);
                apply();
            };
            andInput.addEventListener('change', onModeChange);
            orInput.addEventListener('change', onModeChange);
            modeGroup.appendChild(andInput);
            modeGroup.appendChild(andLab);
            modeGroup.appendChild(orInput);
            modeGroup.appendChild(orLab);
            modeBox.appendChild(modeLabel);
            modeBox.appendChild(modeGroup);

            const options = document.createElement('div');
            options.className = 'generic-multi-filter-options';
            menu.appendChild(modeBox);
            menu.appendChild(options);
            wrap.appendChild(btn);
            wrap.appendChild(menu);
            return wrap;
        };

        const ensureHeaderFilters = () => {
            if (!isWide()) return;
            if (table.querySelector('.generic-col-filters')) return;
            const n = colCount();
            const skipAction = actionColIndex();
            const tr = document.createElement('tr');
            tr.className = 'generic-col-filters';
            for (let i = 0; i < n; i++) {
                const th = document.createElement('th');
                if (i !== skipAction && !skipCols.has(String(i))) {
                    th.appendChild(createMultiFilter(i));
                }
                tr.appendChild(th);
            }
            const thead = table.querySelector('thead');
            if (thead) thead.appendChild(tr);
        };

        const searchKw = () => (searchInput ? norm(searchInput.value) : '');

        const colFilters = () => {
            const out = {};
            table.querySelectorAll('.generic-multi-filter').forEach((wrap) => {
                const menu = getMultiFilterMenu(wrap);
                if (!menu) return;
                const values = Array.from(menu.querySelectorAll('.generic-multi-filter-options input[type="checkbox"]:checked'))
                    .map((c) => (c.value || '').trim())
                    .filter(Boolean);
                if (values.length) {
                    out[wrap.dataset.col] = {
                        values,
                        mode: (wrap.dataset.mode || 'and') === 'or' ? 'or' : 'and'
                    };
                }
            });
            return out;
        };

        const tokensMatch = (tokens, selected, mode) => {
            if (!selected || !selected.length) return true;
            const set = tokens || [];
            if (mode === 'or') {
                return selected.some((v) => set.indexOf(v) >= 0);
            }
            return selected.every((v) => set.indexOf(v) >= 0);
        };

        const rowMatches = (tr, exceptCol) => {
            const kw = searchKw();
            if (kw) {
                if (rowSearchMatch) {
                    if (!rowSearchMatch(tr, kw, searchInput ? searchInput.value.trim() : '')) return false;
                } else {
                    const text = norm(tr.textContent);
                    const meta = norm(tr.getAttribute('data-filters') || '');
                    if (!(text + ' ' + meta).includes(kw)) return false;
                }
            }
            if (!isWide()) return true;
            const filters = colFilters();
            for (const [col, spec] of Object.entries(filters)) {
                if (exceptCol != null && String(exceptCol) === String(col)) continue;
                if (!spec) continue;
                if (!tokensMatch(rowTokensForCol(tr, col), spec.values, spec.mode)) return false;
            }
            return true;
        };

        const cardMatches = (card) => {
            const uid = card.getAttribute('data-user-id');
            if (uid) {
                const tr = rows.find((r) => String(r.getAttribute('data-user-id')) === String(uid));
                if (tr) return rowMatches(tr, null);
            }
            const idx = cards.indexOf(card);
            if (idx >= 0 && rows[idx]) return rowMatches(rows[idx], null);
            const kw = searchKw();
            if (!kw) return true;
            if (rowSearchMatch) return rowSearchMatch(card, kw, searchInput ? searchInput.value.trim() : '');
            return norm(card.textContent).includes(kw);
        };

        const refreshFilterOptions = () => {
            if (!isWide()) return;
            table.querySelectorAll('.generic-multi-filter').forEach((wrap) => {
                const col = wrap.dataset.col;
                const menu = getMultiFilterMenu(wrap);
                const options = menu ? menu.querySelector('.generic-multi-filter-options') : null;
                if (!menu || !options) return;
                const prevChecked = new Set(
                    Array.from(options.querySelectorAll('input[type="checkbox"]:checked'))
                        .map((c) => c.value)
                );
                const mode = (wrap.dataset.mode || 'and') === 'or' ? 'or' : 'and';
                wrap.dataset.mode = mode;
                const andInput = menu.querySelector('input[value="and"]');
                const orInput = menu.querySelector('input[value="or"]');
                if (andInput) andInput.checked = mode === 'and';
                if (orInput) orInput.checked = mode === 'or';

                const set = new Set();
                rows.forEach((tr) => {
                    if (!rowMatches(tr, col)) return;
                    rowTokensForCol(tr, col).forEach((tok) => set.add(tok));
                });
                const values = Array.from(set).sort((a, b) => a.localeCompare(b, 'zh'));
                options.replaceChildren();
                if (!values.length) {
                    const empty = document.createElement('div');
                    empty.className = 'text-muted small px-1';
                    empty.textContent = '无可选项';
                    options.appendChild(empty);
                } else {
                    values.forEach((v, idx) => {
                        const id = 'gf-tok-' + col + '-' + idx;
                        const row = document.createElement('div');
                        row.className = 'form-check';
                        const cb = document.createElement('input');
                        cb.type = 'checkbox';
                        cb.className = 'form-check-input';
                        cb.id = id;
                        cb.value = v;
                        if (prevChecked.has(v)) cb.checked = true;
                        cb.addEventListener('change', () => {
                            updateMultiFilterButton(wrap);
                            apply();
                        });
                        const lab = document.createElement('label');
                        lab.className = 'form-check-label';
                        lab.htmlFor = id;
                        lab.textContent = v;
                        lab.title = v;
                        row.appendChild(cb);
                        row.appendChild(lab);
                        options.appendChild(row);
                    });
                }
                updateMultiFilterButton(wrap);
            });
        };

        const apply = () => {
            ensureHeaderFilters();
            refreshFilterOptions();
            let visible = 0;
            rows.forEach((tr) => {
                const ok = rowMatches(tr, null);
                tr.classList.toggle('d-none', !ok);
                tr.style.display = '';
                if (ok) visible++;
            });
            cards.forEach((card) => {
                const ok = cardMatches(card);
                card.classList.toggle('d-none', !ok);
                card.style.display = '';
            });
            if (onApply) {
                onApply({
                    visibleCount: visible,
                    totalCount: rows.length,
                    keyword: searchInput ? searchInput.value.trim() : ''
                });
            }
        };

        if (searchInput) {
            let timer = null;
            searchInput.addEventListener('input', () => {
                clearTimeout(timer);
                timer = setTimeout(apply, 300);
            });
        }
        window.addEventListener('resize', () => {
            apply();
        });
        apply();
        return { apply };
    }

    global.GenericColFilters = { init };
})(window);

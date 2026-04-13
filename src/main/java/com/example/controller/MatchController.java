package com.example.controller;

import com.example.entity.*;
import com.example.service.*;
import com.example.util.IpAddressUtil;
import com.example.util.HtmlEscaper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/match")
public class MatchController {

    @Autowired private IMatchService matchService;
    @Autowired private TournamentService tournamentService;
    @Autowired private SeriesService seriesService;
    @Autowired private SeasonService seasonService;
    @Autowired private ISetScoreService setScoreService;
    @Autowired private UserService userService;
    @Autowired private ITournamentCompetitionService tournamentCompetitionService;

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            User user = userService.findByUsername(username);
            return user != null && user.getRole() <= 1;
        }
        return false;
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            User user = userService.findByUsername(username);
            return user != null && user.getRole() != null && user.getRole() == 0;
        }
        return false;
    }
    
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return userService.findByUsername(auth.getName());
        }
        return null;
    }
    
    private boolean isHostUser(Long tournamentId) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return false;
        Tournament tournament = tournamentService.getById(tournamentId);
        return tournament != null && tournament.getHostUserId().equals(currentUser.getId());
    }
    
    private boolean canManageMatch(Long tournamentId) {
        return isAdmin() || isHostUser(tournamentId);
    }

    @GetMapping("/list")
    public String matchList(@RequestParam(required = false) Long tournamentId,
                           Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        List<Match> matches;
        if (tournamentId != null) {
            matches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .orderByAsc(Match::getRound)
                .list();
        } else {
            matches = matchService.list();
        }
        
        // 修复：使用Map来存储额外信息，而不是直接调用不存在的set方法
        List<Map<String, Object>> matchInfoList = new ArrayList<>();
        for (Match m : matches) {
            Map<String, Object> matchInfo = new HashMap<>();
            matchInfo.put("match", m);
            
            Tournament tournament = tournamentService.getById(m.getTournamentId());
            if (tournament != null) {
                Series series = seriesService.getById(tournament.getSeriesId());
                Season season = series != null ? seasonService.getById(series.getSeasonId()) : null;
                matchInfo.put("tournamentName", (season != null ? season.getYear() + "年" : "") + 
                    (tournament.getLevelCode() != null ? tournament.getLevelCode() : ""));
                matchInfo.put("seriesId", tournament.getSeriesId());
                matchInfo.put("isHost", isHostUser(tournament.getId()));
            }
            User player1 = userService.getById(m.getPlayer1Id());
            User player2 = userService.getById(m.getPlayer2Id());
            matchInfo.put("player1Name", player1 != null ? player1.getUsername() : "待定");
            matchInfo.put("player2Name", player2 != null ? player2.getUsername() : "待定");
            
            matchInfoList.add(matchInfo);
        }
        
        User currentUser = getCurrentUser();
        boolean admin = isAdmin();
        boolean superAdmin = isSuperAdmin();
        
        // 构建通用列表数据
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Map<String, Object> matchInfo : matchInfoList) {
            Match match = (Match) matchInfo.get("match");
            String tournamentName = (String) matchInfo.get("tournamentName");
            String player1Name = (String) matchInfo.get("player1Name");
            String player2Name = (String) matchInfo.get("player2Name");
            Boolean isHost = (Boolean) matchInfo.get("isHost");
            
            Map<String, Object> item = new HashMap<>();
            
            // 构建状态徽章
            String statusBadge = "";
            switch (match.getStatus()) {
                case 0:
                    statusBadge = "<span class=\"badge bg-secondary\">未开始</span>";
                    break;
                case 1:
                    statusBadge = "<span class=\"badge bg-primary\">进行中</span>";
                    break;
                case 2:
                    statusBadge = "<span class=\"badge bg-success\">已结束</span>";
                    break;
                case 3:
                    statusBadge = "<span class=\"badge bg-danger\">退赛</span>";
                    break;
            }
            
            // 构建比赛显示
            String matchDisplay = HtmlEscaper.escapeHtml(player1Name) + " vs " + HtmlEscaper.escapeHtml(player2Name);
            if (match.getWinnerId() != null && match.getWinnerId().equals(match.getPlayer1Id())) {
                matchDisplay += " <span class=\"text-success\">(" + HtmlEscaper.escapeHtml(player1Name) + "胜)</span>";
            } else if (match.getWinnerId() != null && match.getWinnerId().equals(match.getPlayer2Id())) {
                matchDisplay += " <span class=\"text-success\">(" + HtmlEscaper.escapeHtml(player2Name) + "胜)</span>";
            }
            
            // 构建时间显示
            String timeStr = "";
            if (match.getScheduledTime() != null) {
                timeStr = match.getScheduledTime().toLocalDate() + " " + match.getScheduledTime().toLocalTime();
            } else {
                timeStr = "-";
            }
            
            // 构建创建时间显示
            String createTimeStr = match.getCreateTime() != null ? 
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(match.getCreateTime()) : "-";
            
            item.put("data", Arrays.asList(
                match.getId(),
                "<strong>" + HtmlEscaper.escapeHtml(tournamentName) + "</strong>",
                match.getCategory() != null ? match.getCategory() : "-",
                match.getRound() != null ? "第" + match.getRound() + "场" : "-",
                matchDisplay,
                statusBadge,
                timeStr,
                createTimeStr
            ));
            item.put("filters", Map.of("status", match.getStatus()));
            item.put("id", match.getId());
            item.put("player1Name", player1Name);
            item.put("player2Name", player2Name);

            dataList.add(item);
        }
        
        // 构建列配置
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(Map.of("title", "ID", "type", "text"));
        columns.add(Map.of("title", "赛事", "type", "custom"));
        columns.add(Map.of("title", "类别", "type", "text"));
        columns.add(Map.of("title", "轮次", "type", "text"));
        columns.add(Map.of("title", "对阵", "type", "custom"));
        columns.add(Map.of("title", "状态", "type", "custom"));
        columns.add(Map.of("title", "预定时间", "type", "text"));
        columns.add(Map.of("title", "创建时间", "type", "text"));
        
        // 构建操作按钮
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(Map.of("urlPrefix", "/match/edit/", "btnClass", "btn btn-sm btn-outline-warning", "icon", "bi bi-pencil", "text", "编辑"));
        if (superAdmin) {
            actions.add(Map.of("urlPrefix", "/match/score/", "btnClass", "btn btn-sm btn-outline-success", "icon", "bi bi-pencil-square", "text", "录入比分"));
        }
        
        // 通用列表参数
        model.addAttribute("pageTitle", "比赛列表");
        model.addAttribute("pageIcon", "bi bi-controller");
        model.addAttribute("entityName", "比赛");
        model.addAttribute("addUrl", "/match/add" + (tournamentId != null ? "?tournamentId=" + tournamentId : ""));
        model.addAttribute("dataList", dataList);
        model.addAttribute("columns", columns);
        model.addAttribute("actions", actions);
        model.addAttribute("hasActions", true);
        model.addAttribute("emptyIcon", "bi bi-controller");
        model.addAttribute("emptyMessage", "暂无比赛数据");
        model.addAttribute("tournamentId", tournamentId);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("isSuperAdmin", superAdmin);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("customScript", """
(() => {
  let modal;
  const ensureModal = () => {
    let el = document.getElementById('matchListScoreModal');
    if (el) return el;
    el = document.createElement('div');
    el.id = 'matchListScoreModal';
    el.className = 'modal fade';
    el.tabIndex = -1;
    el.innerHTML = `
      <div class="modal-dialog modal-lg modal-dialog-scrollable">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title" id="mlModalTitle">单场比赛录分</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
          </div>
          <div class="modal-body">
            <input type="hidden" id="mlMatchId">
            <div class="mb-2 small text-muted" id="mlMatchSubTitle"></div>
            <div class="row g-2 mb-2">
              <div class="col-md-4">
                <label class="form-label">开局后手</label>
                <select class="form-select" id="mlFirstEndHammer">
                  <option value="">不设置</option>
                  <option value="1" id="mlHammerP1">player1</option>
                  <option value="2" id="mlHammerP2">player2</option>
                </select>
              </div>
              <div class="col-md-4">
                <label class="form-label">局数</label>
                <input type="number" min="1" max="20" id="mlSetCount" class="form-control" value="8">
              </div>
              <div class="col-md-4 d-flex align-items-end">
                <button type="button" class="btn btn-outline-primary w-100" id="mlRenderBtn">生成/重置局数</button>
              </div>
            </div>
            <div class="table-responsive score-entry-wrap">
              <table class="table table-sm table-bordered align-middle score-entry-table">
                <thead class="table-light"><tr><th style="width:26%">选手名称</th><th>局次</th><th style="width:18%">总比分</th></tr></thead>
                <tbody id="mlRows"></tbody>
              </table>
            </div>
            <div class="mt-2"><div class="small fw-bold">验收信息</div><div class="small text-muted" id="mlAcceptInfo">暂无</div></div>
            <div class="mt-2"><div class="small fw-bold">比分修改记录</div><div class="small text-muted" id="mlEditLogInfo">暂无</div></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">关闭</button>
            <button type="button" class="btn btn-primary" id="mlSaveBtn">保存并锁定</button>
          </div>
        </div>
      </div>`;
    document.body.appendChild(el);
    return el;
  };
  const csrf = () => { const i = document.querySelector('input[name="_csrf"]'); return i ? {name: i.name, value: i.value} : null; };
  const fetchJson = async (url) => { const r = await fetch(url); if (!r.ok) throw new Error('请求失败：' + r.status); return await r.json(); };
  const scoreTotalText = (values) => {
    if (!values || values.length === 0) return '0';
    if (values.some(v => String(v).toUpperCase() === 'X')) return 'X';
    return String(values.reduce((sum, v) => sum + (Number(v) || 0), 0));
  };
  const scoreCellTokenFromRow = (row, side) => {
    if (!row) return '0';
    const isP1 = side === 1;
    const isX = isP1
      ? (row.player1IsX === true || row.player1_is_x === true || row.player1_is_x === 1)
      : (row.player2IsX === true || row.player2_is_x === true || row.player2_is_x === 1);
    if (isX) return 'X';
    const sc = isP1 ? row.player1Score : row.player2Score;
    return String(sc ?? 0);
  };
  const buildScoreRow = (label, values, n, inputClass, totalClass) => {
    const opts = ['0','1','2','3','4','5','6','7','8','X'];
    const items = [];
    for (let i = 1; i <= n; i++) {
      const valStr = String(values[i - 1] ?? '0');
      const options = opts.map(v => `<option value="${v}" ${valStr===v?'selected':''}>${v}</option>`).join('');
      items.push(`<div class="score-end-item"><span class="score-end-label">第${i}局</span><select class="form-select form-select-sm score-input ${inputClass}">${options}</select></div>`);
    }
    return `<tr><th class="score-player-name">${label}</th><td><div class="score-ends-grid">${items.join('')}</div></td><td><span class="badge rounded-pill text-bg-light border ${totalClass}">${scoreTotalText(values)}</span></td></tr>`;
  };
  const bindScoreTotal = (tbodyId, inputClass, totalClass) => {
    const tbody = document.getElementById(tbodyId);
    if (!tbody || tbody.dataset.totalBound === '1') return;
    tbody.addEventListener('change', (e) => {
      if (!e.target.classList.contains(inputClass)) return;
      const values = Array.from(tbody.querySelectorAll(`.${inputClass}`)).map(i => i.value || '0');
      const total = tbody.querySelector(`.${totalClass}`);
      if (total) total.textContent = scoreTotalText(values);
    });
    tbody.dataset.totalBound = '1';
  };
  const renderRows = (existing) => {
    const tbody = document.getElementById('mlRows');
    const n = Number(document.getElementById('mlSetCount').value || 0);
    if (!tbody || !Number.isInteger(n) || n < 1) return;
    const p1Name = document.getElementById('mlHammerP1').textContent || 'player1';
    const p2Name = document.getElementById('mlHammerP2').textContent || 'player2';
    const p1Vals = [];
    const p2Vals = [];
    for (let i = 1; i <= n; i++) {
      p1Vals.push(existing && existing[i - 1] ? scoreCellTokenFromRow(existing[i - 1], 1) : '0');
      p2Vals.push(existing && existing[i - 1] ? scoreCellTokenFromRow(existing[i - 1], 2) : '0');
    }
    tbody.innerHTML = buildScoreRow(p1Name, p1Vals, n, 'ml-p1', 'ml-total-p1')
      + buildScoreRow(p2Name, p2Vals, n, 'ml-p2', 'ml-total-p2');
    bindScoreTotal('mlRows', 'ml-p1', 'ml-total-p1');
    bindScoreTotal('mlRows', 'ml-p2', 'ml-total-p2');
    const editable = typeof window._mlModalEditable === 'boolean' ? window._mlModalEditable : true;
    document.querySelectorAll('#mlRows .ml-p1, #mlRows .ml-p2').forEach(i => { i.disabled = !editable; });
  };
  const extractNames = (link) => {
    const tr = link.closest('tr');
    if (tr && tr.children && tr.children.length >= 5) {
      const txt = (tr.children[4].innerText || '').replace(/\\([^)]*胜\\)/g, '').trim();
      const ps = txt.split(' vs ');
      if (ps.length >= 2) return [ps[0].trim(), ps[1].trim()];
    }
    return ['player1','player2'];
  };
  const openMatchModal = async (matchId, title, names) => {
    const el = ensureModal();
    if (!modal) modal = bootstrap.Modal.getOrCreateInstance(el);
    const data = await fetchJson(`/tournament/competition/match/detail/${matchId}`);
    if (!data.ok) throw new Error(data.message || '加载失败');
    document.getElementById('mlModalTitle').textContent = title;
    document.getElementById('mlMatchId').value = String(matchId);
    document.getElementById('mlMatchSubTitle').textContent = (data.match.category || '') + ' / round ' + (data.match.round || '-');
    document.getElementById('mlHammerP1').textContent = names[0];
    document.getElementById('mlHammerP2').textContent = names[1];
    { const raw = (data.matchFirstEndHammer != null && data.matchFirstEndHammer !== '') ? data.matchFirstEndHammer : (data.match && (data.match.firstEndHammer != null ? data.match.firstEndHammer : data.match.first_end_hammer)); const s = raw == null || raw === '' ? '' : String(raw); document.getElementById('mlFirstEndHammer').value = (s === '1' || s === '2') ? s : ''; }
    const defaultSetCount = Number(data.defaultSetCount || 8);
    document.getElementById('mlSetCount').value = (data.setScores && data.setScores.length) ? data.setScores.length : defaultSetCount;
    const canEdit = data.canEditScore === true;
    window._mlModalEditable = canEdit;
    window._mlViewerIsSuperAdmin = data.viewerIsSuperAdmin === true;
    window._mlPhaseCode = (data.match && data.match.phaseCode) ? String(data.match.phaseCode).toUpperCase() : '';
    renderRows(data.setScores || []);
    document.getElementById('mlAcceptInfo').textContent = (data.acceptances || []).map(a => {
      const s = (a.signature && String(a.signature).trim()) ? String(a.signature).trim() : '';
      const sigLabel = !s ? '无签名' : (s.startsWith('data:image/') ? '[手写签名]' : s);
      return `${a.username}（${sigLabel}）@${(a.acceptedAt||'').replace('T',' ')}`;
    }).join('；') || '暂无';
    document.getElementById('mlEditLogInfo').textContent = (data.editLogs || []).slice(0, 20).map(l => `第${l.setNumber}局：${l.editorUsername} ${l.oldPlayer1IsX?'X':(l.oldPlayer1Score??0)}:${l.oldPlayer2IsX?'X':(l.oldPlayer2Score??0)} -> ${l.newPlayer1IsX?'X':(l.newPlayer1Score??0)}:${l.newPlayer2IsX?'X':(l.newPlayer2Score??0)} @${(l.editedAt||'').replace('T',' ')}`).join('；') || '暂无';
    document.getElementById('mlSaveBtn').style.display = canEdit ? '' : 'none';
    document.getElementById('mlRenderBtn').style.display = canEdit ? '' : 'none';
    document.getElementById('mlFirstEndHammer').disabled = !canEdit;
    document.getElementById('mlSetCount').disabled = !canEdit;
    modal.show();
  };
  document.addEventListener('click', async (e) => {
    const a = e.target.closest('a');
    if (!a || !a.href) return;
    const u = new URL(a.href, window.location.origin);
    try {
      if (u.pathname.startsWith('/match/score/')) {
        e.preventDefault();
        const p1 = (a.dataset.player1Name || '').trim();
        const p2 = (a.dataset.player2Name || '').trim();
        const names = (p1 || p2) ? [p1 || 'player1', p2 || 'player2'] : extractNames(a);
        await openMatchModal(u.pathname.split('/').pop(), '单场比赛录分', names);
      }
    } catch (err) { alert(err.message || '加载失败'); }
  }, true);
  document.addEventListener('click', async (e) => {
    if (e.target && e.target.id === 'mlRenderBtn') renderRows();
    if (e.target && e.target.id === 'mlSaveBtn') {
      if (!window._mlModalEditable) return;
      const matchId = document.getElementById('mlMatchId').value;
      if (!matchId) return;
      const hammer = document.getElementById('mlFirstEndHammer').value;
      const p1 = Array.from(document.querySelectorAll('.ml-p1')).map(i => i.value || '0');
      const p2 = Array.from(document.querySelectorAll('.ml-p2')).map(i => i.value || '0');
      const hasX = [...p1, ...p2].some(v => String(v).toUpperCase() === 'X');
      const phase = window._mlPhaseCode || '';
      const needSig = phase === 'QUALIFIER' || phase === 'MAIN' || phase === 'FINAL';
      const form = new URLSearchParams();
      if (hammer) form.append('firstEndHammer', hammer);
      p1.forEach(v => form.append('player1Scores', v));
      p2.forEach(v => form.append('player2Scores', v));
      if (hasX) {
        const ok = confirm('检测到X。是否验收？验收后不可再修改比分。');
        if (!ok) return;
        if (needSig) {
          // 超级管理员允许在 /match/list 中继续修改（仅留痕），因此这里跳过手写签名限制
          if (!window._mlViewerIsSuperAdmin) {
            alert('该阶段验收需手写签名，请从赛事详情页小组赛对阵卡片打开录分弹窗。');
            return;
          }
          form.append('autoAccept', 'true');
          form.append('signature', 'SUPER_ADMIN_BYPASS');
        } else {
          form.append('autoAccept', 'true');
        }
      }
      const c = csrf(); if (c) form.append(c.name, c.value);
      const res = await fetch(`/tournament/competition/match/save/${matchId}`, {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:form.toString()});
      const data = await res.json();
      if (!data.ok) return alert(data.message || '保存失败');
      window.location.reload();
    }
  });
})();
""");
        
        return "generic-list";
    }

    @GetMapping("/detail/{id}")
    public String matchDetailPage(@PathVariable Long id) {
        return "redirect:/match/score/" + id;
    }
    
    @GetMapping("/add")
    public String addMatchPage(@RequestParam Long tournamentId,
                              @RequestParam(required = false) Long seriesId,
                              Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        if (!canManageMatch(tournamentId)) {
            return "redirect:/tournament/list";
        }
        
        Tournament tournament = tournamentService.getById(tournamentId);
        if (tournament == null) {
            return "redirect:/tournament/list";
        }
        
        List<User> allUsers = userService.list();
        
        // 构建用户选项
        List<Map<String, Object>> userOptions = new ArrayList<>();
        for (User user : allUsers) {
            userOptions.add(Map.of(
                "value", user.getId(),
                "text", user.getUsername()
            ));
        }
        
        // 构建表单字段配置
        List<Map<String, Object>> fields = new ArrayList<>();
        
        fields.add(Map.of(
            "type", "hidden",
            "id", "tournamentId",
            "name", "tournamentId",
            "value", tournamentId.toString()
        ));
        
        fields.add(Map.of(
            "type", "text",
            "id", "category",
            "name", "category",
            "label", "比赛类别",
            "placeholder", "如：1000赛资格赛、1/8决赛等",
            "required", true,
            "help", "比赛的类别描述"
        ));
        
        fields.add(Map.of(
            "type", "number",
            "id", "round",
            "name", "round",
            "label", "轮次",
            "placeholder", "如：1、2、3等",
            "min", "1",
            "required", true,
            "help", "比赛轮次，用于排序"
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "player1Id",
            "name", "player1Id",
            "label", "选手1",
            "placeholder", "请选择选手1",
            "required", true,
            "help", "选择第一位选手",
            "options", userOptions
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "player2Id",
            "name", "player2Id",
            "label", "选手2",
            "placeholder", "请选择选手2",
            "required", true,
            "help", "选择第二位选手",
            "options", userOptions
        ));
        
        fields.add(Map.of(
            "type", "datetime-local",
            "id", "scheduledTime",
            "name", "scheduledTime",
            "label", "预定时间",
            "help", "比赛预定开始时间（可选）"
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "firstEndHammer",
            "name", "firstEndHammer",
            "label", "先手后手",
            "placeholder", "请选择",
            "help", "第一局谁先手",
            "options", Arrays.asList(
                Map.of("value", "1", "text", "选手1先手"),
                Map.of("value", "2", "text", "选手2先手")
            )
        ));
        
        // 通用表单参数
        model.addAttribute("pageTitle", "添加比赛");
        model.addAttribute("pageIcon", "bi bi-controller");
        model.addAttribute("saveUrl", "/match/save");
        model.addAttribute("backUrl", "/match/list?tournamentId=" + tournamentId);
        model.addAttribute("formData", new Match());
        model.addAttribute("fields", fields);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        return "generic-form";
    }
    
    @GetMapping("/edit/{id}")
    public String editMatchPage(@PathVariable Long id, Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        
        Match match = matchService.getById(id);
        if (match == null) {
            return "redirect:/match/list";
        }
        
        if (!canManageMatch(match.getTournamentId())) {
            return "redirect:/match/list?tournamentId=" + match.getTournamentId();
        }
        
        List<User> allUsers = userService.list();
        
        // 构建用户选项
        List<Map<String, Object>> userOptions = new ArrayList<>();
        for (User user : allUsers) {
            userOptions.add(Map.of(
                "value", user.getId(),
                "text", user.getUsername()
            ));
        }
        
        // 构建表单字段配置
        List<Map<String, Object>> fields = new ArrayList<>();
        
        fields.add(Map.of(
            "type", "hidden",
            "id", "tournamentId",
            "name", "tournamentId",
            "value", match.getTournamentId().toString()
        ));
        
        fields.add(Map.of(
            "type", "text",
            "id", "category",
            "name", "category",
            "label", "比赛类别",
            "placeholder", "如：1000赛资格赛、1/8决赛等",
            "required", true,
            "help", "比赛的类别描述"
        ));
        
        fields.add(Map.of(
            "type", "number",
            "id", "round",
            "name", "round",
            "label", "轮次",
            "placeholder", "如：1、2、3等",
            "min", "1",
            "required", true,
            "help", "比赛轮次，用于排序"
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "player1Id",
            "name", "player1Id",
            "label", "选手1",
            "placeholder", "请选择选手1",
            "required", true,
            "help", "选择第一位选手",
            "options", userOptions
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "player2Id",
            "name", "player2Id",
            "label", "选手2",
            "placeholder", "请选择选手2",
            "required", true,
            "help", "选择第二位选手",
            "options", userOptions
        ));
        
        fields.add(Map.of(
            "type", "datetime-local",
            "id", "scheduledTime",
            "name", "scheduledTime",
            "label", "预定时间",
            "help", "比赛预定开始时间（可选）"
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "firstEndHammer",
            "name", "firstEndHammer",
            "label", "先手后手",
            "placeholder", "请选择",
            "help", "第一局谁先手",
            "options", Arrays.asList(
                Map.of("value", "1", "text", "选手1先手"),
                Map.of("value", "2", "text", "选手2先手")
            )
        ));
        
        // 通用表单参数
        model.addAttribute("pageTitle", "编辑比赛");
        model.addAttribute("pageIcon", "bi bi-controller");
        model.addAttribute("saveUrl", "/match/save");
        model.addAttribute("backUrl", "/match/list?tournamentId=" + match.getTournamentId());
        model.addAttribute("formData", match);
        model.addAttribute("fields", fields);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        return "generic-form";
    }
    
    @PostMapping("/save")
    public String saveMatch(@ModelAttribute Match match,
                           @RequestParam(required = false) Long seriesId,
                           RedirectAttributes redirectAttributes) {
        try {
            if (!canManageMatch(match.getTournamentId())) {
                redirectAttributes.addFlashAttribute("error", "您没有权限管理此比赛");
                return "redirect:/match/list";
            }
            
            if (match.getPlayer1Id() != null && match.getPlayer2Id() != null && 
                match.getPlayer1Id().equals(match.getPlayer2Id())) {
                redirectAttributes.addFlashAttribute("error", "两名选手不能相同");
                return "redirect:/match/add?tournamentId=" + match.getTournamentId();
            }
            
            if (match.getId() == null) {
                match.setStatus((byte) 0);
                match.setCreatedAt(LocalDateTime.now());
                matchService.save(match);
                redirectAttributes.addFlashAttribute("success", "比赛创建成功");
            } else {
                match.setUpdatedAt(LocalDateTime.now());
                matchService.updateById(match);
                redirectAttributes.addFlashAttribute("success", "比赛更新成功");
            }
            
            String redirectUrl = seriesId != null ? "/match/list?seriesId=" + seriesId + "&tournamentId=" + match.getTournamentId() : "/match/list?tournamentId=" + match.getTournamentId();
            return "redirect:" + redirectUrl;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "操作失败：" + e.getMessage());
            return match.getId() == null ? "redirect:/match/add?tournamentId=" + match.getTournamentId() : "redirect:/match/edit/" + match.getId();
        }
    }
    
    @GetMapping("/score/{id}")
    public String matchScorePage(@PathVariable Long id,
                                 @RequestParam(required = false) Long seriesId,
                                 Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        Match match = matchService.getById(id);
        if (match == null) {
            return "redirect:/match/list";
        }
        
        if (!canManageMatch(match.getTournamentId())) {
            return "redirect:/match/list?tournamentId=" + match.getTournamentId();
        }
        
        Tournament tournament = tournamentService.getById(match.getTournamentId());
        User player1 = userService.getById(match.getPlayer1Id());
        User player2 = userService.getById(match.getPlayer2Id());
        
        List<SetScore> setScores = setScoreService.lambdaQuery()
            .eq(SetScore::getMatchId, id)
            .orderByAsc(SetScore::getSetNumber)
            .list();
        
        int totalPlayer1 = 0, totalPlayer2 = 0;
        for (SetScore ss : setScores) {
            totalPlayer1 += ss.getPlayer1Score();
            totalPlayer2 += ss.getPlayer2Score();
        }
        
        model.addAttribute("match", match);
        model.addAttribute("tournament", tournament);
        model.addAttribute("player1", player1);
        model.addAttribute("player2", player2);
        model.addAttribute("setScores", setScores);
        model.addAttribute("totalPlayer1", totalPlayer1);
        model.addAttribute("totalPlayer2", totalPlayer2);
        model.addAttribute("seriesId", seriesId);
        boolean resultLocked = Boolean.TRUE.equals(match.getResultLocked());
        model.addAttribute("resultLocked", resultLocked);
        model.addAttribute("canSubmitScore", !resultLocked);
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        int defaultSetCount = 8;
        TournamentCompetitionConfig cfg = tournamentCompetitionService.getConfig(match.getTournamentId());
        if (cfg != null) {
            if ("GROUP".equalsIgnoreCase(match.getPhaseCode())) {
                defaultSetCount = (cfg.getGroupStageSets() != null && cfg.getGroupStageSets() > 0) ? cfg.getGroupStageSets() : defaultSetCount;
            } else if ("FINAL".equalsIgnoreCase(match.getPhaseCode())) {
                defaultSetCount = (cfg.getFinalStageSets() != null && cfg.getFinalStageSets() > 0) ? cfg.getFinalStageSets() : defaultSetCount;
            } else {
                defaultSetCount = (cfg.getKnockoutStageSets() != null && cfg.getKnockoutStageSets() > 0) ? cfg.getKnockoutStageSets() : defaultSetCount;
            }
        }
        model.addAttribute("defaultSetCount", defaultSetCount);
        
        return "match-score";
    }
    
    @PostMapping("/score/save/{matchId}")
    public String saveMatchScore(@PathVariable Long matchId,
                                @RequestParam(required = false) Boolean simpleMode,
                                @RequestParam(required = false) List<Integer> setNumbers,
                                @RequestParam(required = false) List<Integer> player1Scores,
                                @RequestParam(required = false) List<Integer> player2Scores,
                                @RequestParam(required = false) Integer player1TotalScore,
                                @RequestParam(required = false) Integer player2TotalScore,
                                @RequestParam(required = false) Long seriesId,
                                RedirectAttributes redirectAttributes) {
        try {
            Match match = matchService.getById(matchId);
            if (match == null) {
                redirectAttributes.addFlashAttribute("error", "比赛不存在");
                return "redirect:/match/list";
            }
            
            if (!canManageMatch(match.getTournamentId())) {
                redirectAttributes.addFlashAttribute("error", "您没有权限录入比分");
                return "redirect:/match/list?tournamentId=" + match.getTournamentId();
            }
            // 验收锁定后：禁用 match-score 页面录分入口（只允许 /match/list 页由超管继续修改留痕）
            if (Boolean.TRUE.equals(match.getResultLocked())) {
                redirectAttributes.addFlashAttribute("error", "该比赛已验收；请在 /match/list 页面由超级管理员继续修改（会留痕）。");
                return "redirect:/match/list?tournamentId=" + match.getTournamentId();
            }
            
            // 删除旧比分
            setScoreService.lambdaUpdate()
                .eq(SetScore::getMatchId, matchId)
                .remove();
            
            if (simpleMode != null && simpleMode) {
                // 简单模式：只录一局总分
                SetScore ss = new SetScore();
                ss.setMatchId(matchId);
                ss.setSetNumber(1);
                ss.setPlayer1Score(player1TotalScore != null ? player1TotalScore : 0);
                ss.setPlayer2Score(player2TotalScore != null ? player2TotalScore : 0);
                ss.setCreatedAt(LocalDateTime.now());
                setScoreService.save(ss);
                
                // 更新胜者
                if (player1TotalScore != null && player2TotalScore != null) {
                    if (player1TotalScore > player2TotalScore) {
                        match.setWinnerId(match.getPlayer1Id());
                    } else if (player2TotalScore > player1TotalScore) {
                        match.setWinnerId(match.getPlayer2Id());
                    }
                    match.setStatus((byte) 2);
                    matchService.updateById(match);
                }
            } else if (setNumbers != null && !setNumbers.isEmpty()) {
                // 详细模式：录每一局
                int totalPlayer1 = 0, totalPlayer2 = 0;
                for (int i = 0; i < setNumbers.size(); i++) {
                    SetScore ss = new SetScore();
                    ss.setMatchId(matchId);
                    ss.setSetNumber(setNumbers.get(i));
                    ss.setPlayer1Score(player1Scores != null && i < player1Scores.size() ? player1Scores.get(i) : 0);
                    ss.setPlayer2Score(player2Scores != null && i < player2Scores.size() ? player2Scores.get(i) : 0);
                    ss.setCreatedAt(LocalDateTime.now());
                    setScoreService.save(ss);
                    
                    totalPlayer1 += ss.getPlayer1Score();
                    totalPlayer2 += ss.getPlayer2Score();
                }
                
                // 更新胜者
                if (totalPlayer1 > totalPlayer2) {
                    match.setWinnerId(match.getPlayer1Id());
                } else if (totalPlayer2 > totalPlayer1) {
                    match.setWinnerId(match.getPlayer2Id());
                }
                match.setStatus((byte) 2);
                matchService.updateById(match);
            }
            
            redirectAttributes.addFlashAttribute("success", "比分保存成功");
            
            String redirectUrl = seriesId != null ? "/match/score/" + matchId + "?seriesId=" + seriesId : "/match/score/" + matchId;
            return "redirect:" + redirectUrl;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "保存失败：" + e.getMessage());
            return "redirect:/match/score/" + matchId;
        }
    }
}
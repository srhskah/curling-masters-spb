package com.example.controller;

import com.example.entity.Tournament;
import com.example.entity.TournamentRegistrationSetting;
import com.example.entity.User;
import com.example.service.ITournamentRegistrationService;
import com.example.service.TournamentRegistrationExportAssembler;
import com.example.service.TournamentService;
import com.example.service.TournamentUrlService;
import com.example.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报名页友好 URL：{@code /tournament/registration/{year}/{half}/{seriesSequence}/{levelCode}}；
 * 旧链接 {@code /tournament/registration/{id}} 会 302 到友好路径。
 */
@Controller
@RequestMapping("/tournament/registration")
public class TournamentRegistrationController {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired
    private ITournamentRegistrationService registrationService;
    @Autowired
    private TournamentService tournamentService;
    @Autowired
    private UserService userService;
    @Autowired
    private TournamentRegistrationExportAssembler registrationExportAssembler;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TournamentUrlService tournamentUrlService;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return userService.findByUsername(auth.getName());
        }
        return null;
    }

    private String redirectToPage(Long tournamentId) {
        return tournamentUrlService.redirectRegistration(tournamentId);
    }

    @GetMapping("/{year:\\d{4}}/{half:[12]}/{series:\\d+}/{level}")
    public String registrationPageByKey(@PathVariable int year,
                                        @PathVariable int half,
                                        @PathVariable("series") int seriesSequence,
                                        @PathVariable String level,
                                        Model model) {
        Tournament t = tournamentUrlService.resolve(year, half, seriesSequence, level);
        if (t == null) {
            return "redirect:/tournament/list";
        }
        return renderRegistrationPage(t.getId(), model);
    }

    /** 兼容旧链接 /tournament/registration/{id} */
    @GetMapping("/{id}")
    public String registrationPageById(@PathVariable("id") Long tournamentId, Model model,
                                       jakarta.servlet.http.HttpServletRequest request) {
        String redirect = tournamentUrlService.redirectToPrettyOrNull(
                tournamentId, tournamentUrlService.registrationUrl(tournamentId), request);
        if (redirect != null) {
            return redirect;
        }
        return renderRegistrationPage(tournamentId, model);
    }

    private String renderRegistrationPage(Long tournamentId, Model model) {
        User u = getCurrentUser();
        if (u == null) {
            return "redirect:/user/login";
        }
        LocalDateTime now = LocalDateTime.now();
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) {
            return "redirect:/tournament/list";
        }
        TournamentRegistrationSetting setting = registrationService.getSetting(tournamentId);
        model.addAttribute("tournament", t);
        model.addAttribute("setting", setting);
        model.addAttribute("now", now);
        model.addAttribute("rows", registrationService.listRows(tournamentId, now));
        model.addAttribute("preview", registrationService.preview(tournamentId, now));
        model.addAttribute("canManage", registrationService.canManage(u, tournamentId));
        model.addAttribute("registrationOpen", registrationService.isRegistrationOpen(t, now));
        model.addAttribute("registrationEnabled", registrationService.isRegistrationEnabled(t));
        model.addAttribute("registerBlockReason", registrationService.validateRegister(tournamentId, u.getId(), now));
        model.addAttribute("alreadyRegistered", registrationService.hasRegistration(tournamentId, u.getId()));
        List<Tournament> siblingTournaments = t.getSeriesId() == null ? List.of()
                : tournamentService.lambdaQuery()
                .eq(Tournament::getSeriesId, t.getSeriesId())
                .ne(Tournament::getId, t.getId())
                .list()
                .stream()
                .sorted(Comparator.comparing(Tournament::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Tournament::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        model.addAttribute("siblingTournaments", siblingTournaments);
        model.addAttribute("banRefTournamentIds", registrationService.resolveBanOtherTournamentRefIdSet(setting));
        model.addAttribute("banRefTournamentTopMap", registrationService.getBanRefTopMap(tournamentId));
        
        // 构造禁报规则描述
        String banRuleDescription = buildBanRuleDescription(setting, siblingTournaments, registrationService.getBanRefTopMap(tournamentId));
        model.addAttribute("banRuleDescription", banRuleDescription);
        
        try {
            Map<String, Object> exportData = registrationExportAssembler.assemble(tournamentId, now);
            if (exportData != null) {
                String json = objectMapper.writeValueAsString(exportData);
                model.addAttribute("registrationExportB64",
                        Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
            } else {
                model.addAttribute("registrationExportB64", "");
            }
        } catch (Exception e) {
            model.addAttribute("registrationExportB64", "");
        }
        return "tournament-registration";
    }

    @PostMapping("/{id}/register")
    public String register(@PathVariable("id") Long tournamentId, RedirectAttributes ra) {
        User u = getCurrentUser();
        if (u == null) {
            return "redirect:/user/login";
        }
        try {
            registrationService.register(tournamentId, u.getId(), LocalDateTime.now());
            ra.addFlashAttribute("message", "报名成功");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return redirectToPage(tournamentId);
    }

    @PostMapping("/{id}/withdraw")
    public String withdraw(@PathVariable("id") Long tournamentId, RedirectAttributes ra) {
        User u = getCurrentUser();
        if (u == null) {
            return "redirect:/user/login";
        }
        try {
            registrationService.withdraw(tournamentId, u.getId(), LocalDateTime.now());
            ra.addFlashAttribute("message", "已撤销报名");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return redirectToPage(tournamentId);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String approve(@PathVariable("id") Long tournamentId,
                          @RequestParam Long userId,
                          RedirectAttributes ra) {
        User reviewer = getCurrentUser();
        try {
            registrationService.approve(tournamentId, userId, reviewer, LocalDateTime.now());
            ra.addFlashAttribute("message", "已通过");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return redirectToPage(tournamentId);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String reject(@PathVariable("id") Long tournamentId,
                         @RequestParam Long userId,
                         RedirectAttributes ra) {
        User reviewer = getCurrentUser();
        try {
            registrationService.reject(tournamentId, userId, reviewer, LocalDateTime.now());
            ra.addFlashAttribute("message", "已拒绝并移除报名");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return redirectToPage(tournamentId);
    }

    @PostMapping("/{id}/settings")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String saveSettings(@PathVariable("id") Long tournamentId,
                               @RequestParam(required = false) Boolean enabled,
                               @RequestParam(required = false) String deadline,
                               @RequestParam(required = false) Integer quotaN,
                               @RequestParam(required = false) Integer mode,
                               @RequestParam(required = false) Integer mainDirectM,
                               @RequestParam(required = false) Integer qualifierSeedCount,
                               @RequestParam(required = false) Integer banTotalRankTop,
                               @RequestParam(required = false) List<Long> banOtherTournamentIds,
                               @RequestParam Map<String, String> allParams,
                               RedirectAttributes ra) {
        User editor = getCurrentUser();
        TournamentRegistrationSetting form = new TournamentRegistrationSetting();
        form.setTournamentId(tournamentId);
        form.setEnabled(enabled);
        if (deadline != null && !deadline.isBlank()) {
            try {
                form.setDeadline(LocalDateTime.parse(deadline, DT));
            } catch (DateTimeParseException ex) {
                ra.addFlashAttribute("error", "截止时间格式无效");
                return redirectToPage(tournamentId);
            }
        }
        form.setQuotaN(quotaN);
        form.setMode(mode);
        form.setMainDirectM(mainDirectM);
        form.setQualifierSeedCount(qualifierSeedCount);
        form.setBanTotalRankTop(banTotalRankTop);
        List<Long> banIds = banOtherTournamentIds == null ? List.of() : banOtherTournamentIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .sorted()
                .toList();
        String banCsv = banIds.isEmpty() ? "" : banIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        form.setBanOtherTournamentIds(banCsv);
        form.setBanOtherTournamentId(null);
        // banOtherTournamentTop 字段已废弃，改用独立的参照赛事K值
        form.setBanOtherTournamentTop(null);
        
        // 从所有参数中提取 banRefTournamentTopMap 的值
        Map<Long, Integer> refTournamentIdToTop = new HashMap<>();
        String prefix = "banRefTournamentTopMap[";
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.startsWith(prefix) && key.endsWith("]")) {
                try {
                    String idStr = key.substring(prefix.length(), key.length() - 1);
                    Long refTournamentId = Long.parseLong(idStr);
                    String valueStr = entry.getValue();
                    if (valueStr != null && !valueStr.isBlank()) {
                        Integer banTop = Integer.parseInt(valueStr);
                        if (refTournamentId != null && banTop != null && banTop >= 1 && banIds.contains(refTournamentId)) {
                            refTournamentIdToTop.put(refTournamentId, banTop);
                        }
                    }
                } catch (NumberFormatException e) {
                    // 忽略无效的参数
                }
            }
        }
        
        // 先保存每个参照赛事的独立K值（这样 validateSetting 才能查到）
        registrationService.saveBanRefs(tournamentId, refTournamentIdToTop);
        
        try {
            registrationService.saveSetting(editor, form);
            ra.addFlashAttribute("message", "报名配置已保存");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return redirectToPage(tournamentId);
    }

    @PostMapping("/{id}/materialize")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String materialize(@PathVariable("id") Long tournamentId, RedirectAttributes ra) {
        User op = getCurrentUser();
        try {
            int n = registrationService.materializeMainDirectEntries(op, tournamentId, LocalDateTime.now());
            ra.addFlashAttribute("message", "已写入正赛直通车入选 " + n + " 人（跳过已存在的条目）");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return redirectToPage(tournamentId);
    }

    /**
     * 获取报名列表数据（JSON API，用于前端自动刷新）
     */
    @GetMapping("/{id}/results")
    @ResponseBody
    public Map<String, Object> getRegistrationResults(@PathVariable("id") Long tournamentId) {
        LocalDateTime now = LocalDateTime.now();
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) {
            return Map.of("error", "赛事不存在");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("rows", registrationService.listRows(tournamentId, now));
        result.put("preview", registrationService.preview(tournamentId, now));
        result.put("tournamentId", tournamentId);
        result.put("levelCode", t.getLevelCode());
        
        TournamentRegistrationSetting setting = registrationService.getSetting(tournamentId);
        if (setting != null && setting.getDeadline() != null) {
            result.put("deadlineText", setting.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        } else {
            result.put("deadlineText", "未设置");
        }
        
        return result;
    }

    /**
     * 构造禁报规则描述文本
     */
    private String buildBanRuleDescription(TournamentRegistrationSetting setting, 
                                            List<Tournament> siblingTournaments,
                                            Map<String, Integer> banRefTopMap) {
        if (setting == null) return null;
        
        List<String> parts = new ArrayList<>();
        
        // 总排名前K禁报
        if (setting.getBanTotalRankTop() != null && setting.getBanTotalRankTop() > 0) {
            parts.add("总排名前" + setting.getBanTotalRankTop());
        }
        
        // 各参照赛事独立K值禁报
        Set<Long> banRefIds = registrationService.resolveBanOtherTournamentRefIdSet(setting);
        if (banRefIds != null && !banRefIds.isEmpty() && siblingTournaments != null) {
            for (Tournament sibling : siblingTournaments) {
                if (banRefIds.contains(sibling.getId())) {
                    String tournamentName = sibling.getLevelCode() != null ? sibling.getLevelCode() : "赛事";
                    String tournamentIdStr = String.valueOf(sibling.getId());
                    Integer banTop = banRefTopMap != null ? banRefTopMap.get(tournamentIdStr) : null;
                    if (banTop != null && banTop > 0) {
                        parts.add(tournamentName + "赛前" + banTop);
                    }
                }
            }
        }
        
        if (parts.isEmpty()) return null;
        
        return "禁止" + String.join("、", parts) + "报名本届赛事";
    }
}

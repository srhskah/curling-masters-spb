package com.example.controller;

import com.example.entity.Tournament;
import com.example.entity.TournamentRegistrationSetting;
import com.example.entity.User;
import com.example.service.ITournamentRegistrationService;
import com.example.service.TournamentRegistrationExportAssembler;
import com.example.service.TournamentService;
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
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 报名接龙路径统一为 /tournament/registration/{id}，避免与 /tournament/detail/{id} 产生匹配歧义。
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

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return userService.findByUsername(auth.getName());
        }
        return null;
    }

    private static String redirectToPage(Long tournamentId) {
        return "redirect:/tournament/registration/" + tournamentId;
    }

    @GetMapping("/{id}")
    public String registrationPage(@PathVariable("id") Long tournamentId, Model model) {
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
                               @RequestParam(required = false) Integer banOtherTournamentTop,
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
        form.setBanOtherTournamentTop(banOtherTournamentTop);
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
}

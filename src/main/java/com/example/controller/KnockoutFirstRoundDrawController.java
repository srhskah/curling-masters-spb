package com.example.controller;

import com.example.entity.CustomUserDetails;
import com.example.entity.Tournament;
import com.example.entity.User;
import com.example.service.TournamentDrawAuthHelper;
import com.example.service.TournamentService;
import com.example.service.TournamentUrlService;
import com.example.service.UserService;
import com.example.service.impl.KnockoutFirstRoundDrawService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class KnockoutFirstRoundDrawController {

    @Autowired private TournamentService tournamentService;
    @Autowired private TournamentUrlService tournamentUrlService;
    @Autowired private UserService userService;
    @Autowired private TournamentDrawAuthHelper tournamentDrawAuthHelper;
    @Autowired private KnockoutFirstRoundDrawService knockoutFirstRoundDrawService;

    @GetMapping("/tournament/{year:\\d{4}}/{half:[12]}/{series:\\d+}/{level}/knockout-draw")
    public String pageByKey(@PathVariable int year,
                            @PathVariable int half,
                            @PathVariable("series") int seriesSequence,
                            @PathVariable String level,
                            Model model, Authentication auth, HttpServletRequest request) {
        Tournament tournament = tournamentUrlService.resolve(year, half, seriesSequence, level);
        if (tournament == null) {
            return "redirect:/";
        }
        return render(tournament.getId(), model, auth, request);
    }

    @GetMapping("/tournament/{tournamentId}/knockout-draw")
    public String pageById(@PathVariable Long tournamentId, Model model, Authentication auth, HttpServletRequest request) {
        String redirect = tournamentUrlService.redirectToPrettyOrNull(
                tournamentId, tournamentUrlService.knockoutDrawUrl(tournamentId), request);
        if (redirect != null) {
            return redirect;
        }
        return render(tournamentId, model, auth, request);
    }

    @PostMapping("/tournament/{tournamentId}/knockout-draw/perform")
    @ResponseBody
    public Map<String, Object> perform(@PathVariable Long tournamentId, Authentication auth) {
        try {
            return knockoutFirstRoundDrawService.performDraw(currentUser(auth), tournamentId);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage() == null ? "抽签失败" : e.getMessage());
        }
    }

    @PostMapping("/tournament/{tournamentId}/knockout-draw/reset")
    @ResponseBody
    public Map<String, Object> reset(@PathVariable Long tournamentId, Authentication auth) {
        try {
            User user = currentUser(auth);
            if (!tournamentDrawAuthHelper.canManageDraw(user, tournamentId)) {
                return Map.of("success", false, "message", "无权重置抽签");
            }
            knockoutFirstRoundDrawService.reset(user, tournamentId);
            return Map.of("success", true);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage() == null ? "重置失败" : e.getMessage());
        }
    }

    @PostMapping("/tournament/{tournamentId}/knockout-draw/apply")
    @ResponseBody
    public Map<String, Object> apply(@PathVariable Long tournamentId, Authentication auth) {
        try {
            User user = currentUser(auth);
            if (!tournamentDrawAuthHelper.canManageDraw(user, tournamentId)) {
                return Map.of("success", false, "message", "无权写入对阵");
            }
            String message = knockoutFirstRoundDrawService.applyAgain(user, tournamentId);
            return Map.of("success", true, "message", message == null ? "已写入" : message);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage() == null ? "写入失败" : e.getMessage());
        }
    }

    private String render(Long tournamentId, Model model, Authentication auth, HttpServletRequest request) {
        Tournament tournament = tournamentService.getById(tournamentId);
        if (tournament == null) {
            return "redirect:/";
        }
        User user = currentUser(auth);
        boolean canManage = tournamentDrawAuthHelper.canManageDraw(user, tournamentId);
        Map<String, Object> page;
        try {
            page = knockoutFirstRoundDrawService.pageModel(tournamentId, user);
        } catch (Exception e) {
            page = Map.of("enabled", true, "blockedReason", e.getMessage() == null ? "暂时不能抽签" : e.getMessage());
        }
        model.addAttribute("tournament", tournament);
        model.addAttribute("canManageDraw", canManage);
        model.addAttribute("page", page);
        model.addAttribute("pageTitle", (tournament.getLevelCode() == null ? "赛事" : tournament.getLevelCode()) + " - 淘汰赛首轮抽签");
        model.addAttribute("clientIp", com.example.util.IpAddressUtil.getClientIpAddress(request));
        model.addAttribute("ipType", com.example.util.IpAddressUtil.classifyIpAddress(
                com.example.util.IpAddressUtil.getClientIpAddress(request)));
        return "tournament/knockout-draw";
    }

    private User currentUser(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            return null;
        }
        return userService.getById(details.getId());
    }
}

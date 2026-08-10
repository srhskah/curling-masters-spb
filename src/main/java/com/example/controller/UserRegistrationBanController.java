package com.example.controller;

import com.example.entity.TournamentLevel;
import com.example.entity.User;
import com.example.entity.UserRegistrationBan;
import com.example.service.ITournamentLevelService;
import com.example.service.UserRegistrationBanService;
import com.example.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户禁止报名控制器
 */
@Controller
@RequestMapping("/user")
public class UserRegistrationBanController {

    private final UserRegistrationBanService banService;
    private final UserService userService;
    private final ITournamentLevelService tournamentLevelService;

    public UserRegistrationBanController(UserRegistrationBanService banService, UserService userService,
                                        ITournamentLevelService tournamentLevelService) {
        this.banService = banService;
        this.userService = userService;
        this.tournamentLevelService = tournamentLevelService;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return userService.findByUsername(auth.getName());
        }
        return null;
    }

    /**
     * 获取所有禁止报名记录
     */
    @GetMapping("/registration-bans")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public String listBans(Model model) {
        List<UserRegistrationBan> bans = banService.getAllBans();
        model.addAttribute("bans", bans);
        return "user-registration-bans";
    }

    /**
     * 创建禁止报名
     */
    @PostMapping("/registration-ban/create")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @ResponseBody
    public Map<String, Object> createBan(@RequestParam List<Long> userIds,
                                          @RequestParam String banType,
                                          @RequestParam(required = false) String targetLevelCode,
                                          @RequestParam(required = false) Long targetSeasonId,
                                          @RequestParam(required = false) Integer targetYear,
                                          @RequestParam(required = false) Integer banCount,
                                          @RequestParam(required = false, defaultValue = "false") Boolean banFinalTournament,
                                          @RequestParam String reason) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            User currentUser = getCurrentUser();
            if (currentUser == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return result;
            }

            List<UserRegistrationBan> bans = banService.createBans(
                    userIds, banType, targetLevelCode, targetSeasonId, 
                    targetYear, banCount, banFinalTournament, reason, currentUser.getId()
            );

            result.put("success", true);
            result.put("message", "成功禁止 " + bans.size() + " 个用户报名");
            result.put("count", bans.size());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "禁止报名失败：" + e.getMessage());
        }

        return result;
    }

    /**
     * 解除禁止报名
     */
    @PostMapping("/registration-ban/remove/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @ResponseBody
    public Map<String, Object> removeBan(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            User currentUser = getCurrentUser();
            if (currentUser == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return result;
            }

            banService.removeBan(id, currentUser.getId());

            result.put("success", true);
            result.put("message", "解除禁止报名成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "解除禁止报名失败：" + e.getMessage());
        }

        return result;
    }

    /**
     * 获取用户的禁止报名记录
     */
    @GetMapping("/registration-bans/user/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @ResponseBody
    public List<UserRegistrationBan> getUserBans(@PathVariable Long userId) {
        return banService.getUserBans(userId);
    }

    /**
     * 获取所有赛事等级
     */
    @GetMapping("/tournament-levels")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @ResponseBody
    public List<TournamentLevel> getTournamentLevels() {
        return tournamentLevelService.list();
    }
}
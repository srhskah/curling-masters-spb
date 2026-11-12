package com.example.controller;

import com.example.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class SystemConfigController {

    @Autowired
    private SystemConfigService systemConfigService;
    
    @Autowired
    private com.example.service.UserService userService;

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            com.example.entity.User user = userService.findByUsername(username);
            return user != null && user.getRole() != null && user.getRole() == 0;
        }
        return false;
    }

    /** 旧入口：合并进总控台后重定向，保留书签兼容 */
    @GetMapping("/system-config")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String systemConfigPage() {
        return "redirect:/admin/console#competition-rule-defaults";
    }

    @PostMapping("/system-config/save")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String saveSystemConfig(
            @RequestParam Integer defaultCenterLineLimit,
            @RequestParam Integer defaultDeadStoneLimit,
            @RequestParam(required = false, defaultValue = "false") String defaultKnockoutRequirePrevStageDone,
            RedirectAttributes redirectAttributes) {
        try {
            if (defaultCenterLineLimit == null || defaultCenterLineLimit < 0 || defaultCenterLineLimit > 16) {
                redirectAttributes.addFlashAttribute("error", "不允许磕中线壶的数量必须在0-16之间");
                return "redirect:/admin/console#competition-rule-defaults";
            }
            if (defaultDeadStoneLimit == null || defaultDeadStoneLimit < 0 || defaultDeadStoneLimit > 16) {
                redirectAttributes.addFlashAttribute("error", "不允许打成死壶的数量必须在0-16之间");
                return "redirect:/admin/console#competition-rule-defaults";
            }

            systemConfigService.setConfigValue("competition_rule_default_center_line_limit",
                String.valueOf(defaultCenterLineLimit), "默认不允许磕中线壶的数量");
            systemConfigService.setConfigValue("competition_rule_default_dead_stone_limit",
                String.valueOf(defaultDeadStoneLimit), "默认不允许打成死壶的数量");
            boolean koSerialDefault = "true".equalsIgnoreCase(defaultKnockoutRequirePrevStageDone)
                    || "1".equals(defaultKnockoutRequirePrevStageDone)
                    || "on".equalsIgnoreCase(defaultKnockoutRequirePrevStageDone);
            systemConfigService.setConfigValue("knockout_require_prev_stage_done_default",
                    koSerialDefault ? "true" : "false",
                    "新建/未单独设置时：是否要求上一阶段淘汰赛全部结束后才可进入下一阶段");

            redirectAttributes.addFlashAttribute("message", "赛制规则默认值设置成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "设置失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage()));
        }
        return "redirect:/admin/console#competition-rule-defaults";
    }
}
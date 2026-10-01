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

    @GetMapping("/system-config")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String systemConfigPage(Model model) {
        model.addAttribute("defaultCenterLineLimit", systemConfigService.getDefaultCenterLineLimit());
        model.addAttribute("defaultDeadStoneLimit", systemConfigService.getDefaultDeadStoneLimit());
        return "system-config";
    }

    @PostMapping("/system-config/save")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String saveSystemConfig(
            @RequestParam Integer defaultCenterLineLimit,
            @RequestParam Integer defaultDeadStoneLimit,
            RedirectAttributes redirectAttributes) {
        try {
            if (defaultCenterLineLimit < 0 || defaultCenterLineLimit > 16) {
                redirectAttributes.addFlashAttribute("error", "不允许磕中线壶的数量必须在0-16之间");
                return "redirect:/admin/system-config";
            }
            if (defaultDeadStoneLimit < 0 || defaultDeadStoneLimit > 16) {
                redirectAttributes.addFlashAttribute("error", "不允许打成死壶的数量必须在0-16之间");
                return "redirect:/admin/system-config";
            }

            systemConfigService.setConfigValue("competition_rule_default_center_line_limit", 
                String.valueOf(defaultCenterLineLimit), "默认不允许磕中线壶的数量");
            systemConfigService.setConfigValue("competition_rule_default_dead_stone_limit", 
                String.valueOf(defaultDeadStoneLimit), "默认不允许打成死壶的数量");

            redirectAttributes.addFlashAttribute("success", "赛制规则默认值设置成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "设置失败：" + e.getMessage());
        }
        return "redirect:/admin/system-config";
    }
}
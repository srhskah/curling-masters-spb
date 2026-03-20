package com.example.controller;

import com.example.entity.TournamentLevel;
import com.example.entity.User;
import com.example.service.ITournamentLevelService;
import com.example.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.util.HtmlEscaper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.*;

import com.example.util.IpAddressUtil;

/**
 * 赛事等级管理控制器
 * 支持赛事等级的全生命周期管理，包括创建、编辑、删除和查看
 * 
 * @author Curling Masters
 * @since 2026-03-17
 */
@Controller
@RequestMapping("/tournamentLevel")
public class TournamentLevelController {

    @Autowired
    private ITournamentLevelService tournamentLevelService;
    
    @Autowired
    private UserService userService;

    /**
     * 赛事等级列表页面（所有用户可查看）
     */
    @GetMapping("/list")
    public String tournamentLevelList(Model model, HttpServletRequest request) {
        List<TournamentLevel> levels = tournamentLevelService.list();
        
        // 构建通用列表数据
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (TournamentLevel level : levels) {
            Map<String, Object> item = new HashMap<>();
            
            item.put("data", Arrays.asList(
                level.getId(),
                "<strong>" + HtmlEscaper.escapeHtml(level.getCode()) + "</strong>",
                level.getName(),
                level.getDefaultChampionRatio(),
                level.getDefaultBottomPoints(),
                level.getDescription() != null ? level.getDescription() : "-"
            ));
            item.put("filters", Map.of());
            item.put("id", level.getId());
            
            dataList.add(item);
        }
        
        // 构建列配置
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(Map.of("title", "ID", "type", "text"));
        columns.add(Map.of("title", "等级代码", "type", "custom"));
        columns.add(Map.of("title", "等级名称", "type", "text"));
        columns.add(Map.of("title", "冠军积分/人数比率", "type", "text"));
        columns.add(Map.of("title", "垫底积分", "type", "text"));
        columns.add(Map.of("title", "描述", "type", "text"));
        
        // 构建操作按钮
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(Map.of("urlPrefix", "/tournamentLevel/edit/", "btnClass", "btn btn-sm btn-outline-warning", "icon", "bi bi-pencil", "text", "编辑"));
        actions.add(Map.of("urlPrefix", "/tournamentLevel/delete/", "method", "post", "btnClass", "btn btn-sm btn-outline-danger", "icon", "bi bi-trash", "text", "删除", "confirm", "确定要删除这个赛事等级吗？"));
        
        // 获取当前用户信息以判断权限
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = false;
        if (auth != null && auth.isAuthenticated()) {
            String username = auth.getName();
            User user = userService.findByUsername(username);
            if (user != null && user.getRole() <= 1) { // 管理员
                isAdmin = true;
            }
        }
        
        // 通用列表参数
        model.addAttribute("pageTitle", "赛事等级列表");
        model.addAttribute("pageIcon", "bi bi-layers");
        model.addAttribute("entityName", "赛事等级");
        model.addAttribute("addUrl", "/tournamentLevel/add");
        model.addAttribute("dataList", dataList);
        model.addAttribute("columns", columns);
        model.addAttribute("actions", actions);
        model.addAttribute("hasActions", true);
        model.addAttribute("emptyIcon", "bi bi-layers");
        model.addAttribute("emptyMessage", "暂无赛事等级数据");
        model.addAttribute("isAdmin", isAdmin);
        
        return "generic-list";
    }
    
    /**
     * 新增赛事等级页面（仅管理员）
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/add")
    public String addTournamentLevelPage(Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        
        // 构建表单字段配置
        List<Map<String, Object>> fields = new ArrayList<>();
        
        fields.add(Map.of(
            "type", "text",
            "id", "code",
            "name", "code",
            "label", "等级代码",
            "placeholder", "如：年终总决赛、2000赛等",
            "required", true,
            "help", "赛事等级的唯一标识代码"
        ));
        
        fields.add(Map.of(
            "type", "text",
            "id", "name",
            "name", "name",
            "label", "等级名称",
            "placeholder", "如：年终总决赛、2000积分赛等",
            "required", true,
            "help", "赛事等级的显示名称"
        ));
        
        fields.add(Map.of(
            "type", "number",
            "id", "defaultChampionRatio",
            "name", "defaultChampionRatio",
            "label", "冠军积分/人数比率",
            "placeholder", "如：100",
            "min", "0.1",
            "max", "100",
            "step", "0.1",
            "required", true,
            "help", "冠军积分 = 参赛人数 × 比率（比率不是百分比，可为小数）"
        ));
        
        fields.add(Map.of(
            "type", "number",
            "id", "defaultBottomPoints",
            "name", "defaultBottomPoints",
            "label", "垫底积分",
            "placeholder", "如：0",
            "min", "0",
            "required", true,
            "help", "该等级赛事的最低积分"
        ));
        
        fields.add(Map.of(
            "type", "textarea",
            "id", "description",
            "name", "description",
            "label", "描述",
            "placeholder", "赛事等级描述信息（可选）",
            "rows", 3,
            "help", "可选的赛事等级描述信息"
        ));
        
        // 通用表单参数
        model.addAttribute("pageTitle", "添加赛事等级");
        model.addAttribute("pageIcon", "bi bi-layers");
        model.addAttribute("saveUrl", "/tournamentLevel/save");
        model.addAttribute("backUrl", "/tournamentLevel/list");
        model.addAttribute("formData", new TournamentLevel());
        model.addAttribute("fields", fields);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        return "generic-form";
    }
    
    /**
     * 编辑赛事等级页面（仅管理员）
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/edit/{id}")
    public String editTournamentLevelPage(@PathVariable Integer id, Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        
        TournamentLevel level = tournamentLevelService.getById(id);
        if (level == null) {
            return "redirect:/tournamentLevel/list";
        }
        
        // 构建表单字段配置
        List<Map<String, Object>> fields = new ArrayList<>();
        
        fields.add(Map.of(
            "type", "text",
            "id", "code",
            "name", "code",
            "label", "等级代码",
            "placeholder", "如：年终总决赛、2000赛等",
            "required", true,
            "help", "赛事等级的唯一标识代码"
        ));
        
        fields.add(Map.of(
            "type", "text",
            "id", "name",
            "name", "name",
            "label", "等级名称",
            "placeholder", "如：年终总决赛、2000积分赛等",
            "required", true,
            "help", "赛事等级的显示名称"
        ));
        
        fields.add(Map.of(
            "type", "number",
            "id", "defaultChampionRatio",
            "name", "defaultChampionRatio",
            "label", "冠军积分/人数比率",
            "placeholder", "如：100",
            "min", "0.1",
            "max", "100",
            "step", "0.1",
            "required", true,
            "help", "冠军积分 = 参赛人数 × 比率（比率不是百分比，可为小数）"
        ));
        
        fields.add(Map.of(
            "type", "number",
            "id", "defaultBottomPoints",
            "name", "defaultBottomPoints",
            "label", "垫底积分",
            "placeholder", "如：0",
            "min", "0",
            "required", true,
            "help", "该等级赛事的最低积分"
        ));
        
        fields.add(Map.of(
            "type", "textarea",
            "id", "description",
            "name", "description",
            "label", "描述",
            "placeholder", "赛事等级描述信息（可选）",
            "rows", 3,
            "help", "可选的赛事等级描述信息"
        ));
        
        // 通用表单参数
        model.addAttribute("pageTitle", "编辑赛事等级");
        model.addAttribute("pageIcon", "bi bi-layers");
        model.addAttribute("saveUrl", "/tournamentLevel/save");
        model.addAttribute("backUrl", "/tournamentLevel/list");
        model.addAttribute("formData", level);
        model.addAttribute("fields", fields);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        return "generic-form";
    }
    
    /**
     * 保存赛事等级（新增或编辑，仅管理员）
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @PostMapping("/save")
    public String saveTournamentLevel(@ModelAttribute TournamentLevel level, RedirectAttributes redirectAttributes) {
        try {
            // 验证数据
            if (level.getCode() == null || level.getCode().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "等级代码不能为空");
                return "redirect:/tournamentLevel/add";
            }
            if (level.getName() == null || level.getName().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "等级名称不能为空");
                return "redirect:/tournamentLevel/add";
            }
            if (level.getDefaultChampionRatio() == null || level.getDefaultChampionRatio().compareTo(BigDecimal.ZERO) <= 0 || level.getDefaultChampionRatio().compareTo(new BigDecimal("100")) > 0) {
                redirectAttributes.addFlashAttribute("error", "冠军积分/人数比率必须大于等于0");
                return "redirect:/tournamentLevel/add";
            }
            if (level.getDefaultBottomPoints() == null || level.getDefaultBottomPoints() < 0) {
                redirectAttributes.addFlashAttribute("error", "垫底积分不能为负数");
                return "redirect:/tournamentLevel/add";
            }
            
            // 检查代码是否已存在
            boolean exists = tournamentLevelService.lambdaQuery()
                    .eq(TournamentLevel::getCode, level.getCode())
                    .ne(level.getId() != null, TournamentLevel::getId, level.getId())
                    .exists();
            
            if (exists) {
                redirectAttributes.addFlashAttribute("error", "该等级代码已存在");
                return "redirect:/tournamentLevel/add";
            }
            
            if (level.getId() == null) {
                // 新增
                tournamentLevelService.save(level);
                redirectAttributes.addFlashAttribute("success", "赛事等级创建成功");
            } else {
                // 编辑
                tournamentLevelService.updateById(level);
                redirectAttributes.addFlashAttribute("success", "赛事等级更新成功");
            }
            
            return "redirect:/tournamentLevel/list";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "操作失败：" + e.getMessage());
            return level.getId() == null ? "redirect:/tournamentLevel/add" : "redirect:/tournamentLevel/edit/" + level.getId();
        }
    }
    
    /**
     * 删除赛事等级（仅管理员）
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @PostMapping("/delete/{id}")
    public String deleteTournamentLevel(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            // 检查是否有相关联的赛事
            boolean hasRelatedTournaments = tournamentLevelService.hasRelatedTournaments(id);
            if (hasRelatedTournaments) {
                redirectAttributes.addFlashAttribute("error", "该赛事等级下有关联的赛事，无法删除");
                return "redirect:/tournamentLevel/list";
            }
            
            tournamentLevelService.removeById(id);
            redirectAttributes.addFlashAttribute("success", "赛事等级删除成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "删除失败：" + e.getMessage());
        }
        return "redirect:/tournamentLevel/list";
    }
}

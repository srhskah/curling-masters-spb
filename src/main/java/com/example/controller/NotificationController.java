package com.example.controller;

import com.example.entity.NotificationMessage;
import com.example.entity.User;
import com.example.service.INotificationService;
import com.example.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author Curling Masters
 * @since 2026-03-04
 */
@Controller
@RequestMapping("/notification")
public class NotificationController {
    private final INotificationService notificationService;
    private final UserService userService;

    public NotificationController(INotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GetMapping("/list")
    public String list(Model model, Principal principal) {
        User me = currentUser(principal);
        model.addAttribute("isManager", notificationService.canManage(me));
        if (notificationService.canManage(me)) {
            model.addAttribute("items", notificationService.listManage(80));
        } else {
            model.addAttribute("items", notificationService.listInbox(me != null ? me.getId() : null, 80));
        }
        return "notification/list";
    }

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Long id, Model model, Principal principal) {
        User me = currentUser(principal);
        Optional<NotificationMessage> detail = notificationService.getReadableDetail(id, me != null ? me.getId() : null);
        if (detail.isEmpty()) {
            model.addAttribute("error", "通知不存在或未发布");
            return "redirect:/notification/list";
        }
        NotificationMessage m = detail.get();
        model.addAttribute("isManager", notificationService.canManage(me));
        model.addAttribute("canEditThis", notificationService.canEditNotification(me, m));
        model.addAttribute("message", m);
        model.addAttribute("copyText", notificationService.buildCopyText(m));
        return "notification/detail";
    }

    @GetMapping("/edit")
    public String createPage(Model model, Principal principal) {
        User me = currentUser(principal);
        if (!notificationService.canManage(me)) return "redirect:/notification/list";
        model.addAttribute("message", new NotificationMessage());
        model.addAttribute("isEdit", false);
        return "notification/edit";
    }

    @GetMapping("/edit/{id}")
    public String editPage(@PathVariable Long id, Model model, Principal principal, RedirectAttributes ra) {
        User me = currentUser(principal);
        if (!notificationService.canManage(me)) return "redirect:/notification/list";
        Optional<NotificationMessage> detail = notificationService.getManageDetail(id, me);
        if (detail.isEmpty()) {
            ra.addFlashAttribute("error", "无权限编辑该通知（仅创建者可修改）");
            return "redirect:/notification/list";
        }
        model.addAttribute("message", detail.get());
        model.addAttribute("isEdit", true);
        return "notification/edit";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String title,
                       @RequestParam("contentMarkdown") String markdown,
                       @RequestParam(defaultValue = "false") boolean publishToHome,
                       @RequestParam(defaultValue = "false") boolean publishNow,
                       Principal principal,
                       RedirectAttributes ra) {
        User me = currentUser(principal);
        if (!notificationService.canManage(me)) {
            ra.addFlashAttribute("error", "无权限");
            return "redirect:/notification/list";
        }
        try {
            NotificationMessage m = notificationService.createOrUpdate(id, title, markdown, publishToHome, publishNow, me);
            ra.addFlashAttribute("message", "通知已保存");
            return "redirect:/notification/detail/" + m.getId();
        } catch (Exception e) {
            ra.addFlashAttribute("error", "保存失败：" + e.getMessage());
            return id == null ? "redirect:/notification/edit" : "redirect:/notification/edit/" + id;
        }
    }

    @PostMapping("/mark-unread/{id}")
    public String markUnread(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        User me = currentUser(principal);
        if (me == null) {
            ra.addFlashAttribute("error", "请先登录");
            return "redirect:/user/login";
        }
        try {
            notificationService.markUnread(id, me.getId());
            ra.addFlashAttribute("message", "已标记为未读");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "操作失败：" + e.getMessage());
        }
        return "redirect:/notification/detail/" + id;
    }

    private User currentUser(Principal principal) {
        return principal == null ? null : userService.findByUsername(principal.getName());
    }
}

package com.example.controller;

import com.example.entity.NotificationMessage;
import com.example.entity.User;
import com.example.service.INotificationService;
import com.example.service.UserService;
import com.example.util.NotificationRssBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 消息通知：列表/详情支持未登录阅读公共通知；提供公共与个人 RSS。
 */
@Controller
@RequestMapping("/notification")
public class NotificationController {
    private static final MediaType RSS_MEDIA = MediaType.valueOf("application/rss+xml");

    private final INotificationService notificationService;
    private final UserService userService;
    private final String configuredPublicBaseUrl;

    public NotificationController(INotificationService notificationService,
                                  UserService userService,
                                  @Value("${app.public-base-url:}") String configuredPublicBaseUrl) {
        this.notificationService = notificationService;
        this.userService = userService;
        this.configuredPublicBaseUrl = configuredPublicBaseUrl == null ? "" : configuredPublicBaseUrl.trim();
    }

    @GetMapping("/list")
    public String list(Model model, Principal principal, HttpServletRequest request) {
        User me = currentUser(principal);
        boolean manager = notificationService.canManage(me);
        model.addAttribute("isManager", manager);
        model.addAttribute("loggedIn", me != null);
        model.addAttribute("unreadCount", me == null ? 0L : notificationService.unreadCount(me.getId()));
        // 办赛人员收件箱也按「公共 + 本人收件」；不得因管理员身份列出他人个人向通知
        if (me != null) {
            model.addAttribute("items", notificationService.listReadableForUser(me, 80));
        } else {
            model.addAttribute("items", notificationService.listPublicPublished(80));
        }
        String base = requestBaseUrl(request);
        model.addAttribute("publicRssUrl", base + "/notification/rss/public.xml");
        model.addAttribute("rssUrlLooksLocal", looksLocalOrPrivate(base));
        model.addAttribute("publicBaseUrlConfigured", !configuredPublicBaseUrl.isEmpty());
        if (me != null) {
            String token = userService.ensureRssToken(me);
            model.addAttribute("personalRssUrl", base + "/notification/rss/u/" + token + ".xml");
        }
        return "notification/list";
    }

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Long id, Model model, Principal principal) {
        User me = currentUser(principal);
        Optional<NotificationMessage> detail = notificationService.getReadableDetail(id, me);
        if (detail.isEmpty()) {
            model.addAttribute("error", "通知不存在、未发布或无权查看");
            return "redirect:/notification/list";
        }
        NotificationMessage m = detail.get();
        model.addAttribute("isManager", notificationService.canManage(me));
        model.addAttribute("canEditThis", notificationService.canEditNotification(me, m));
        model.addAttribute("notice", m);
        model.addAttribute("copyText", notificationService.buildCopyText(m));
        return "notification/detail";
    }

    /**
     * 不在 mapping 上限制 produces：部分阅读器只发 Accept: application/atom+xml，
     * 限制后会匹配失败并落到 /error（需登录）→ 302。
     */
    @GetMapping("/rss/public.xml")
    @ResponseBody
    public ResponseEntity<byte[]> publicRss(HttpServletRequest request) {
        String base = requestBaseUrl(request);
        String self = base + "/notification/rss/public.xml";
        List<NotificationMessage> items = notificationService.listPublicPublished(50);
        String xml = NotificationRssBuilder.build(
                "冰壶大师赛 · 公共消息通知",
                base + "/notification/list",
                "无需登录即可订阅的公共消息通知",
                self,
                base + "/notification/detail/",
                items);
        return rssResponse(xml);
    }

    @GetMapping("/rss/u/{token}.xml")
    @ResponseBody
    public ResponseEntity<byte[]> personalRss(@PathVariable String token, HttpServletRequest request) {
        User owner = userService.findByRssToken(token);
        if (owner == null) {
            return ResponseEntity.notFound().build();
        }
        String base = requestBaseUrl(request);
        String self = base + "/notification/rss/u/" + token + ".xml";
        List<NotificationMessage> items = notificationService.listReadableForUser(owner, 50);
        String xml = NotificationRssBuilder.build(
                "冰壶大师赛 · 专属消息通知（" + owner.getUsername() + "）",
                base + "/notification/list",
                "按账号权限与当前已报名/主办赛事筛选的专属订阅源",
                self,
                base + "/notification/detail/",
                items);
        return rssResponse(xml);
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

    @PostMapping("/mark-all-read")
    public String markAllRead(Principal principal, RedirectAttributes ra) {
        User me = currentUser(principal);
        if (me == null) {
            ra.addFlashAttribute("error", "请先登录");
            return "redirect:/user/login";
        }
        try {
            int updated = notificationService.markAllRead(me.getId());
            if (updated <= 0) {
                ra.addFlashAttribute("message", "当前没有未读消息");
            } else {
                ra.addFlashAttribute("message", "已将 " + updated + " 条消息标为已读");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "操作失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage()));
        }
        return "redirect:/notification/list";
    }

    @GetMapping("/reset-password-status/{id}")
    @ResponseBody
    public java.util.Map<String, Object> getResetPasswordStatus(@PathVariable Long id, Principal principal) {
        User me = currentUser(principal);
        java.util.Map<String, Object> result = new java.util.HashMap<>();

        if (me == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }

        try {
            var status = notificationService.getResetPasswordRecipientStatus(id, me.getId());
            if (status.isEmpty()) {
                result.put("success", false);
                result.put("message", "无权限或状态不存在");
                return result;
            }

            var recipient = status.get();
            result.put("success", true);
            result.put("status", recipient.getResetPasswordStatus());

            if (recipient.getResetPasswordAt() != null) {
                result.put("resetTime", recipient.getResetPasswordAt().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }

            if (recipient.getResetPasswordByUsername() != null) {
                result.put("resetBy", recipient.getResetPasswordByUsername());
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }

        return result;
    }

    @PostMapping("/reset-password/{id}")
    @ResponseBody
    public java.util.Map<String, Object> resetPassword(@PathVariable Long id, Principal principal) {
        User me = currentUser(principal);
        java.util.Map<String, Object> result = new java.util.HashMap<>();

        if (me == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }

        try {
            var message = notificationService.getReadableDetail(id, me);
            if (message.isEmpty()) {
                result.put("success", false);
                result.put("message", "消息不存在");
                return result;
            }

            var msg = message.get();
            if (!"FORGOT_PASSWORD".equals(msg.getSourceType()) || msg.getSourceRefId() == null) {
                result.put("success", false);
                result.put("message", "无效的忘记密码请求");
                return result;
            }

            notificationService.resetUserPassword(id, me.getId(), msg.getSourceRefId());

            result.put("success", true);
            result.put("message", "密码重置成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }

        return result;
    }

    private User currentUser(Principal principal) {
        return principal == null ? null : userService.findByUsername(principal.getName());
    }

    private String requestBaseUrl(HttpServletRequest request) {
        if (!configuredPublicBaseUrl.isEmpty()) {
            return trimTrailingSlash(configuredPublicBaseUrl);
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost != null && !forwardedHost.isBlank()) {
            String proto = (forwardedProto == null || forwardedProto.isBlank()) ? "https" : forwardedProto.split(",")[0].trim();
            String host = forwardedHost.split(",")[0].trim();
            return proto + "://" + host;
        }
        String url = request.getRequestURL().toString();
        String uri = request.getRequestURI();
        String base;
        if (uri != null && !uri.isEmpty() && url.endsWith(uri)) {
            base = url.substring(0, url.length() - uri.length());
        } else {
            base = request.getScheme() + "://" + request.getServerName()
                    + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : (":" + request.getServerPort()));
        }
        return trimTrailingSlash(base);
    }

    private static String trimTrailingSlash(String base) {
        if (base == null || base.isEmpty()) {
            return "";
        }
        String b = base.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b;
    }

    private static ResponseEntity<byte[]> rssResponse(String xml) {
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType(RSS_MEDIA.getType(), RSS_MEDIA.getSubtype(), StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=120")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(body);
    }

    /** 是否像本机/内网地址（外网订阅器拉不到） */
    static boolean looksLocalOrPrivate(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return true;
        }
        String u = baseUrl.toLowerCase(Locale.ROOT);
        return u.contains("://127.") || u.contains("://localhost") || u.contains("://0.0.0.0")
                || u.contains("://192.168.") || u.contains("://10.") || u.contains("://172.16.")
                || u.contains("://172.17.") || u.contains("://172.18.") || u.contains("://172.19.")
                || u.contains("://172.2") || u.contains("://172.3");
    }
}

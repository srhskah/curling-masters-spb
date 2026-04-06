package com.example.controller;

import com.example.entity.NotificationMessage;
import com.example.entity.User;
import com.example.service.INotificationService;
import com.example.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/notification/api")
public class NotificationApiController {
    private final INotificationService notificationService;
    private final UserService userService;

    public NotificationApiController(INotificationService notificationService,
                                     UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Principal principal) {
        User me = currentUser(principal);
        return Map.of("count", me == null ? 0L : notificationService.unreadCount(me.getId()));
    }

    @PostMapping("/save")
    public ResponseEntity<?> save(@RequestParam(required = false) Long id,
                                  @RequestParam String title,
                                  @RequestParam("contentMarkdown") String markdown,
                                  @RequestParam(defaultValue = "false") boolean publishToHome,
                                  @RequestParam(defaultValue = "false") boolean publishNow,
                                  Principal principal) {
        User me = currentUser(principal);
        if (!notificationService.canManage(me)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "无权限"));
        }
        try {
            NotificationMessage m = notificationService.createOrUpdate(id, title, markdown, publishToHome, publishNow, me);
            return ResponseEntity.ok(Map.of("success", true, "id", m.getId()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/mark-unread/{messageId}")
    public ResponseEntity<?> markUnread(@PathVariable Long messageId, Principal principal) {
        User me = currentUser(principal);
        if (me == null) return ResponseEntity.status(401).body(Map.of("success", false));
        notificationService.markUnread(messageId, me.getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    private User currentUser(Principal principal) {
        return principal == null ? null : userService.findByUsername(principal.getName());
    }
}

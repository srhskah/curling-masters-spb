package com.example.controller;

import com.example.entity.User;
import com.example.service.INotificationService;
import com.example.service.UserService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

@ControllerAdvice
public class GlobalModelAttributeControllerAdvice {
    private final UserService userService;
    private final INotificationService notificationService;

    public GlobalModelAttributeControllerAdvice(UserService userService, INotificationService notificationService) {
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @ModelAttribute("notificationUnreadCount")
    public long notificationUnreadCount(Principal principal) {
        if (principal == null) return 0L;
        User me = userService.findByUsername(principal.getName());
        return me == null ? 0L : notificationService.unreadCount(me.getId());
    }
}

package com.example.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.entity.User;
import com.example.service.UserService;
import com.example.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author Curling Masters
 * @since 2026-03-04
 */
@Controller
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/test")
    @ResponseBody
    public String test(){
        return "ok";
    }

    // 显示登录页面
    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                           @RequestParam(value = "logout", required = false) String logout,
                           Model model, HttpServletRequest request) {
        if (error != null) {
            model.addAttribute("error", "用户名或密码错误");
        }
        if (logout != null) {
            model.addAttribute("message", "您已成功退出登录");
        }
        
        // 从Cookie读取记住的用户名
        String rememberedUsername = CookieUtil.getCookie(request, "remembered_username");
        if (rememberedUsername != null) {
            model.addAttribute("rememberedUsername", rememberedUsername);
            model.addAttribute("rememberMe", true);
        }
        
        // 读取用户偏好设置
        String theme = CookieUtil.getCookie(request, "user_theme");
        String language = CookieUtil.getCookie(request, "user_language");
        if (theme != null) {
            model.addAttribute("userTheme", theme);
        }
        if (language != null) {
            model.addAttribute("userLanguage", language);
        }
        
        return "login";
    }

    // 显示注册页面
    @GetMapping("/register")
    public String registerPage() {
        return "register-cookie";
    }

    // 处理注册
    @PostMapping("/register")
    public String register(@RequestParam String username,
                          @RequestParam String password,
                          @RequestParam String email,
                          @RequestParam(value = "rememberMe", required = false, defaultValue = "false") Boolean rememberMe,
                          @RequestParam(value = "theme", required = false) String theme,
                          @RequestParam(value = "language", required = false) String language,
                          HttpServletResponse response,
                          RedirectAttributes redirectAttributes) {
        try {
            userService.register(username, password, email);
            
            // 保存用户偏好设置到Cookie
            CookieUtil.saveUserPreferences(response, theme, language, rememberMe);
            
            redirectAttributes.addFlashAttribute("success", "注册成功，请登录");
            return "redirect:/user/login";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/user/register";
        }
    }

    // 登录成功处理
    @PostMapping("/login-success")
    public String loginSuccess(@RequestParam(value = "rememberMe", required = false, defaultValue = "false") Boolean rememberMe,
                              @RequestParam(value = "theme", required = false) String theme,
                              @RequestParam(value = "language", required = false) String language,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            // 如果选择记住我，保存用户名到Cookie
            if (rememberMe) {
                CookieUtil.addCookie(response, "remembered_username", username, 7 * 24 * 60 * 60); // 7天
            } else {
                CookieUtil.removeCookie(response, "remembered_username");
            }
            
            // 保存用户偏好设置
            CookieUtil.saveUserPreferences(response, theme, language, rememberMe);
            
            // 保存登录状态
            String token = java.util.UUID.randomUUID().toString();
            CookieUtil.saveLoginCookie(response, username, token);
            
        } catch (Exception e) {
            // 记录日志但不影响登录流程
            System.err.println("保存Cookie失败: " + e.getMessage());
        }
        
        return "redirect:/";
    }

    // 显示修改密码页面
    @GetMapping("/change-password")
    public String changePasswordPage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            model.addAttribute("username", auth.getName());
        }
        return "change-password";
    }

    // 处理修改密码
    @PostMapping("/change-password")
    public String changePassword(@RequestParam String newPassword,
                               @RequestParam String confirmPassword,
                               RedirectAttributes redirectAttributes) {
        try {
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "两次输入的密码不一致");
                return "redirect:/user/change-password";
            }
            
            if (newPassword.length() < 6) {
                redirectAttributes.addFlashAttribute("error", "密码长度不能少于6位");
                return "redirect:/user/change-password";
            }
            
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", username);
            User user = userService.getOne(queryWrapper);

            // 检查新密码是否与当前密码相同
            if (passwordEncoder.matches(newPassword, user.getPassword())) {
                redirectAttributes.addFlashAttribute("error", "新密码不能与当前密码相同");
                return "redirect:/user/change-password";
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setPasswordChanged(false); // 标记为已修改密码
            user.setUpdatedAt(LocalDateTime.now());
            
            userService.updateById(user);
            
            redirectAttributes.addFlashAttribute("success", "密码修改成功！");
            return "redirect:/";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "修改密码失败：" + e.getMessage());
            return "redirect:/user/change-password";
        }
    }

    // 显示重置密码页面
    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "reset-password";
    }

    // 处理重置密码
    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String newPassword,
                               RedirectAttributes redirectAttributes) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", username);
            User user = userService.getOne(queryWrapper);

            userService.resetPassword(user.getId(), newPassword);
            redirectAttributes.addFlashAttribute("success", "密码重置成功");
            return "redirect:/";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/user/reset-password";
        }
    }

    // 用户管理页面（仅限超级管理员和普通管理员）
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/manage")
    public String manageUsers(Model model) {
        List<User> users = userService.list();
        model.addAttribute("users", users);
        return "user-manage";
    }

    // 编辑用户页面（仅限超级管理员）
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/edit/{id}")
    public String editUserPage(@PathVariable Long id, Model model) {
        User user = userService.getById(id);
        model.addAttribute("user", user);
        return "user-edit";
    }

    // 更新用户信息（仅限超级管理员）
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/edit/{id}")
    public String updateUser(@PathVariable Long id,
                            @RequestParam String email,
                            @RequestParam Integer role,
                            RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getById(id);
            if (user == null) {
                redirectAttributes.addFlashAttribute("error", "用户不存在");
                return "redirect:/user/manage";
            }

            // 检查超级管理员数量限制
            if (role == 0 && user.getRole() != 0 && userService.existsSuperAdmin()) {
                redirectAttributes.addFlashAttribute("error", "已存在超级管理员，不能设置多个超级管理员");
                return "redirect:/user/manage";
            }

            user.setEmail(email);
            user.setRole(role);
            userService.updateById(user);
            redirectAttributes.addFlashAttribute("success", "用户信息更新成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "更新失败：" + e.getMessage());
        }
        return "redirect:/user/manage";
    }

    // 删除用户（仅限超级管理员）
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.removeById(id);
            redirectAttributes.addFlashAttribute("success", "用户删除成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "删除失败：" + e.getMessage());
        }
        return "redirect:/user/manage";
    }

    // 重置用户密码（超级管理员和普通管理员都可以使用）
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @PostMapping("/reset-password/{id}")
    public String resetUserPassword(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getById(id);
            if (user == null) {
                redirectAttributes.addFlashAttribute("error", "用户不存在");
                return "redirect:/user/manage";
            }

            // 重置密码为123456
            String defaultPassword = "123456";
            user.setPassword(passwordEncoder.encode(defaultPassword));
            user.setPasswordChanged(true); // 设置为需要修改密码
            user.setUpdatedAt(LocalDateTime.now());
            
            userService.updateById(user);
            
            redirectAttributes.addFlashAttribute("success", 
                String.format("用户 %s 的密码已重置为：123456，该用户下次登录时必须修改密码", user.getUsername()));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "重置密码失败：" + e.getMessage());
        }
        return "redirect:/user/manage";
    }
}

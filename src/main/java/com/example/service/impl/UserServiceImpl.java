package com.example.service.impl;

import com.example.dto.GroupImportResult;
import com.example.util.GroupMemberImportParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.entity.User;
import com.example.mapper.UserMapper;
import com.example.service.UserService;
import com.example.util.UsernameValidator;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Curling Masters
 * @since 2026-03-04
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();


    @Override
    public User register(String username, String password, String email) {
        // 验证用户名格式和安全性
        UsernameValidator.ValidationResult validation = UsernameValidator.validateUsername(username);
        if (!validation.isValid()) {
            throw new RuntimeException(validation.getMessage());
        }

        // 清理用户名
        String cleanUsername = UsernameValidator.sanitizeUsername(username);
        
        // 检查用户名是否已存在（使用清理后的用户名）
        if (existsByUsername(cleanUsername)) {
            throw new RuntimeException("用户名已存在");
        }

        // 检查超级管理员逻辑
        int role = 2; // 默认普通用户
        if (UsernameValidator.containsAdminKeyword(cleanUsername)) {
            if (existsSuperAdmin()) {
                throw new RuntimeException("已存在超级管理员，不能注册包含管理员关键词的账号");
            }
            role = 0; // 超级管理员
        }

        User user = new User();
        user.setUsername(cleanUsername);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setRole(role);
        user.setPasswordChanged(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        save(user);
        return user;
    }

    @Override
    public User login(String username, String password) {
        // 验证用户名格式和安全性
        UsernameValidator.ValidationResult validation = UsernameValidator.validateUsername(username);
        if (!validation.isValid()) {
            throw new RuntimeException(validation.getMessage());
        }

        // 清理用户名
        String cleanUsername = UsernameValidator.sanitizeUsername(username);
        
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", cleanUsername);
        User user = getOne(queryWrapper);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        return user;
    }

    @Override
    public boolean resetPassword(Long userId, String newPassword) {
        User user = getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 检查新密码是否与当前密码相同
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new RuntimeException("新密码不能与当前密码相同");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChanged(true);
        user.setUpdatedAt(LocalDateTime.now());

        return updateById(user);
    }

    @Override
    public boolean existsByUsername(String username) {
        // 验证用户名格式
        UsernameValidator.ValidationResult validation = UsernameValidator.validateUsername(username);
        if (!validation.isValid()) {
            return false; // 无效用户名视为不存在
        }

        // 清理用户名
        String cleanUsername = UsernameValidator.sanitizeUsername(username);
        
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", cleanUsername);
        return count(queryWrapper) > 0;
    }

    @Override
    public boolean existsSuperAdmin() {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("role", 0);
        return count(queryWrapper) > 0;
    }

    @Override
    public List<String> batchAddUsers(String usernames) {
        List<String> createdUsers = new ArrayList<>();
        String[] userArray = usernames.split("[,，;；\\t\\n]+");
        for (String username : userArray) {
            username = username.trim();
            if (username.isEmpty()) continue;
            // Validate username
            UsernameValidator.ValidationResult validation = UsernameValidator.validateUsername(username);
            if (!validation.isValid()) continue;
            String cleanUsername = UsernameValidator.sanitizeUsername(username);
            // Check if exists
            if (existsByUsername(cleanUsername)) continue;
            User user = new User();
            user.setUsername(cleanUsername);
            user.setPassword(passwordEncoder.encode("123456"));
            user.setEmail("lorem.ipsum@example.com");
            user.setRole(2);
            user.setPasswordChanged(true);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            save(user);
            createdUsers.add(cleanUsername);
        }
        return createdUsers;
    }

    @Override
    public User findByUsername(String username) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        return getOne(queryWrapper);
    }

    @Override
    public GroupImportResult resolveGroupImport(String raw) {
        List<User> all = list();
        Map<String, Long> byName = new HashMap<>();
        List<String> sortedNames = new ArrayList<>();
        for (User u : all) {
            if (u.getUsername() != null && !u.getUsername().isEmpty()) {
                byName.put(u.getUsername(), u.getId());
                sortedNames.add(u.getUsername());
            }
        }
        sortedNames.sort(Comparator.comparingInt(String::length).reversed());
        List<String> tokens = GroupMemberImportParser.parseUserTokens(raw, sortedNames);
        List<Long> ids = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        for (String token : tokens) {
            Long id = byName.get(token);
            if (id == null) {
                unknown.add(token);
            } else if (seen.add(id)) {
                ids.add(id);
            }
        }
        return new GroupImportResult(ids, unknown);
    }
}
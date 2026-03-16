package com.example.service;

import com.example.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Curling Masters
 * @since 2026-03-04
 */
public interface UserService extends IService<User> {

    
    /**
     * 用户注册
     * @param username 用户名
     * @param password 密码
     * @param email 邮箱
     * @return 注册成功的用户
     */
    User register(String username, String password, String email);

    /**
     * 用户登录验证
     * @param username 用户名
     * @param password 密码
     * @return 用户信息
     */
    User login(String username, String password);

    /**
     * 重置密码
     * @param userId 用户ID
     * @param newPassword 新密码
     * @return 是否成功
     */
    boolean resetPassword(Long userId, String newPassword);

    /**
     * 检查用户名是否存在
     * @param username 用户名
     * @return 是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查是否已存在超级管理员
     * @return 是否存在
     */
    boolean existsSuperAdmin();

    /**
     * 批量添加用户
     * @param usernames 用户名字符串，用分隔符分隔
     * @return 成功创建的用户列表
     */
    List<String> batchAddUsers(String usernames);

    /**
     * 根据用户名查找用户
     * @param username 用户名
     * @return 用户信息
     */
    User findByUsername(String username);
}
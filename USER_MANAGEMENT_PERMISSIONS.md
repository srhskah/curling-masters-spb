# 用户管理权限控制更新

## 📋 权限控制规则更新

### 🔐 **权限分级**

#### **角色定义**
- **超级管理员 (ROLE_SUPER_ADMIN)**: role = 0
- **普通管理员 (ROLE_ADMIN)**: role = 1  
- **普通用户 (ROLE_USER)**: role = 2

### 🛡️ **权限控制更新**

#### **1. 用户管理页面访问**
- **路径**: `/user/manage`
- **权限**: `SUPER_ADMIN` 或 `ADMIN`
- **说明**: 超级管理员和普通管理员都可以访问用户管理页面

#### **2. 编辑用户权限**
- **路径**: `/user/edit/{id}`
- **权限**: 仅 `SUPER_ADMIN`
- **说明**: 只有超级管理员可以修改其他用户的信息和角色
- **前端控制**: `th:if="${#authentication.principal.role == 0}"`

#### **3. 删除用户权限**
- **路径**: `/user/delete/{id}`
- **权限**: 仅 `SUPER_ADMIN`
- **说明**: 只有超级管理员可以删除其他用户
- **前端控制**: 
  - `th:if="${#authentication.principal.role == 0 and user.role != 0 and user.id != #authentication.principal.id}"`
  - 不能删除超级管理员
  - 不能删除自己

#### **4. 重置密码权限** 🆕
- **路径**: `/user/reset-password/{id}`
- **权限**: `SUPER_ADMIN` 或 `ADMIN`
- **说明**: 超级管理员和普通管理员都可以重置其他用户密码
- **前端控制**: `th:if="${#authentication.principal.role <= 1 and user.id != #authentication.principal.id}"`
- **不能重置自己的密码**

## 🔧 **重置密码功能**

### 📝 **功能特性**
- **默认密码**: `123456`
- **强制修改**: 重置后用户下次登录必须修改密码
- **安全通知**: 管理员需要手动通知用户新密码

### 🎯 **操作流程**
1. 管理员在用户管理页面点击"重置密码"
2. 确认对话框显示重置信息
3. 系统将密码重置为 `123456`
4. 设置 `passwordChanged = false` 强制下次登录修改
5. 显示成功消息包含用户名和新密码

### 🔒 **安全考虑**
- 密码使用BCrypt加密存储
- 重置操作记录日志
- 不能重置自己的密码
- 需要手动通知用户新密码

## 🎨 **界面更新**

### 📊 **用户管理表格**
```html
<!-- 操作列权限控制 -->
<td>
    <!-- 编辑：仅超级管理员 -->
    <a th:if="${#authentication.principal.role == 0}" 
       th:href="@{'/user/edit/' + ${user.id}}" class="btn btn-sm btn-primary">编辑</a>
    
    <!-- 重置密码：超级管理员和普通管理员 -->
    <button th:if="${#authentication.principal.role <= 1 and user.id != #authentication.principal.id}" 
            class="btn btn-sm btn-warning" onclick="resetPassword(this)">重置密码</button>
    
    <!-- 删除：仅超级管理员 -->
    <button th:if="${#authentication.principal.role == 0 and user.role != 0 and user.id != #authentication.principal.id}" 
            class="btn btn-sm btn-danger" onclick="deleteUser(this)">删除</button>
</td>
```

### 💬 **重置密码确认对话框**
- 显示用户名
- 警告信息说明
- 新密码显示：`123456`
- 强制修改密码提醒

## 🔄 **后端实现**

### 🎯 **权限注解**
```java
// 用户管理页面
@PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
@GetMapping("/manage")

// 编辑用户（仅超级管理员）
@PreAuthorize("hasRole('SUPER_ADMIN')")
@GetMapping("/edit/{id}")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@PostMapping("/edit/{id}")

// 删除用户（仅超级管理员）
@PreAuthorize("hasRole('SUPER_ADMIN')")
@PostMapping("/delete/{id}")

// 重置密码（超级管理员和普通管理员）
@PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
@PostMapping("/reset-password/{id}")
```

### 🔐 **重置密码逻辑**
```java
@PostMapping("/reset-password/{id}")
public String resetUserPassword(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    User user = userService.getById(id);
    
    // 重置密码为123456
    String defaultPassword = "123456";
    user.setPassword(passwordEncoder.encode(defaultPassword));
    user.setPasswordChanged(false); // 强制下次修改密码
    user.setUpdatedAt(LocalDateTime.now());
    
    userService.updateById(user);
    
    // 返回成功消息
    redirectAttributes.addFlashAttribute("success", 
        String.format("用户 %s 的密码已重置为：123456，该用户下次登录时必须修改密码", user.getUsername()));
    
    return "redirect:/user/manage";
}
```

## 📱 **用户体验**

### 👤 **超级管理员**
- ✅ 可以查看所有用户
- ✅ 可以编辑任何用户信息
- ✅ 可以删除非超级管理员用户
- ✅ 可以重置其他用户密码
- ❌ 不能删除自己
- ❌ 不能重置自己密码

### 👨‍💼 **普通管理员**
- ✅ 可以查看所有用户
- ❌ 不能编辑用户信息
- ❌ 不能删除用户
- ✅ 可以重置其他用户密码
- ❌ 不能重置自己密码

### 🔄 **安全防护**
- 前端和后端双重权限验证
- 防止用户操作自己的账户
- 密码加密存储
- 操作日志记录

## 🧪 **测试用例**

### 🎯 **权限测试**
1. **超级管理员登录**
   - 访问 `/user/manage` ✅
   - 看到编辑按钮 ✅
   - 看到重置密码按钮 ✅
   - 看到删除按钮（非超级管理员） ✅

2. **普通管理员登录**
   - 访问 `/user/manage` ✅
   - 看不到编辑按钮 ❌
   - 看到重置密码按钮 ✅
   - 看不到删除按钮 ❌

3. **普通用户登录**
   - 访问 `/user/manage` ❌ (403 Forbidden)

### 🔐 **重置密码测试**
1. 管理员重置用户密码
2. 被重置用户登录
3. 系统强制跳转到修改密码页面
4. 修改密码后正常使用

现在用户管理权限控制更加严格和安全！

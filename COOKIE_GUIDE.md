# Cookie 功能使用指南

本项目已完整实现了 Cookie 保存和管理功能，包括用户偏好设置、登录状态记忆、Cookie 同意管理等。

## 🍪 功能概览

### 1. Cookie 工具类 (`CookieUtil.java`)
- **位置**: `src/main/java/com/example/util/CookieUtil.java`
- **功能**: 提供完整的 Cookie 操作 API
- **特性**:
  - URL 编码/解码（支持中文）
  - 灵活的过期时间设置
  - 安全属性配置
  - 批量操作支持

### 2. Cookie 控制器 (`CookieController.java`)
- **位置**: `src/main/java/com/example/controller/CookieController.java`
- **功能**: RESTful Cookie 管理 API
- **端点**:
  - `GET /cookie/view` - 查看所有 Cookie
  - `POST /cookie/set` - 设置 Cookie
  - `DELETE /cookie/remove` - 删除指定 Cookie
  - `DELETE /cookie/clear` - 清除所有应用 Cookie
  - `GET /cookie/preferences` - 获取用户偏好
  - `POST /cookie/preferences` - 保存用户偏好

### 3. 用户偏好设置
- **主题设置**: 浅色/深色/自动
- **语言设置**: 简体中文/繁体中文/English
- **记住用户名**: 登录时自动填充
- **登录状态**: 保持会话

## 🎯 使用场景

### 1. 登录页面
```html
<!-- 使用增强版登录页面 -->
<form th:action="@{/user/register}" method="post">
    <!-- 记住用户名 -->
    <input type="checkbox" name="rememberMe">
    
    <!-- 主题偏好 -->
    <select name="theme">
        <option value="light">浅色主题</option>
        <option value="dark">深色主题</option>
        <option value="auto">跟随系统</option>
    </select>
    
    <!-- 语言偏好 -->
    <select name="language">
        <option value="zh-CN">简体中文</option>
        <option value="zh-TW">繁体中文</option>
        <option value="en">English</option>
    </select>
</form>
```

### 2. 在控制器中读取 Cookie
```java
@GetMapping("/profile")
public String profile(HttpServletRequest request, Model model) {
    String theme = CookieUtil.getCookie(request, "user_theme");
    String language = CookieUtil.getCookie(request, "user_language");
    Boolean rememberMe = "true".equals(CookieUtil.getCookie(request, "remember_me"));
    
    model.addAttribute("userTheme", theme);
    model.addAttribute("userLanguage", language);
    model.addAttribute("rememberMe", rememberMe);
    
    return "profile";
}
```

### 3. JavaScript 中使用 Cookie
```javascript
// 设置 Cookie
CookieManager.setCookie('user_preference', 'dark-theme', 30);

// 获取 Cookie
const theme = CookieManager.getCookie('user_theme');

// 删除 Cookie
CookieManager.deleteCookie('user_preference');

// 获取所有 Cookie
const allCookies = CookieManager.getAllCookies();
```

## 🔧 API 使用示例

### 1. 设置 Cookie
```bash
curl -X POST http://localhost:8080/cookie/set \
  -d "name=user_theme" \
  -d "value=dark" \
  -d "maxAge=86400"
```

### 2. 获取 Cookie
```bash
curl http://localhost:8080/cookie/view
```

### 3. 删除 Cookie
```bash
curl -X DELETE http://localhost:8080/cookie/remove?name=user_theme
```

### 4. 保存用户偏好
```bash
curl -X POST http://localhost:8080/cookie/preferences \
  -d "theme=dark" \
  -d "language=zh-CN" \
  -d "rememberMe=true"
```

## 🎨 Cookie 同意横幅

### 1. 在模板中添加横幅
```html
<!-- 在 body 标签末尾添加 -->
<div th:replace="~{fragments :: cookie-banner}"></div>
<div th:replace="~{fragments :: cookie-settings-modal}"></div>
```

### 2. 初始化 Cookie 同意
```javascript
// 页面加载完成后显示横幅
document.addEventListener('DOMContentLoaded', function() {
    CookieConsent.showBanner();
    UserPreferences.init();
});
```

### 3. 自定义同意逻辑
```javascript
// 检查用户同意状态
if (CookieConsent.hasConsent()) {
    const preferences = CookieConsent.getPreferences();
    if (preferences.analytics) {
        // 启用 Google Analytics
        gtag('config', 'GA_MEASUREMENT_ID');
    }
}
```

## 📱 移动端适配

Cookie 横幅已完全适配移动端：
- 响应式布局
- 触摸友好的按钮
- 自适应文字大小

## 🔒 安全考虑

### 1. HttpOnly Cookie
```java
// 设置 HttpOnly 属性（防止 XSS 攻击）
CookieUtil.addCookie(response, "auth_token", token, 3600, "/", null, true);
```

### 2. Secure Cookie
```java
// 生产环境启用 Secure 属性（仅 HTTPS 传输）
cookie.setSecure(true);
```

### 3. SameSite 属性
```java
// 防止 CSRF 攻击
response.setHeader("Set-Cookie", "sessionId=xxx; SameSite=Strict");
```

## 🧪 测试 Cookie 功能

### 1. 单元测试
```java
@Test
public void testCookieOperations() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    
    // 设置 Cookie
    CookieUtil.addCookie(response, "test", "value", 3600);
    
    // 验证 Cookie
    assertEquals("value", CookieUtil.getCookie(request, "test"));
}
```

### 2. 集成测试
```bash
# 启动应用
./gradlew bootRun

# 测试 Cookie API
curl -X POST http://localhost:8080/cookie/set \
  -d "name=test_cookie" \
  -d "value=test_value"

curl http://localhost:8080/cookie/view
```

## 📊 Cookie 类型说明

| Cookie 名称 | 用途 | 过期时间 | 示例值 |
|------------|------|----------|--------|
| `user_theme` | 用户主题偏好 | 30天 | `dark` |
| `user_language` | 用户语言偏好 | 30天 | `zh-CN` |
| `remembered_username` | 记住的用户名 | 7天 | `admin` |
| `remember_me` | 记住我选项 | 7天 | `true` |
| `login_token` | 登录令牌 | 7天 | `uuid-string` |
| `cookie_consent` | Cookie 同意状态 | 365天 | `true` |
| `cookie_preferences` | Cookie 偏好设置 | 365天 | JSON字符串 |

## 🚀 最佳实践

### 1. Cookie 命名规范
- 使用下划线分隔
- 前缀标识应用或功能
- 避免使用特殊字符

### 2. 过期时间设置
- 敏感信息：较短过期时间
- 用户偏好：较长过期时间
- 临时数据：会话级别

### 3. 数据大小限制
- 单个 Cookie ≤ 4KB
- 总 Cookie 数 ≤ 50个
- 域名总 Cookie ≤ 20个

### 4. 性能优化
- 避免在 Cookie 中存储大量数据
- 定期清理过期 Cookie
- 使用压缩减少存储空间

## 🔧 故障排除

### 1. Cookie 不生效
- 检查域名和路径设置
- 确认过期时间配置
- 验证浏览器 Cookie 设置

### 2. 中文乱码
- 确保使用 URL 编码
- 检查字符集设置
- 验证浏览器编码

### 3. 跨域问题
- 配置正确的域名
- 设置 SameSite 属性
- 检查 CORS 设置

## 📝 更新日志

### v1.0.0 (2026-03-05)
- ✅ 基础 Cookie 工具类
- ✅ 用户偏好设置
- ✅ Cookie 同意管理
- ✅ RESTful API 接口
- ✅ 移动端适配
- ✅ 安全属性配置

现在你的应用已经具备了完整的 Cookie 功能！用户可以享受个性化的浏览体验，同时符合现代隐私保护要求。

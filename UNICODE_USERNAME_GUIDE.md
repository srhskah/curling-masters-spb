# Unicode 用户名支持指南

本项目已完全支持Unicode字符和emoji用户名，同时确保防止SQL注入和其他安全威胁。

## 🌍 支持的字符类型

### 1. **Unicode字符**
- ✅ **中文**: 张三、李四、王五
- ✅ **日文**: さくら、太郎、ユーザー
- ✅ **韩文**: 김철수、이영희、사용자
- ✅ **阿拉伯文**: أحمد、محمد、مستخدم
- ✅ **俄文**: Иван、Анна、пользователь
- ✅ **泰文**: สมชาย、สมศรี、ผู้ใช้

### 2. **Emoji字符**
- ✅ **表情**: 😊、😎、🎯、🏆
- ✅ **符号**: ⭐、💎、🔥、⚡
- ✅ **运动**: 🏸、🎾、⚽、🏀
- ✅ **动物**: 🐉、🦅、🐺、🦊

### 3. **其他字符**
- ✅ **英文字母**: a-z, A-Z
- ✅ **数字**: 0-9
- ✅ **下划线**: _
- ✅ **连字符**: -
- ✅ **空白字符**: 各种Unicode空白

## 🛡️ 安全防护

### 1. **SQL注入防护**
```java
// 检测SQL注入模式
private static final Pattern[] SQL_INJECTION_PATTERNS = {
    Pattern.compile("(?i)(union|select|insert|update|delete|drop|create|alter|exec|execute)"),
    Pattern.compile("(?i)(script|javascript|vbscript|onload|onerror)"),
    Pattern.compile("(?i)(<|>|\"|'|--|;|/\\*|\\*/|xp_|sp_)"),
    Pattern.compile("(?i)(or|and)\\s+\\d+\\s*=\\s*\\d+"),
    Pattern.compile("(?i)(or|and)\\s+'[^']*'\\s*=\\s*'[^']*'")
};
```

### 2. **危险字符过滤**
- ❌ HTML标签: `<`, `>`, `"`, `'`
- ❌ 脚本注入: `script`, `javascript`, `onload`
- ❌ SQL关键字: `union`, `select`, `drop`
- ❌ 控制字符: ASCII 0-31, 127

### 3. **输入验证**
- ✅ 长度限制: 2-50字符
- ✅ 字符类型验证
- ✅ Unicode规范化
- ✅ HTML转义输出

## 📊 数据库支持

### 1. **UTF8MB4配置**
```properties
# 数据库连接配置
spring.datasource.url=${MYSQL_DB_URL}

# Jackson JSON配置
spring.jackson.charset=UTF-8
spring.http.encoding.charset=UTF-8
spring.http.encoding.enabled=true
spring.http.encoding.force=true
```

### 2. **数据库迁移**
```sql
-- 更新用户表为UTF8MB4
ALTER TABLE `user` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `user` 
MODIFY COLUMN `username` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
```

## 🔧 使用方法

### 1. **后端验证**
```java
// 在服务层使用验证器
UsernameValidator.ValidationResult validation = UsernameValidator.validateUsername(username);
if (!validation.isValid()) {
    throw new RuntimeException(validation.getMessage());
}

// 清理用户名
String cleanUsername = UsernameValidator.sanitizeUsername(username);
```

### 2. **前端验证**
```javascript
// 实时验证用户名
const result = FormValidator.validateUsername(username);
if (result.valid) {
    // 显示成功信息
    FormValidator.displayUsernameStats(username, 'statsContainer');
} else {
    // 显示错误信息
}
```

### 3. **模板使用**
```html
<div class="mb-3">
    <label for="username" class="form-label">
        用户名 <small class="text-muted">(支持中文、emoji、Unicode字符)</small>
    </label>
    <input type="text" class="form-control" id="username" name="username" 
           placeholder="例如：张三、Dragon、🎯玩家、ユーザー">
    <div id="usernameValidation" class="form-text"></div>
    <div id="usernameStats" class="form-text"></div>
</div>
```

## 📝 验证规则

### 1. **基本规则**
- 长度: 2-50字符
- 字符类型: Unicode字母、数字、符号、emoji
- 禁止: 控制字符、危险字符、SQL注入

### 2. **正则表达式**
```javascript
// 支持Unicode字符和emoji的正则表达式
const unicodePattern = /^[\p{L}\p{M}\p{N}\p{Zs}\p{So}\p{Sk}\p{Sm}_-]{2,50}$/u;
```

### 3. **字符统计**
```javascript
// 获取字符类型统计
const stats = FormValidator.getUsernameCharacterStats(username);
console.log(`字母: ${stats.letters}, 中文: ${stats.chinese}, Emoji: ${stats.emojis}`);
```

## 🎯 示例用户名

### ✅ **有效的用户名**
- `张三` (纯中文)
- `Dragon` (纯英文)
- `🎯玩家` (emoji + 中文)
- `ユーザー123` (日文 + 数字)
- `김철수-2024` (韩文 + 符号 + 数字)
- `Ahmed_Al` (阿拉伯文 + 英文 + 下划线)
- `😊🏆🎾` (纯emoji)
- `测试用户_2024` (中文 + 下划线 + 数字)

### ❌ **无效的用户名**
- `a` (太短)
- `user<script>` (包含脚本)
- `admin' OR '1'='1` (SQL注入)
- `user\x00` (包含控制字符)
- `very_long_username_that_exceeds_fifty_characters_limit` (太长)

## 🔄 API端点

### 1. **用户名验证**
```bash
# 前端验证
POST /cookie/set
Content-Type: application/x-www-form-urlencoded

name=test_username&value=🎯玩家&maxAge=3600
```

### 2. **字符统计**
```javascript
// 获取字符统计
const stats = FormValidator.getUsernameCharacterStats("🎯玩家张三");
// 返回: { letters: 2, chinese: 2, emojis: 1, digits: 0, others: 0, total: 5 }
```

## 🧪 测试用例

### 1. **中文用户名**
```java
@Test
void testChineseUsername() {
    ValidationResult result = UsernameValidator.validateUsername("张三");
    assertTrue(result.isValid());
}
```

### 2. **Emoji用户名**
```java
@Test
void testEmojiUsername() {
    ValidationResult result = UsernameValidator.validateUsername("🎯玩家");
    assertTrue(result.isValid());
}
```

### 3. **SQL注入防护**
```java
@Test
void testSqlInjectionPrevention() {
    ValidationResult result = UsernameValidator.validateUsername("admin' OR '1'='1");
    assertFalse(result.isValid());
    assertEquals("用户名包含不安全的内容", result.getMessage());
}
```

## 🚀 性能优化

### 1. **数据库优化**
- 使用UTF8MB4字符集
- 适当的索引长度
- 连接池配置

### 2. **前端优化**
- 实时验证反馈
- 防抖处理
- 字符统计缓存

### 3. **安全优化**
- 输入验证和清理
- 输出转义
- 参数化查询

## 📋 最佳实践

### 1. **开发建议**
- 始终验证用户输入
- 使用参数化查询
- 转义输出内容
- 记录安全事件

### 2. **用户体验**
- 实时验证反馈
- 清晰的错误提示
- 字符统计显示
- 多语言支持

### 3. **安全建议**
- 定期更新验证规则
- 监控异常输入
- 限制尝试次数
- 记录审计日志

## 🔍 故障排除

### 1. **字符显示问题**
- 检查数据库字符集
- 验证连接配置
- 确认页面编码

### 2. **验证失败**
- 检查正则表达式
- 验证Unicode支持
- 查看错误日志

### 3. **性能问题**
- 优化数据库查询
- 减少验证复杂度
- 使用缓存机制

现在你的应用完全支持Unicode用户名！用户可以使用中文、emoji、日文、韩文等各种字符创建独特的用户名，同时确保系统安全。

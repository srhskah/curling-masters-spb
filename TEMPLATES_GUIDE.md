# Thymeleaf 模板系统使用指南

本项目现在使用 Thymeleaf 模板片段来实现代码复用，大大简化了页面维护。

## 📁 模板文件结构

```
templates/
├── fragments.html          # 公共模板片段
├── layout.html            # 布局模板
├── home.html              # 首页（使用片段）
├── login.html             # 登录页（使用片段）
├── register.html          # 注册页
├── reset-password.html    # 重置密码页
├── user-edit.html         # 用户编辑页
├── user-manage.html       # 用户管理页
└── *-old.html            # 原始备份文件
```

## 🧩 可用的模板片段

### 1. 头部片段
```html
<head th:replace="~{fragments :: head('页面标题')}">
```
- 自动包含 Bootstrap CSS 和自定义 CSS
- 动态设置页面标题

### 2. 导航栏片段

#### 已登录用户导航栏
```html
<div th:replace="~{fragments :: navbar}"></div>
```
- 包含用户菜单
- 根据权限显示管理功能

#### 未登录用户导航栏
```html
<div th:replace="~{fragments :: navbar-login}"></div>
```
- 显示登录和注册链接

### 3. 页脚片段
```html
<div th:replace="~{fragments :: footer}"></div>
```

### 4. 脚本片段
```html
<th:block th:replace="~{fragments :: scripts}"></th:block>
```
- 包含 Bootstrap JS 和自定义 JS

### 5. 消息提示片段

#### 成功消息
```html
<div th:replace="~{fragments :: success-alert}"></div>
```

#### 错误消息
```html
<div th:replace="~{fragments :: error-alert}"></div>
```

#### 自定义消息
```html
<div th:replace="~{fragments :: alert('消息内容', 'success')}"></div>
```

## 🎨 使用布局模板

### 方法1：直接使用片段（推荐）
```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">

<head th:replace="~{fragments :: head('页面标题')}">
</head>

<body>
    <div th:replace="~{fragments :: navbar}"></div>
    
    <!-- 你的页面内容 -->
    <div class="container">
        <h1>你的内容</h1>
    </div>
    
    <div th:replace="~{fragments :: footer}"></div>
    <th:block th:replace="~{fragments :: scripts}"></th:block>
</body>
</html>
```

### 方法2：使用布局模板
```html
<html xmlns:th="http://www.thymeleaf.org" th:replace="~{layout :: layout(
  title = '页面标题',
  content = ~{::content}
)}">

<th:block th:fragment="content">
    <!-- 你的页面内容 -->
    <div class="container">
        <h1>你的内容</h1>
    </div>
</th:block>

</html>
```

## 🔄 更新现有模板

### 更新 register.html 示例
```bash
# 备份原文件
mv register.html register-old.html

# 创建新文件使用模板片段
# 参考 login.html 的结构
```

### 批量更新脚本
```bash
# 创建更新脚本
cat > update-templates.sh << 'EOF'
#!/bin/bash
for file in register.html reset-password.html user-edit.html user-manage.html; do
    if [ -f "$file" ] && [ ! -f "${file%.html}-old.html" ]; then
        mv "$file" "${file%.html}-old.html"
        echo "已备份: $file -> ${file%.html}-old.html"
    fi
done
EOF

chmod +x update-templates.sh
./update-templates.sh
```

## 🎯 最佳实践

### 1. 片段命名规范
- 使用有意义的名称：`navbar`, `footer`, `head`
- 按功能分组：导航相关、内容相关、脚本相关

### 2. 参数传递
- 动态标题：`head('页面标题')`
- 自定义消息：`alert('消息', '类型')`

### 3. 条件渲染
```html
<!-- 根据登录状态显示不同导航栏 -->
<div th:if="${loggedIn}" th:replace="~{fragments :: navbar}"></div>
<div th:unless="${loggedIn}" th:replace="~{fragments :: navbar-login}"></div>

<!-- 根据权限显示内容 -->
<div th:if="${#authorization.expression('hasRole(''ROLE_ADMIN'')')}">
    管理员内容
</div>
```

### 4. 样式一致性
- 所有页面使用相同的头部和脚本
- 统一的导航栏和页脚样式
- 一致的消息提示样式

## 🚀 优势

1. **代码复用**：导航栏、页脚等只需维护一份
2. **一致性**：所有页面样式统一
3. **易维护**：修改公共部分只需改一个地方
4. **灵活性**：可以传递参数自定义部分内容
5. **扩展性**：容易添加新的公共片段

## 📝 下一步

1. 更新剩余的模板文件使用片段
2. 添加更多公共片段（如侧边栏、面包屑等）
3. 考虑使用 Thymeleaf 布局方言进一步简化
4. 添加主题切换功能

现在你的模板系统更加专业和易于维护了！

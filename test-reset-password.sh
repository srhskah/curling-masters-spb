#!/bin/bash

echo "=== 测试重置密码功能 ==="
echo

# 获取管理员token（假设admin用户存在且密码是admin123）
echo "1. 尝试登录管理员账户..."
ADMIN_TOKEN=$(curl -s -c cookies.txt -b cookies.txt -X POST \
  -d "username=admin&password=admin123" \
  http://localhost:8080/login | grep -o "location: [^\s]*" | cut -d' ' -f2)

if [[ "$ADMIN_TOKEN" == *"/"* ]]; then
    echo "✓ 管理员登录成功"
else
    echo "✗ 管理员登录失败"
    exit 1
fi

echo
echo "2. 访问用户管理页面..."
curl -s -b cookies.txt http://localhost:8080/user/manage | grep -q "用户管理"
if [ $? -eq 0 ]; then
    echo "✓ 可以访问用户管理页面"
else
    echo "✗ 无法访问用户管理页面"
    exit 1
fi

echo
echo "3. 查找testuser用户ID..."
TESTUSER_ID=$(curl -s -b cookies.txt http://localhost:8080/user/manage | \
  grep -o 'resetPassword([^)]*' | grep -o "data-id=\"[^\"]*" | cut -d'"' -f2 | head -1)

if [ -n "$TESTUSER_ID" ]; then
    echo "✓ 找到testuser，ID: $TESTUSER_ID"
else
    echo "✗ 未找到testuser"
    exit 1
fi

echo
echo "4. 重置testuser密码..."
RESULT=$(curl -s -b cookies.txt -X POST \
  http://localhost:8080/user/reset-password/$TESTUSER_ID)

echo "$RESULT" | grep -q "密码已重置为：123456"
if [ $? -eq 0 ]; then
    echo "✓ testuser密码重置成功"
else
    echo "✗ testuser密码重置失败"
    echo "$RESULT"
fi

echo
echo "5. 清除cookies并尝试用testuser登录..."
rm -f cookies.txt
echo "   尝试用默认密码123456登录testuser..."

# 登录testuser
LOGIN_RESULT=$(curl -s -c cookies.txt -X POST \
  -d "username=testuser&password=123456" \
  http://localhost:8080/login)

echo "$LOGIN_RESULT" | grep -q "change-password"
if [ $? -eq 0 ]; then
    echo "✓ testuser被正确重定向到修改密码页面"
else
    echo "✗ testuser没有被重定向到修改密码页面"
    echo "$LOGIN_RESULT"
fi

echo
echo "6. 访问修改密码页面..."
CHANGE_PAGE=$(curl -s -b cookies.txt http://localhost:8080/user/change-password)
echo "$CHANGE_PAGE" | grep -q "修改密码"
if [ $? -eq 0 ]; then
    echo "✓ 可以访问修改密码页面"
else
    echo "✗ 无法访问修改密码页面"
fi

echo
echo "7. 测试修改密码功能..."
# 修改密码为新密码
NEW_PASSWORD="newpass123"
RESULT=$(curl -s -b cookies.txt -X POST \
  -d "newPassword=$NEW_PASSWORD&confirmPassword=$NEW_PASSWORD" \
  http://localhost:8080/user/change-password)

echo "$RESULT" | grep -q "密码修改成功"
if [ $? -eq 0 ]; then
    echo "✓ 密码修改成功"
else
    echo "✗ 密码修改失败"
    echo "$RESULT"
fi

echo
echo "8. 验证新密码可以正常登录..."
rm -f cookies.txt
LOGIN_RESULT=$(curl -s -c cookies.txt -X POST \
  -d "username=testuser&password=$NEW_PASSWORD" \
  http://localhost:8080/login)

echo "$LOGIN_RESULT" | grep -q "location: /"
if [ $? -eq 0 ]; then
    echo "✓ 新密码可以正常登录并跳转到首页"
else
    echo "✗ 新密码无法正常登录"
    echo "$LOGIN_RESULT"
fi

echo
echo "=== 测试完成 ==="
rm -f cookies.txt

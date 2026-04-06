// Main JavaScript file for Curling Masters

// Wait for DOM to be fully loaded
document.addEventListener('DOMContentLoaded', function() {
    console.log('Curling Masters - Main.js loaded');
    
    // Initialize all functions
    initializeAnimations();
    initializeFormValidation();
    initializeNavigation();
    initializeTooltips();
    initializeBackToTopButton();
    initializeGlobalCopyTextButtons();
});

/**
 * 全站统一：复制纯文本（支持 HTTPS / localhost 的 Clipboard API，否则 textarea + execCommand 降级）
 * @param {string|null|undefined} text
 */
async function copyPlainTextToClipboard(text) {
    const s = text == null ? '' : String(text);
    if (navigator.clipboard && (window.isSecureContext || location.hostname === 'localhost')) {
        // 优先用 text/plain Blob（UTF-8）写入，确保 Unicode / emoji 原样复制。
        if (window.ClipboardItem && navigator.clipboard.write) {
            const blob = new Blob([s], { type: 'text/plain;charset=utf-8' });
            await navigator.clipboard.write([new ClipboardItem({ 'text/plain': blob })]);
            return;
        }
        await navigator.clipboard.writeText(s);
        return;
    }
    const ta = document.createElement('textarea');
    ta.value = s;
    ta.style.position = 'fixed';
    ta.style.top = '-1000px';
    ta.style.left = '-1000px';
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    document.execCommand('copy');
    ta.remove();
}

/**
 * 复制后在按钮上显示反馈（可选）。若提供 button 且复制失败会还原 innerHTML 后抛出异常。
 * @param {string|null|undefined} text
 * @param {{ button?: HTMLElement, okHtml?: string, resetMs?: number }} [opts]
 */
async function copyPlainTextToClipboardWithButton(text, opts) {
    const o = opts || {};
    const btn = o.button;
    const oldHtml = btn ? btn.innerHTML : null;
    try {
        await copyPlainTextToClipboard(text);
        if (btn) {
            btn.innerHTML = o.okHtml != null ? o.okHtml : '已复制';
            const ms = o.resetMs != null ? o.resetMs : 1500;
            setTimeout(() => {
                if (btn && oldHtml != null) btn.innerHTML = oldHtml;
            }, ms);
        }
    } catch (e) {
        if (btn && oldHtml != null) btn.innerHTML = oldHtml;
        throw e;
    }
}

/**
 * 声明式：按钮上设置 data-copy-target="#选择器"，指向含文本的 textarea 或元素。
 * 可选：data-copy-ok-html、data-copy-reset-ms
 */
function initializeGlobalCopyTextButtons() {
    document.querySelectorAll('[data-copy-target]').forEach((btn) => {
        if (btn.dataset.copyBound === '1') return;
        btn.dataset.copyBound = '1';
        btn.addEventListener('click', async () => {
            const sel = btn.getAttribute('data-copy-target');
            const el = sel ? document.querySelector(sel) : null;
            let t = '';
            if (el) {
                if ('value' in el) t = el.value;
                else t = el.textContent || '';
            }
            const resetMs = parseInt(btn.getAttribute('data-copy-reset-ms') || '1500', 10) || 1500;
            const okHtml = btn.getAttribute('data-copy-ok-html');
            try {
                await copyPlainTextToClipboardWithButton(t, {
                    button: btn,
                    okHtml: okHtml != null && okHtml !== '' ? okHtml : undefined,
                    resetMs,
                });
            } catch (err) {
                if (typeof showAlert === 'function') {
                    showAlert('复制失败，请手动选择文本复制。', 'danger');
                } else {
                    alert('复制失败，请手动选择文本复制。');
                }
            }
        });
    });
}

/** 全站统一：通过当前窗口导航触发 PDF 下载（与 location.href 等价，便于集中维护/扩展） */
function downloadPdfFromUrl(url) {
    if (!url) return;
    window.location.href = url;
}

// Animation functions
function initializeAnimations() {
    // Add fade-in animation to cards
    const cards = document.querySelectorAll('.card, .welcome-card');
    cards.forEach((card, index) => {
        setTimeout(() => {
            card.classList.add('fade-in');
        }, index * 100);
    });
}

// Form validation
function initializeFormValidation() {
    const forms = document.querySelectorAll('form');
    
    forms.forEach(form => {
        form.addEventListener('submit', function(event) {
            if (!form.checkValidity()) {
                event.preventDefault();
                event.stopPropagation();
            }
            form.classList.add('was-validated');
        });
    });
}

// Navigation enhancements
function initializeNavigation() {
    // Active navigation highlighting
    const currentPath = window.location.pathname;
    const navLinks = document.querySelectorAll('.navbar-nav .nav-link');
    
    navLinks.forEach(link => {
        if (link.getAttribute('href') === currentPath) {
            link.classList.add('active');
        }
    });
    // 窄屏菜单：由 Bootstrap data-bs-toggle（collapse / offcanvas）单独控制。
    // 切勿在此处再对 .navbar-collapse 做 classList.toggle('show')，否则会与 Bootstrap 双轨切换，
    // 导致「关闭后又自动打开」等问题。
}

// Bootstrap tooltips initialization
function initializeTooltips() {
    const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(function (tooltipTriggerEl) {
        return new bootstrap.Tooltip(tooltipTriggerEl);
    });
}

// Utility functions
function showAlert(message, type = 'info') {
    const alertContainer = document.getElementById('alert-container') || createAlertContainer();
    
    const alert = document.createElement('div');
    alert.className = `alert alert-${type} alert-dismissible fade show`;
    alert.innerHTML = `
        ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    `;
    
    alertContainer.appendChild(alert);
    
    // Auto-dismiss after 5 seconds
    setTimeout(() => {
        alert.remove();
    }, 5000);
}

function createAlertContainer() {
    const container = document.createElement('div');
    container.id = 'alert-container';
    container.className = 'fixed-top';
    container.style.zIndex = '9999';
    container.style.marginTop = '70px';
    container.style.padding = '0 15px';
    document.body.appendChild(container);
    return container;
}

// Loading spinner
function showLoading(element) {
    const originalContent = element.innerHTML;
    element.innerHTML = `
        <span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
        Loading...
    `;
    element.disabled = true;
    
    return originalContent;
}

function hideLoading(element, originalContent) {
    element.innerHTML = originalContent;
    element.disabled = false;
}

// API helper functions
async function apiCall(url, options = {}) {
    const defaultOptions = {
        headers: {
            'Content-Type': 'application/json',
        },
    };
    
    const finalOptions = { ...defaultOptions, ...options };
    
    try {
        const response = await fetch(url, finalOptions);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        return await response.json();
    } catch (error) {
        console.error('API call failed:', error);
        showAlert('An error occurred. Please try again.', 'danger');
        throw error;
    }
}

// Format date/time
function formatDateTime(dateString) {
    const date = new Date(dateString);
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

// Cookie 管理功能
class CookieManager {
    // 设置 Cookie
    static setCookie(name, value, days = 7) {
        const expires = new Date();
        expires.setTime(expires.getTime() + (days * 24 * 60 * 60 * 1000));
        document.cookie = `${name}=${encodeURIComponent(value)};expires=${expires.toUTCString()};path=/`;
    }

    // 获取 Cookie
    static getCookie(name) {
        const nameEQ = name + "=";
        const ca = document.cookie.split(';');
        for(let i = 0; i < ca.length; i++) {
            let c = ca[i];
            while (c.charAt(0) === ' ') c = c.substring(1, c.length);
            if (c.indexOf(nameEQ) === 0) return decodeURIComponent(c.substring(nameEQ.length, c.length));
        }
        return null;
    }

    // 删除 Cookie
    static deleteCookie(name) {
        document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 UTC;path=/;`;
    }

    // 检查 Cookie 是否存在
    static hasCookie(name) {
        return this.getCookie(name) !== null;
    }

    // 获取所有 Cookie
    static getAllCookies() {
        const cookies = {};
        document.cookie.split(';').forEach(cookie => {
            const [name, value] = cookie.trim().split('=');
            if (name && value) {
                cookies[name] = decodeURIComponent(value);
            }
        });
        return cookies;
    }

    // 清除所有 Cookie
    static clearAllCookies() {
        const cookies = this.getAllCookies();
        Object.keys(cookies).forEach(name => this.deleteCookie(name));
    }
}

// Cookie 同意管理
class CookieConsent {
    static CONSENT_KEY = 'cookie_consent';
    static PREFERENCES_KEY = 'cookie_preferences';

    // 检查是否已获得同意
    static hasConsent() {
        return CookieManager.hasCookie(this.CONSENT_KEY);
    }

    // 显示 Cookie 横幅
    static showBanner() {
        if (!this.hasConsent()) {
            const banner = document.getElementById('cookieBanner');
            if (banner) {
                banner.style.display = 'block';
            }
        }
    }

    // 隐藏 Cookie 横幅
    static hideBanner() {
        const banner = document.getElementById('cookieBanner');
        if (banner) {
            banner.style.display = 'none';
        }
    }

    // 接受所有 Cookie
    static acceptAll() {
        const preferences = {
            essential: true,
            preference: true,
            analytics: true,
            marketing: true
        };
        this.saveConsent(preferences);
        this.hideBanner();
        this.applyPreferences(preferences);
    }

    // 仅接受必需 Cookie
    static acceptEssential() {
        const preferences = {
            essential: true,
            preference: false,
            analytics: false,
            marketing: false
        };
        this.saveConsent(preferences);
        this.hideBanner();
        this.applyPreferences(preferences);
    }

    // 保存同意设置
    static saveConsent(preferences) {
        CookieManager.setCookie(this.CONSENT_KEY, 'true', 365);
        CookieManager.setCookie(this.PREFERENCES_KEY, JSON.stringify(preferences), 365);
    }

    // 获取保存的偏好设置
    static getPreferences() {
        const preferences = CookieManager.getCookie(this.PREFERENCES_KEY);
        return preferences ? JSON.parse(preferences) : null;
    }

    // 应用偏好设置
    static applyPreferences(preferences) {
        if (!preferences) return;

        // 根据偏好设置启用/禁用功能
        if (preferences.preference) {
            // 启用偏好设置相关功能
            console.log('偏好设置 Cookie 已启用');
        }

        if (preferences.analytics) {
            // 启用分析功能
            console.log('分析 Cookie 已启用');
            // 这里可以初始化 Google Analytics 等分析工具
        }

        if (preferences.marketing) {
            // 启用营销功能
            console.log('营销 Cookie 已启用');
        }
    }

    // 显示设置模态框
    static showSettings() {
        const modal = new bootstrap.Modal(document.getElementById('cookieSettingsModal'));
        
        // 加载当前设置
        const preferences = this.getPreferences();
        if (preferences) {
            document.getElementById('preferenceCookies').checked = preferences.preference;
            document.getElementById('analyticsCookies').checked = preferences.analytics;
            document.getElementById('marketingCookies').checked = preferences.marketing;
        }
        
        modal.show();
    }

    // 保存设置
    static saveSettings() {
        const preferences = {
            essential: true,
            preference: document.getElementById('preferenceCookies').checked,
            analytics: document.getElementById('analyticsCookies').checked,
            marketing: document.getElementById('marketingCookies').checked
        };
        
        this.saveConsent(preferences);
        this.hideBanner();
        this.applyPreferences(preferences);
        
        // 关闭模态框
        const modal = bootstrap.Modal.getInstance(document.getElementById('cookieSettingsModal'));
        if (modal) modal.hide();
    }
}

// 全局函数（供模板调用）
function acceptAllCookies() {
    CookieConsent.acceptAll();
}

function acceptEssentialCookies() {
    CookieConsent.acceptEssential();
}

function showCookieSettings() {
    CookieConsent.showSettings();
}

function saveCookieSettings() {
    CookieConsent.saveSettings();
}

// 用户偏好设置管理
class UserPreferences {
    static THEMES = {
        LIGHT: 'light',
        DARK: 'dark',
        AUTO: 'auto'
    };

    static LANGUAGES = {
        ZH_CN: 'zh-CN',
        ZH_TW: 'zh-TW',
        EN: 'en'
    };

    // 保存主题偏好
    static setTheme(theme) {
        CookieManager.setCookie('user_theme', theme, 30);
        this.applyTheme(theme);
    }

    // 获取主题偏好
    static getTheme() {
        return CookieManager.getCookie('user_theme') || this.THEMES.LIGHT;
    }

    // 应用主题
    static applyTheme(theme) {
        document.body.classList.remove('theme-light', 'theme-dark', 'theme-auto');
        document.body.classList.add(`theme-${theme}`);
        
        // 如果是自动主题，根据系统偏好设置
        if (theme === this.THEMES.AUTO) {
            if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
                document.body.classList.add('theme-dark');
            } else {
                document.body.classList.add('theme-light');
            }
        }
    }

    // 保存语言偏好
    static setLanguage(language) {
        CookieManager.setCookie('user_language', language, 30);
        // 这里可以添加语言切换逻辑
        console.log(`语言设置为: ${language}`);
    }

    // 获取语言偏好
    static getLanguage() {
        return CookieManager.getCookie('user_language') || this.LANGUAGES.ZH_CN;
    }

    // 记住用户名
    static rememberUsername(username) {
        if (username) {
            CookieManager.setCookie('remembered_username', username, 7);
        } else {
            CookieManager.deleteCookie('remembered_username');
        }
    }

    // 获取记住的用户名
    static getRememberedUsername() {
        return CookieManager.getCookie('remembered_username');
    }

    // 初始化所有偏好设置
    static init() {
        // 应用主题
        this.applyTheme(this.getTheme());
        
        // 应用语言设置
        const language = this.getLanguage();
        console.log(`当前语言: ${language}`);
        
        // 监听系统主题变化（自动模式）
        if (this.getTheme() === this.THEMES.AUTO) {
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
                this.applyTheme(this.THEMES.AUTO);
            });
        }
    }
}

// Export functions for global use
window.CurlingMasters = {
    showAlert,
    showLoading,
    hideLoading,
    apiCall,
    formatDateTime,
    acceptAllCookies,
    acceptEssentialCookies,
    showCookieSettings,
    saveCookieSettings,
    UserPreferences
};

// 表单验证类（支持Unicode和emoji）
class FormValidator {
    // 用户名验证（支持Unicode和emoji）
    static validateUsername(username) {
        if (!username || username.trim().length === 0) {
            return { valid: false, message: '用户名不能为空' };
        }

        const trimmed = username.trim();
        
        if (trimmed.length < 1) {
            return { valid: false, message: '用户名长度至少为1个字符' };
        }

        if (trimmed.length > 50) {
            return { valid: false, message: '用户名长度不能超过50个字符' };
        }

        // 支持Unicode字符和emoji的正则表达式
        // 允许：中文、日文、韩文、emoji、英文字母、数字、空格、下划线、连字符、点号
        const unicodePattern = /^[\p{L}\p{M}\p{N}\p{Zs}\p{So}\p{Sk}\p{Sm}_.-]{1,50}$/u;
        
        if (!unicodePattern.test(trimmed)) {
            return { valid: false, message: '用户名只能包含字母、数字、中文、emoji、空格以及 _ - .' };
        }

        // 检查SQL注入模式
        const sqlPatterns = [
            /union|select|insert|update|delete|drop|create|alter|exec|execute/i,
            /script|javascript|vbscript|onload|onerror/i,
            /[<>"'&;\/\*\-\-]/i,
            /or\s+\d+\s*=\s*\d+/i,
            /or\s+'[^']*'\s*=\s*'[^']*'/i
        ];

        for (const pattern of sqlPatterns) {
            if (pattern.test(trimmed)) {
                return { valid: false, message: '用户名包含不安全的内容' };
            }
        }

        // 检查控制字符
        if (/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/.test(trimmed)) {
            return { valid: false, message: '用户名包含非法字符' };
        }

        return { valid: true, message: '用户名格式正确' };
    }

    // 邮箱验证
    static validateEmail(email) {
        if (!email || email.trim().length === 0) {
            return { valid: false, message: '邮箱不能为空' };
        }

        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(email)) {
            return { valid: false, message: '请输入有效的邮箱地址' };
        }

        return { valid: true, message: '邮箱格式正确' };
    }

    // 密码验证
    static validatePassword(password) {
        if (!password || password.length === 0) {
            return { valid: false, message: '密码不能为空' };
        }

        if (password.length < 6) {
            return { valid: false, message: '密码长度至少为6位' };
        }

        // 检查密码强度（可选）
        const hasLetter = /[a-zA-Z]/.test(password);
        const hasNumber = /\d/.test(password);
        
        if (!hasLetter || !hasNumber) {
            return { valid: false, message: '密码必须包含字母和数字' };
        }

        return { valid: true, message: '密码格式正确' };
    }

    // 实时验证用户名
    static validateUsernameRealtime(username, callback) {
        // 前端验证
        const frontendResult = this.validateUsername(username);
        if (!frontendResult.valid) {
            callback(frontendResult);
            return;
        }

        // 后端验证（检查是否已存在）
        fetch(`/cookie/view`, {
            method: 'GET',
            headers: {
                'X-Requested-With': 'XMLHttpRequest'
            }
        })
        .then(response => response.json())
        .then(data => {
            // 这里可以添加后端验证逻辑
            callback(frontendResult);
        })
        .catch(error => {
            console.error('验证失败:', error);
            callback({ valid: false, message: '验证失败，请稍后重试' });
        });
    }

    // 获取用户名字符统计
    static getUsernameCharacterStats(username) {
        if (!username) return null;

        const stats = {
            letters: 0,
            digits: 0,
            whitespace: 0,
            emojis: 0,
            chinese: 0,
            others: 0,
            total: username.length
        };

        for (let i = 0; i < username.length; i++) {
            const char = username[i];
            const codePoint = username.codePointAt(i);

            if (/\p{L}/u.test(char)) {
                stats.letters++;
            } else if (/\p{N}/u.test(char)) {
                stats.digits++;
            } else if (/\s/.test(char)) {
                stats.whitespace++;
            } else if (this.isEmoji(codePoint)) {
                stats.emojis++;
            } else if (this.isChinese(codePoint)) {
                stats.chinese++;
            } else {
                stats.others++;
            }
        }

        return stats;
    }

    // 检查是否为emoji
    static isEmoji(codePoint) {
        return (codePoint >= 0x1F600 && codePoint <= 0x1F64F) || // 表情符号
               (codePoint >= 0x1F300 && codePoint <= 0x1F5FF) || // 杂项符号
               (codePoint >= 0x1F680 && codePoint <= 0x1F6FF) || // 交通和地图符号
               (codePoint >= 0x2600 && codePoint <= 0x26FF) ||   // 杂项符号
               (codePoint >= 0x2700 && codePoint <= 0x27BF);    // 装饰符号
    }

    // 检查是否为中文
    static isChinese(codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF) ||   // CJK统一汉字
               (codePoint >= 0x3400 && codePoint <= 0x4DBF);     // CJK扩展A
    }

    // 显示用户名统计信息
    static displayUsernameStats(username, containerId) {
        const stats = this.getUsernameCharacterStats(username);
        if (!stats) return;

        const container = document.getElementById(containerId);
        if (!container) return;

        let html = '<div class="username-stats small text-muted">';
        html += `字符统计: `;
        if (stats.letters > 0) html += `字母 ${stats.letters} `;
        if (stats.digits > 0) html += `数字 ${stats.digits} `;
        if (stats.chinese > 0) html += `中文 ${stats.chinese} `;
        if (stats.emojis > 0) html += `Emoji ${stats.emojis} `;
        if (stats.others > 0) html += `其他 ${stats.others} `;
        html += `</div>`;

        container.innerHTML = html;
    }
}

// 将FormValidator添加到全局对象
window.FormValidator = FormValidator;

// 左下角返回顶部按钮：短按回顶，长按拖动
function initializeBackToTopButton() {
    const btn = document.getElementById('backToTopBtn');
    if (!btn) return;

    const updateVisibility = () => {
        const show = window.scrollY > 10;
        btn.classList.toggle('show', show);
    };
    updateVisibility();
    window.addEventListener('scroll', updateVisibility, { passive: true });

    let longPressTimer = null;
    let isDown = false;
    let longPressTriggered = false;
    let startY = 0;
    let startScrollY = 0;
    let moved = false;
    let lastY = 0;

    const cancelTimer = () => {
        if (longPressTimer) {
            clearTimeout(longPressTimer);
            longPressTimer = null;
        }
    };

    const onPointerDown = (e) => {
        if (e.pointerType === 'mouse' && e.button !== 0) return;
        e.preventDefault();
        isDown = true;
        moved = false;
        longPressTriggered = false;

        startY = e.clientY;
        lastY = e.clientY;
        startScrollY = window.scrollY;

        cancelTimer();
        longPressTimer = setTimeout(() => {
            longPressTriggered = true;
            btn.classList.add('dragging');
        }, 420);

        try { btn.setPointerCapture(e.pointerId); } catch (err) { /* ignore */ }
    };

    const onPointerMove = (e) => {
        if (!isDown) return;
        const y = e.clientY;
        if (Math.abs(y - lastY) > 2) moved = true;
        lastY = y;

        if (!longPressTriggered) return;
        // 长按后拖动：跟随手指滚动回顶
        const delta = y - startY;
        window.scrollTo({ top: Math.max(0, startScrollY - delta), behavior: 'auto' });
    };

    const onPointerEnd = (e) => {
        isDown = false;
        cancelTimer();
        btn.classList.remove('dragging');
        try { btn.releasePointerCapture(e.pointerId); } catch (err) { /* ignore */ }

        if (!longPressTriggered && !moved) {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }
        longPressTriggered = false;
    };

    btn.addEventListener('pointerdown', onPointerDown);
    btn.addEventListener('pointermove', onPointerMove, { passive: true });
    btn.addEventListener('pointerup', onPointerEnd);
    btn.addEventListener('pointercancel', onPointerEnd);
    btn.addEventListener('contextmenu', (e) => e.preventDefault());
}

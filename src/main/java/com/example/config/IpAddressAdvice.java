package com.example.config;

import com.example.util.IpAddressUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 全局Controller增强：根据客户端IP类型注入背景CSS类（{@code ipBgClass}）。
 * 模板中 favicon 切换（fragments.html）与此字段保持一致：{@code bg-internal} 为 emoji，
 * {@code bg-external} 为 {@code /favicon.ico}。
 * 无需用户登录，所有请求自动生效。
 */
@ControllerAdvice
public class IpAddressAdvice {

    @ModelAttribute("ipBgClass")
    public String ipBgClass(HttpServletRequest request) {
        String ip = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(ip);
        switch (ipType) {
            case "local":
            case "private":
                return "bg-internal";
            default:
                return "bg-external";
        }
    }
}

package com.example.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IP地址工具类
 * 提供IP地址获取、分类和验证功能
 * 
 * @author Curling Masters
 * @since 2026-03-16
 */
public class IpAddressUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(IpAddressUtil.class);
    
    /**
     * 获取客户端真实IP地址
     * 支持代理环境下的IP获取，处理各种HTTP头信息
     * 
     * @param request HTTP请求对象
     * @return 客户端真实IP地址
     */
    public static String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            logger.warn("HttpServletRequest is null, returning unknown IP");
            return "unknown";
        }
        
        String ip = request.getHeader("X-Forwarded-For");
        if (isInvalidIp(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (isInvalidIp(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (isInvalidIp(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (isInvalidIp(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (isInvalidIp(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 对于通过多个代理的情况，第一个IP为客户端真实IP，多个IP按照','分割
        if (ip != null && ip.contains(",")) {
            ip = ip.substring(0, ip.indexOf(",")).trim();
        }
        
        logger.debug("Client IP address resolved to: {}", ip);
        return ip != null ? ip : "unknown";
    }
    
    /**
     * 判断IP地址类型：local, private, public
     * 
     * @param ip IP地址字符串
     * @return IP类型（local/private/public/unknown）
     */
    public static String classifyIpAddress(String ip) {
        if (ip == null || ip.isEmpty() || "unknown".equals(ip)) {
            return "unknown";
        }
        
        // 处理IPv6本地地址
        if (ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) {
            return "local";
        }
        
        // 处理IPv4本地地址
        if (ip.equals("127.0.0.1")) {
            return "local";
        }
        
        try {
            // 解析IP地址
            String[] parts = ip.split("\\.");
            if (parts.length != 4) {
                logger.debug("Invalid IPv4 format: {}", ip);
                return "unknown";
            }
            
            int firstOctet = Integer.parseInt(parts[0]);
            int secondOctet = Integer.parseInt(parts[1]);
            
            // 判断内网地址
            // 10.0.0.0 - 10.255.255.255
            if (firstOctet == 10) {
                return "private";
            }
            
            // 172.16.0.0 - 172.31.255.255
            if (firstOctet == 172 && secondOctet >= 16 && secondOctet <= 31) {
                return "private";
            }
            
            // 192.168.0.0 - 192.168.255.255
            if (firstOctet == 192 && secondOctet == 168) {
                return "private";
            }
            
            // 169.254.0.0 - 169.254.255.255 (链路本地地址)
            if (firstOctet == 169 && secondOctet == 254) {
                return "private";
            }
            
            // 其他情况为公网地址
            return "public";
            
        } catch (NumberFormatException e) {
            logger.warn("Error parsing IP address: {}", ip, e);
            return "unknown";
        }
    }
    
    /**
     * 验证IP地址是否为内网地址
     * 
     * @param ip IP地址字符串
     * @return true如果是内网地址，否则false
     */
    public static boolean isPrivateIpAddress(String ip) {
        return "private".equals(classifyIpAddress(ip));
    }
    
    /**
     * 验证IP地址是否为本地地址
     * 
     * @param ip IP地址字符串
     * @return true如果是本地地址，否则false
     */
    public static boolean isLocalIpAddress(String ip) {
        return "local".equals(classifyIpAddress(ip));
    }
    
    /**
     * 验证IP地址是否为公网地址
     * 
     * @param ip IP地址字符串
     * @return true如果是公网地址，否则false
     */
    public static boolean isPublicIpAddress(String ip) {
        return "public".equals(classifyIpAddress(ip));
    }
    
    /**
     * 检查IP地址是否有效（非空且不是unknown）
     * 
     * @param ip IP地址字符串
     * @return true如果IP地址无效，否则false
     */
    private static boolean isInvalidIp(String ip) {
        return ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip);
    }
    
    /**
     * 获取IP地址的友好描述
     * 
     * @param ipType IP类型（local/private/public/unknown）
     * @return 友好的描述文本
     */
    public static String getIpTypeDescription(String ipType) {
        switch (ipType) {
            case "local":
                return "本地访问";
            case "private":
                return "内网访问";
            case "public":
                return "外网访问";
            default:
                return "未知访问";
        }
    }
    
    /**
     * 获取IP类型对应的CSS类名
     * 
     * @param ipType IP类型（local/private/public/unknown）
     * @return Bootstrap徽章CSS类名
     */
    public static String getIpTypeBadgeClass(String ipType) {
        switch (ipType) {
            case "local":
                return "bg-success";
            case "private":
                return "bg-warning text-dark";
            case "public":
                return "bg-info";
            default:
                return "bg-secondary";
        }
    }
}
package com.example.service;

import com.example.entity.SystemConfig;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 系统配置服务类
 * </p>
 */
public interface SystemConfigService extends IService<SystemConfig> {
    
    /**
     * 获取配置值
     */
    String getConfigValue(String configKey);
    
    /**
     * 获取配置值，如果不存在返回默认值
     */
    String getConfigValue(String configKey, String defaultValue);
    
    /**
     * 设置配置值
     */
    void setConfigValue(String configKey, String configValue, String description);
    
    /**
     * 获取赛制规则默认值
     */
    int getDefaultCenterLineLimit();
    
    /**
     * 获取赛制规则默认值
     */
    int getDefaultDeadStoneLimit();

    /**
     * 总控台默认：上一阶段淘汰赛全部结束后才可进入下一阶段（默认 false / OFF）
     */
    boolean getDefaultKnockoutRequirePrevStageDone();
}
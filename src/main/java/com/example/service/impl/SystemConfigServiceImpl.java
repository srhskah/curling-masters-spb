package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.entity.SystemConfig;
import com.example.mapper.SystemConfigMapper;
import com.example.service.SystemConfigService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 系统配置服务实现类
 * </p>
 */
@Service
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig> implements SystemConfigService {
    
    @Override
    public String getConfigValue(String configKey) {
        return getConfigValue(configKey, null);
    }
    
    @Override
    public String getConfigValue(String configKey, String defaultValue) {
        SystemConfig config = getOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, configKey));
        return config != null ? config.getConfigValue() : defaultValue;
    }
    
    @Override
    public void setConfigValue(String configKey, String configValue, String description) {
        SystemConfig config = getOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, configKey));
        
        if (config != null) {
            config.setConfigValue(configValue);
            if (description != null) {
                config.setDescription(description);
            }
            updateById(config);
        } else {
            config = new SystemConfig();
            config.setConfigKey(configKey);
            config.setConfigValue(configValue);
            config.setDescription(description);
            save(config);
        }
    }
    
    @Override
    public int getDefaultCenterLineLimit() {
        String value = getConfigValue("competition_rule_default_center_line_limit", "5");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 5;
        }
    }
    
    @Override
    public int getDefaultDeadStoneLimit() {
        String value = getConfigValue("competition_rule_default_dead_stone_limit", "5");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 5;
        }
    }
}
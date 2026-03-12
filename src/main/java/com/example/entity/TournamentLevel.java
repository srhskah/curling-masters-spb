package com.example.entity;

import java.math.BigDecimal;

public class TournamentLevel {
    private Integer id;
    private String code; // 等级代码，如年终总决赛、2000赛等
    private String name;
    private BigDecimal defaultChampionRatio; // 默认冠军积分比率(%)
    private Integer defaultBottomPoints; // 垫底积分
    private String description;

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getDefaultChampionRatio() {
        return defaultChampionRatio;
    }

    public void setDefaultChampionRatio(BigDecimal defaultChampionRatio) {
        this.defaultChampionRatio = defaultChampionRatio;
    }

    public Integer getDefaultBottomPoints() {
        return defaultBottomPoints;
    }

    public void setDefaultBottomPoints(Integer defaultBottomPoints) {
        this.defaultBottomPoints = defaultBottomPoints;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

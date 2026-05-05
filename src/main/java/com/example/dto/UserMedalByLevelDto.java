package com.example.dto;

public record UserMedalByLevelDto(String levelCode, String levelLabel, int gold, int silver, int bronze) {
    public int total() {
        return gold + silver + bronze;
    }
}

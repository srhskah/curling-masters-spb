package com.example.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前用户在某一赛事中的参赛标记、所在阶段和该阶段待完成场次。
 */
public class ViewerTournamentProgress {
    private boolean participating;
    private String stageLabel = "";
    private String matchesUrl = "";
    private final List<String> pendingOpponents = new ArrayList<>();

    public boolean isParticipating() {
        return participating;
    }

    public void setParticipating(boolean participating) {
        this.participating = participating;
    }

    public String getStageLabel() {
        return stageLabel;
    }

    public void setStageLabel(String stageLabel) {
        this.stageLabel = stageLabel == null ? "" : stageLabel;
    }

    public String getMatchesUrl() {
        return matchesUrl;
    }

    public void setMatchesUrl(String matchesUrl) {
        this.matchesUrl = matchesUrl == null ? "" : matchesUrl;
    }

    public List<String> getPendingOpponents() {
        return pendingOpponents;
    }

    public boolean isHasPending() {
        return !pendingOpponents.isEmpty();
    }
}

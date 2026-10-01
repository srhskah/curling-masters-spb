package com.example.dto;

import java.util.List;

/**
 * 淘汰赛对阵图数据传输对象
 */
public class KnockoutBracketData {
    private List<MatchSlot> qualifierMatches;
    private List<MatchSlot> qualifierMatchesUpper;
    private List<MatchSlot> qualifierMatchesLower;
    private List<MatchSlot> round16Matches;
    private List<MatchSlot> round16MatchesUpper;
    private List<MatchSlot> round16MatchesLower;
    private List<MatchSlot> round8Matches;
    private List<MatchSlot> round8MatchesUpper;
    private List<MatchSlot> round8MatchesLower;
    private List<MatchSlot> round4Matches;
    private List<MatchSlot> round4MatchesUpper;
    private List<MatchSlot> round4MatchesLower;
    private MatchSlot goldMatch;
    private MatchSlot bronzeMatch;
    private Integer currentRound;
    private String currentRoundLabel;
    private boolean hasQualifiers;
    private boolean hasRound16;
    private boolean hasRound8;
    private boolean hasRound4;
    private boolean hasFinal;

    public List<MatchSlot> getQualifierMatches() {
        return qualifierMatches;
    }

    public void setQualifierMatches(List<MatchSlot> qualifierMatches) {
        this.qualifierMatches = qualifierMatches;
    }

    public List<MatchSlot> getQualifierMatchesUpper() {
        return qualifierMatchesUpper;
    }

    public void setQualifierMatchesUpper(List<MatchSlot> qualifierMatchesUpper) {
        this.qualifierMatchesUpper = qualifierMatchesUpper;
    }

    public List<MatchSlot> getQualifierMatchesLower() {
        return qualifierMatchesLower;
    }

    public void setQualifierMatchesLower(List<MatchSlot> qualifierMatchesLower) {
        this.qualifierMatchesLower = qualifierMatchesLower;
    }

    public List<MatchSlot> getRound16Matches() {
        return round16Matches;
    }

    public void setRound16Matches(List<MatchSlot> round16Matches) {
        this.round16Matches = round16Matches;
    }

    public List<MatchSlot> getRound16MatchesUpper() {
        return round16MatchesUpper;
    }

    public void setRound16MatchesUpper(List<MatchSlot> round16MatchesUpper) {
        this.round16MatchesUpper = round16MatchesUpper;
    }

    public List<MatchSlot> getRound16MatchesLower() {
        return round16MatchesLower;
    }

    public void setRound16MatchesLower(List<MatchSlot> round16MatchesLower) {
        this.round16MatchesLower = round16MatchesLower;
    }

    public List<MatchSlot> getRound8Matches() {
        return round8Matches;
    }

    public void setRound8Matches(List<MatchSlot> round8Matches) {
        this.round8Matches = round8Matches;
    }

    public List<MatchSlot> getRound8MatchesUpper() {
        return round8MatchesUpper;
    }

    public void setRound8MatchesUpper(List<MatchSlot> round8MatchesUpper) {
        this.round8MatchesUpper = round8MatchesUpper;
    }

    public List<MatchSlot> getRound8MatchesLower() {
        return round8MatchesLower;
    }

    public void setRound8MatchesLower(List<MatchSlot> round8MatchesLower) {
        this.round8MatchesLower = round8MatchesLower;
    }

    public List<MatchSlot> getRound4Matches() {
        return round4Matches;
    }

    public void setRound4Matches(List<MatchSlot> round4Matches) {
        this.round4Matches = round4Matches;
    }

    public List<MatchSlot> getRound4MatchesUpper() {
        return round4MatchesUpper;
    }

    public void setRound4MatchesUpper(List<MatchSlot> round4MatchesUpper) {
        this.round4MatchesUpper = round4MatchesUpper;
    }

    public List<MatchSlot> getRound4MatchesLower() {
        return round4MatchesLower;
    }

    public void setRound4MatchesLower(List<MatchSlot> round4MatchesLower) {
        this.round4MatchesLower = round4MatchesLower;
    }

    public MatchSlot getGoldMatch() {
        return goldMatch;
    }

    public void setGoldMatch(MatchSlot goldMatch) {
        this.goldMatch = goldMatch;
    }

    public MatchSlot getBronzeMatch() {
        return bronzeMatch;
    }

    public void setBronzeMatch(MatchSlot bronzeMatch) {
        this.bronzeMatch = bronzeMatch;
    }

    public Integer getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(Integer currentRound) {
        this.currentRound = currentRound;
    }

    public String getCurrentRoundLabel() {
        return currentRoundLabel;
    }

    public void setCurrentRoundLabel(String currentRoundLabel) {
        this.currentRoundLabel = currentRoundLabel;
    }

    public boolean isHasQualifiers() {
        return hasQualifiers;
    }

    public void setHasQualifiers(boolean hasQualifiers) {
        this.hasQualifiers = hasQualifiers;
    }

    public boolean isHasRound16() {
        return hasRound16;
    }

    public void setHasRound16(boolean hasRound16) {
        this.hasRound16 = hasRound16;
    }

    public boolean isHasRound8() {
        return hasRound8;
    }

    public void setHasRound8(boolean hasRound8) {
        this.hasRound8 = hasRound8;
    }

    public boolean isHasRound4() {
        return hasRound4;
    }

    public void setHasRound4(boolean hasRound4) {
        this.hasRound4 = hasRound4;
    }

    public boolean isHasFinal() {
        return hasFinal;
    }

    public void setHasFinal(boolean hasFinal) {
        this.hasFinal = hasFinal;
    }

    /**
     * 单个对阵槽位数据
     */
    public static class MatchSlot {
        private Long id;
        private Long player1Id;
        private Long player2Id;
        private String player1Name;
        private String player2Name;
        private Long winnerId;
        private Integer round;
        private Integer knockoutBracketSlot;
        private Integer knockoutHalf;
        private String category;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getPlayer1Id() {
            return player1Id;
        }

        public void setPlayer1Id(Long player1Id) {
            this.player1Id = player1Id;
        }

        public Long getPlayer2Id() {
            return player2Id;
        }

        public void setPlayer2Id(Long player2Id) {
            this.player2Id = player2Id;
        }

        public String getPlayer1Name() {
            return player1Name;
        }

        public void setPlayer1Name(String player1Name) {
            this.player1Name = player1Name;
        }

        public String getPlayer2Name() {
            return player2Name;
        }

        public void setPlayer2Name(String player2Name) {
            this.player2Name = player2Name;
        }

        public Long getWinnerId() {
            return winnerId;
        }

        public void setWinnerId(Long winnerId) {
            this.winnerId = winnerId;
        }

        public Integer getRound() {
            return round;
        }

        public void setRound(Integer round) {
            this.round = round;
        }

        public Integer getKnockoutBracketSlot() {
            return knockoutBracketSlot;
        }

        public void setKnockoutBracketSlot(Integer knockoutBracketSlot) {
            this.knockoutBracketSlot = knockoutBracketSlot;
        }

        public Integer getKnockoutHalf() {
            return knockoutHalf;
        }

        public void setKnockoutHalf(Integer knockoutHalf) {
            this.knockoutHalf = knockoutHalf;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }
    }
}
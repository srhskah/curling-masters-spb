package com.example.util;

import com.example.entity.Match;

import static com.example.service.impl.KnockoutBracketService.SOURCE_AUTO_FROM_GROUP_KO_QUALIFIER;

public final class MatchPhaseClassifier {

    public static final String KIND_GROUP = "GROUP";
    public static final String KIND_ENTRY_QUALIFIER = "ENTRY_QUALIFIER";
    public static final String KIND_KO_QUALIFIER = "KO_QUALIFIER";
    public static final String KIND_MAIN = "MAIN";
    public static final String KIND_FINAL = "FINAL";
    public static final String KIND_OTHER = "OTHER";

    private MatchPhaseClassifier() {
    }

    public static String classify(Match match) {
        if (match == null || match.getPhaseCode() == null) {
            return KIND_OTHER;
        }
        String phase = match.getPhaseCode().trim().toUpperCase();
        return switch (phase) {
            case "GROUP" -> KIND_GROUP;
            case "MAIN" -> KIND_MAIN;
            case "FINAL" -> KIND_FINAL;
            case "QUALIFIER" -> isKnockoutQualifier(match) ? KIND_KO_QUALIFIER : KIND_ENTRY_QUALIFIER;
            default -> KIND_OTHER;
        };
    }

    public static boolean isQualifier(Match match) {
        String kind = classify(match);
        return KIND_ENTRY_QUALIFIER.equals(kind) || KIND_KO_QUALIFIER.equals(kind);
    }

    public static boolean isEntryQualifier(Match match) {
        return KIND_ENTRY_QUALIFIER.equals(classify(match));
    }

    public static boolean isKnockoutQualifier(Match match) {
        return match != null
                && match.getPhaseCode() != null
                && "QUALIFIER".equalsIgnoreCase(match.getPhaseCode())
                && SOURCE_AUTO_FROM_GROUP_KO_QUALIFIER.equalsIgnoreCase(match.getCreateSource());
    }

    public static boolean requiresSignature(Match match) {
        String kind = classify(match);
        return KIND_ENTRY_QUALIFIER.equals(kind)
                || KIND_KO_QUALIFIER.equals(kind)
                || KIND_MAIN.equals(kind)
                || KIND_FINAL.equals(kind);
    }
}

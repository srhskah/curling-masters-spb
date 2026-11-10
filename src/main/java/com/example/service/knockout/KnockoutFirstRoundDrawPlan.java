package com.example.service.knockout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 淘汰赛首轮抽签分档。名次都是小组赛总排名。
 * 相邻两名抽对方两名；资格赛名次写成「该名次参加的资格赛胜者」。
 */
public final class KnockoutFirstRoundDrawPlan {

    private KnockoutFirstRoundDrawPlan() {}

    public record Pot(int index, int[] drawers, int[] prizes, boolean qualifier) {}

    public static List<Pot> mainPots(int bracketSize) {
        requireBracket(bracketSize);
        List<Pot> pots = new ArrayList<>();
        int index = 0;
        for (int drawer = 1; drawer <= bracketSize / 2; drawer += 2) {
            int mate = drawer + 1;
            pots.add(new Pot(index++, new int[]{drawer, mate},
                    new int[]{bracketSize + 1 - drawer, bracketSize + 1 - mate}, false));
        }
        return pots;
    }

    /** 挂载资格赛且选择「资格赛参与抽签」时，这些名次互抽挑战者名次。 */
    public static Pot qualifierPot(int bracketSize, int qualifyCount) {
        if (qualifyCount <= 0) {
            return null;
        }
        requireBracket(bracketSize);
        if (qualifyCount > bracketSize / 2) {
            throw new IllegalArgumentException("资格赛场次不能多于首轮的一半");
        }
        int[] drawers = new int[qualifyCount];
        int[] prizes = new int[qualifyCount];
        int first = bracketSize - qualifyCount + 1;
        for (int i = 0; i < qualifyCount; i++) {
            drawers[i] = first + i;
            prizes[i] = bracketSize + 1 + i;
        }
        return new Pot(1000, drawers, prizes, true);
    }

    public static boolean isQualifierPrize(int rank, int bracketSize, int qualifyCount) {
        return qualifyCount > 0 && rank > bracketSize - qualifyCount && rank <= bracketSize;
    }

    /** 经典场次顺序：前者固定，后者换成该名抽到的数字。前一半为上半区。 */
    public static List<int[]> mainMatches(int bracketSize, Map<Integer, Integer> prizeByDrawer) {
        requireBracket(bracketSize);
        List<int[]> classic = KnockoutPairingUtil.classicOverallRankPairs(bracketSize);
        List<int[]> out = new ArrayList<>();
        for (int[] pair : classic) {
            if (pair[0] > bracketSize / 2) {
                throw new IllegalStateException("经典对阵前手不是抽签名次");
            }
            Integer prize = prizeByDrawer.get(pair[0]);
            if (prize == null) {
                throw new IllegalStateException("第 " + pair[0] + " 名尚未抽签");
            }
            out.add(new int[]{pair[0], prize});
        }
        return out;
    }

    /**
     * 资格赛不抽签时的原对阵：按经典场次遇到挂载名次的顺序，依次配对下一名挑战者。
     */
    public static List<int[]> originalQualifierPairs(int bracketSize, int qualifyCount) {
        if (qualifyCount <= 0) {
            return List.of();
        }
        requireBracket(bracketSize);
        int directUntil = bracketSize - qualifyCount;
        List<int[]> out = new ArrayList<>();
        int challenger = bracketSize + 1;
        for (int[] pair : KnockoutPairingUtil.classicOverallRankPairs(bracketSize)) {
            if (pair[1] > directUntil) {
                out.add(new int[]{pair[1], challenger});
                challenger++;
            }
        }
        if (out.size() != qualifyCount) {
            throw new IllegalStateException("资格赛原对阵数量与名额不一致");
        }
        return out;
    }

    public static Map<Integer, Integer> prizeByDrawer(List<Pot> pots, Map<Integer, Integer> drawnPrizeByRank) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        if (pots == null || drawnPrizeByRank == null) {
            return out;
        }
        for (Pot pot : pots) {
            if (pot == null || pot.qualifier) {
                continue;
            }
            for (int drawer : pot.drawers) {
                Integer prize = drawnPrizeByRank.get(drawer);
                if (prize != null) {
                    out.put(drawer, prize);
                }
            }
        }
        return out;
    }

    public static String prizeLabel(int rank, int bracketSize, int qualifyCount) {
        if (isQualifierPrize(rank, bracketSize, qualifyCount)) {
            return "第" + rank + "名参加的资格赛胜者";
        }
        return "第" + rank + "名";
    }

    private static void requireBracket(int bracketSize) {
        if (bracketSize != 4 && bracketSize != 8 && bracketSize != 16 && bracketSize != 32) {
            throw new IllegalArgumentException("淘汰赛首轮抽签仅支持 4/8/16/32 人");
        }
    }
}

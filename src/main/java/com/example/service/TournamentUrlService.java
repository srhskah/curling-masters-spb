package com.example.service;

import com.example.entity.Season;
import com.example.entity.Series;
import com.example.entity.Tournament;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/**
 * 赛事 / 赛季友好 URL。
 * <p>
 * 赛事详情短路径：{@code /tnd/{YY}{half两位}{系列sequence两位}/{levelCode}}，
 * 例如 {@code /tournament/detail/2026/2/5/CM250} → {@code /tnd/260205/CM250}。
 * 赛季详情：{@code /season/detail/{year}/{half}}（half：1=上半年，2=下半年）。
 */
@Component("tournamentUrls")
public class TournamentUrlService {

    @Autowired
    private TournamentService tournamentService;
    @Autowired
    private SeriesService seriesService;
    @Autowired
    private SeasonService seasonService;

    public record PathKey(int year, int half, int seriesSequence, String levelCode) {
        public String toPath() {
            return year + "/" + half + "/" + seriesSequence + "/" + encodeLevel(levelCode);
        }

        /** 可编码为 6 位短码时返回 {@code /tnd/YYHHSS/level}，否则 null。 */
        public String toCompactPath() {
            String code = compactCode(year, half, seriesSequence);
            if (code == null || levelCode == null || levelCode.isBlank()) {
                return null;
            }
            return "/tnd/" + code + "/" + encodeLevel(levelCode);
        }
    }

    /** 短码解析结果：完整四位年份 + 半年 + 系列序号。 */
    public record CompactParts(int year, int half, int seriesSequence) {
    }

    /**
     * 6 位短码：年份后两位 + 半年两位（01/02）+ 系列序号两位。
     * 仅支持 2000–2099 年、半年 1–2、序号 0–99。
     */
    public static String compactCode(int year, int half, int seriesSequence) {
        if (year < 2000 || year > 2099 || half < 1 || half > 2
                || seriesSequence < 0 || seriesSequence > 99) {
            return null;
        }
        return String.format("%02d%02d%02d", year % 100, half, seriesSequence);
    }

    public static CompactParts parseCompact(String code) {
        if (code == null || code.length() != 6) {
            return null;
        }
        for (int i = 0; i < 6; i++) {
            char c = code.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        int yy = Integer.parseInt(code.substring(0, 2));
        int half = Integer.parseInt(code.substring(2, 4));
        int seq = Integer.parseInt(code.substring(4, 6));
        if (half < 1 || half > 2) {
            return null;
        }
        return new CompactParts(2000 + yy, half, seq);
    }

    public static String encodeLevel(String levelCode) {
        if (levelCode == null || levelCode.isBlank()) {
            return "";
        }
        return URLEncoder.encode(levelCode.trim(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    public PathKey pathKey(Tournament tournament) {
        if (tournament == null || tournament.getSeriesId() == null || tournament.getLevelCode() == null
                || tournament.getLevelCode().isBlank()) {
            return null;
        }
        Series series = seriesService.getById(tournament.getSeriesId());
        if (series == null || series.getSeasonId() == null || series.getSequence() == null) {
            return null;
        }
        Season season = seasonService.getById(series.getSeasonId());
        if (season == null || season.getYear() == null || season.getHalf() == null) {
            return null;
        }
        return new PathKey(season.getYear(), season.getHalf(), series.getSequence(), tournament.getLevelCode().trim());
    }

    public PathKey pathKey(Long tournamentId) {
        if (tournamentId == null) {
            return null;
        }
        return pathKey(tournamentService.getById(tournamentId));
    }

    public Tournament resolve(Integer year, Integer half, Integer seriesSequence, String levelCode) {
        if (year == null || half == null || seriesSequence == null
                || levelCode == null || levelCode.isBlank()) {
            return null;
        }
        Season season = seasonService.lambdaQuery()
                .eq(Season::getYear, year)
                .eq(Season::getHalf, half)
                .one();
        if (season == null) {
            return null;
        }
        Series series = seriesService.lambdaQuery()
                .eq(Series::getSeasonId, season.getId())
                .eq(Series::getSequence, seriesSequence)
                .one();
        if (series == null) {
            return null;
        }
        List<Tournament> list = tournamentService.lambdaQuery()
                .eq(Tournament::getSeriesId, series.getId())
                .eq(Tournament::getLevelCode, levelCode.trim())
                .list();
        if (list == null || list.isEmpty()) {
            return null;
        }
        list.sort(Comparator.comparing(Tournament::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Tournament::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return list.get(0);
    }

    public Tournament resolveCompact(String compact, String levelCode) {
        CompactParts parts = parseCompact(compact);
        if (parts == null) {
            return null;
        }
        return resolve(parts.year(), parts.half(), parts.seriesSequence(), levelCode);
    }

    public String prettyDetailUrl(Tournament tournament) {
        PathKey key = pathKey(tournament);
        if (key == null && tournament != null && tournament.getId() != null) {
            return "/tournament/detail/" + tournament.getId();
        }
        return key == null ? "#" : "/tournament/detail/" + key.toPath();
    }

    /** 优先 6 位短路径；无法压缩时回退到长友好路径。 */
    public String detailUrl(Tournament tournament) {
        PathKey key = pathKey(tournament);
        if (key != null) {
            String compact = key.toCompactPath();
            if (compact != null) {
                return compact;
            }
            return "/tournament/detail/" + key.toPath();
        }
        if (tournament != null && tournament.getId() != null) {
            return "/tournament/detail/" + tournament.getId();
        }
        return "#";
    }

    /** 与 {@link #detailUrl(Tournament)} 相同：供页面「生成短路径」复制。 */
    public String shortDetailUrl(Tournament tournament) {
        return detailUrl(tournament);
    }

    public String detailUrl(Long tournamentId) {
        if (tournamentId == null) {
            return "#";
        }
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) {
            return "/tournament/detail/" + tournamentId;
        }
        return detailUrl(t);
    }

    public String registrationUrl(Tournament tournament) {
        PathKey key = pathKey(tournament);
        if (key == null && tournament != null && tournament.getId() != null) {
            return "/tournament/registration/" + tournament.getId();
        }
        return key == null ? "#" : "/tournament/registration/" + key.toPath();
    }

    public String registrationUrl(Long tournamentId) {
        if (tournamentId == null) {
            return "#";
        }
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) {
            return "/tournament/registration/" + tournamentId;
        }
        return registrationUrl(t);
    }

    public String drawUrl(Tournament tournament) {
        PathKey key = pathKey(tournament);
        if (key == null && tournament != null && tournament.getId() != null) {
            return "/tournament/" + tournament.getId() + "/draw";
        }
        return key == null ? "#" : "/tournament/" + key.toPath() + "/draw";
    }

    public String drawUrl(Long tournamentId) {
        if (tournamentId == null) {
            return "#";
        }
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) {
            return "/tournament/" + tournamentId + "/draw";
        }
        return drawUrl(t);
    }

    public String knockoutBracketUrl(Tournament tournament) {
        PathKey key = pathKey(tournament);
        if (key == null && tournament != null && tournament.getId() != null) {
            return "/tournament/" + tournament.getId() + "/knockout-bracket";
        }
        return key == null ? "#" : "/tournament/" + key.toPath() + "/knockout-bracket";
    }

    public String knockoutBracketUrl(Long tournamentId) {
        if (tournamentId == null) {
            return "#";
        }
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) {
            return "/tournament/" + tournamentId + "/knockout-bracket";
        }
        return knockoutBracketUrl(t);
    }

    /** Spring MVC redirect 目标（含可选 query）。 */
    public String redirectDetail(Long tournamentId) {
        return "redirect:" + detailUrl(tournamentId);
    }

    public String redirectDetail(Long tournamentId, String queryWithoutQuestion) {
        String base = detailUrl(tournamentId);
        if (queryWithoutQuestion == null || queryWithoutQuestion.isBlank()) {
            return "redirect:" + base;
        }
        return "redirect:" + base + "?" + queryWithoutQuestion;
    }

    public String redirectRegistration(Long tournamentId) {
        return "redirect:" + registrationUrl(tournamentId);
    }

    public String redirectDraw(Long tournamentId) {
        return "redirect:" + drawUrl(tournamentId);
    }

    public String redirectDraw(Long tournamentId, String queryWithoutQuestion) {
        String base = drawUrl(tournamentId);
        if (queryWithoutQuestion == null || queryWithoutQuestion.isBlank()) {
            return "redirect:" + base;
        }
        return "redirect:" + base + "?" + queryWithoutQuestion;
    }

    public String redirectKnockoutBracket(Long tournamentId) {
        return "redirect:" + knockoutBracketUrl(tournamentId);
    }

    /**
     * 旧 ID 入口：若能生成友好 URL 则 302 跳转，否则返回 null（调用方继续用 ID 渲染）。
     */
    public String redirectToPrettyOrNull(Long tournamentId, String prettyUrl,
                                         jakarta.servlet.http.HttpServletRequest request) {
        PathKey key = pathKey(tournamentId);
        if (key == null || prettyUrl == null || prettyUrl.isBlank() || "#".equals(prettyUrl)) {
            return null;
        }
        // 无法生成友好路径时 detailUrl 会回退成 /.../{id}
        if (prettyUrl.equals("/tournament/detail/" + tournamentId)
                || prettyUrl.equals("/tournament/registration/" + tournamentId)
                || prettyUrl.equals("/tournament/" + tournamentId + "/draw")
                || prettyUrl.equals("/tournament/" + tournamentId + "/knockout-bracket")) {
            return null;
        }
        String q = request != null ? request.getQueryString() : null;
        return "redirect:" + prettyUrl + (q != null && !q.isBlank() ? "?" + q : "");
    }

    public String seasonDetailUrl(Season season) {
        if (season == null) {
            return "#";
        }
        if (season.getYear() != null && season.getHalf() != null
                && season.getHalf() >= 1 && season.getHalf() <= 2) {
            return "/season/detail/" + season.getYear() + "/" + season.getHalf();
        }
        return season.getId() != null ? "/season/detail/" + season.getId() : "#";
    }

    public String seasonDetailUrl(Long seasonId) {
        if (seasonId == null) {
            return "#";
        }
        Season season = seasonService.getById(seasonId);
        if (season == null) {
            return "/season/detail/" + seasonId;
        }
        return seasonDetailUrl(season);
    }

    public String seasonDetailUrl(Long seasonId, Long seriesId) {
        String base = seasonDetailUrl(seasonId);
        if (seriesId == null || "#".equals(base)) {
            return base;
        }
        return base + "?seriesId=" + seriesId;
    }

    public String seasonDetailBySeriesUrl(Long seriesId) {
        if (seriesId == null) {
            return "#";
        }
        Series series = seriesService.getById(seriesId);
        if (series == null || series.getSeasonId() == null) {
            return "/season/detail/by-series/" + seriesId;
        }
        return seasonDetailUrl(series.getSeasonId(), seriesId);
    }
}

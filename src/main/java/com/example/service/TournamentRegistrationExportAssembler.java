package com.example.service;

import com.example.dto.TournamentRegistrationPreviewDto;
import com.example.dto.TournamentRegistrationRowDto;
import com.example.entity.Season;
import com.example.entity.Series;
import com.example.entity.Tournament;
import com.example.entity.TournamentLevel;
import com.example.entity.TournamentRegistrationSetting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 报名接龙页「复制为文本」与 PDF 导出共用数据结构。
 */
@Component
public class TournamentRegistrationExportAssembler {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private TournamentService tournamentService;
    @Autowired
    private SeriesService seriesService;
    @Autowired
    private SeasonService seasonService;
    @Autowired
    private ITournamentLevelService tournamentLevelService;
    @Autowired
    private ITournamentRegistrationService registrationService;

    /**
     * @return 赛事不存在时返回 null
     */
    public Map<String, Object> assemble(Long tournamentId, LocalDateTime now) {
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) {
            return null;
        }
        TournamentRegistrationSetting setting = registrationService.getSetting(tournamentId);
        List<TournamentRegistrationRowDto> rows = registrationService.listRows(tournamentId, now);
        TournamentRegistrationPreviewDto preview = registrationService.preview(tournamentId, now);
        String seasonLabel = "";
        Integer edition = null;
        if (t.getSeriesId() != null && t.getLevelCode() != null) {
            Series series = seriesService.getById(t.getSeriesId());
            if (series != null && series.getSeasonId() != null) {
                Season season = seasonService.getById(series.getSeasonId());
                if (season != null) {
                    seasonLabel = season.getYear() + "年" + (season.getHalf() != null && season.getHalf() == 1 ? "上半年" : "下半年");
                    List<Long> seasonSeriesIds = seriesService.lambdaQuery()
                            .eq(Series::getSeasonId, season.getId())
                            .list()
                            .stream()
                            .filter(s -> !isTestSeries(s))
                            .map(Series::getId)
                            .filter(Objects::nonNull)
                            .toList();
                    if (!seasonSeriesIds.isEmpty()) {
                        List<Tournament> sameLevel = tournamentService.lambdaQuery()
                                .in(Tournament::getSeriesId, seasonSeriesIds)
                                .eq(Tournament::getLevelCode, t.getLevelCode())
                                .list();
                        sameLevel.sort(Comparator
                                .comparing(Tournament::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(Tournament::getId, Comparator.nullsLast(Comparator.naturalOrder())));
                        for (int i = 0; i < sameLevel.size(); i++) {
                            if (Objects.equals(sameLevel.get(i).getId(), t.getId())) {
                                edition = i + 1;
                                break;
                            }
                        }
                    }
                }
            }
        }
        TournamentLevel level = t.getLevelCode() == null ? null
                : tournamentLevelService.lambdaQuery().eq(TournamentLevel::getCode, t.getLevelCode()).one();
        String levelText = t.getLevelCode() != null ? t.getLevelCode() : (level != null ? level.getName() : "-");
        String tournamentEditionTitle = (seasonLabel.isEmpty() ? "赛季" : seasonLabel) + "-" + levelText + "-" + (edition == null ? "?" : edition);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("documentTitle", "赛事报名接龙");
        root.put("tournamentEditionTitle", tournamentEditionTitle);
        root.put("tournamentId", t.getId());
        root.put("levelCode", t.getLevelCode() != null ? t.getLevelCode() : "-");
        root.put("seasonLabel", seasonLabel);
        root.put("edition", edition);
        root.put("tournamentStatus", t.getStatus());
        root.put("deadlineText", formatDeadline(setting));
        root.put("registrationEnabled", registrationService.isRegistrationEnabled(t));
        root.put("registrationOpen", registrationService.isRegistrationOpen(t, now));
        root.put("previewModeDescription", preview != null && preview.getModeDescription() != null
                ? preview.getModeDescription() : "");
        root.put("mainDirectUsernames", preview != null && preview.getMainDirectUsernames() != null
                ? preview.getMainDirectUsernames() : List.of());
        root.put("qualifierSeedUsernames", preview != null && preview.getQualifierSeedUsernames() != null
                ? preview.getQualifierSeedUsernames() : List.of());

        List<Map<String, Object>> rowMaps = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            rowMaps.add(toRowMap(i + 1, rows.get(i)));
        }
        root.put("rows", rowMaps);
        return root;
    }

    private static boolean isTestSeries(Series series) {
        if (series == null) return false;
        String name = series.getName();
        return name != null && name.contains("测试");
    }

    private static String formatDeadline(TournamentRegistrationSetting setting) {
        if (setting == null || setting.getDeadline() == null) {
            return "未设置";
        }
        return DT.format(setting.getDeadline());
    }

    private static Map<String, Object> toRowMap(int seq, TournamentRegistrationRowDto row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seq", seq);
        m.put("username", row.getUsername() != null ? row.getUsername() : "-");
        Integer tr = row.getTotalRankPosition();
        m.put("totalRankPosition", tr);
        m.put("totalRankText", tr != null ? ("第 " + tr + " 名") : "—");
        m.put("registeredAt", row.getRegisteredAt() == null ? "-" : DT.format(row.getRegisteredAt()));
        m.put("statusText", statusText(row));
        String note = row.getSeriesCrossNote();
        m.put("seriesCrossNote", note == null || note.isEmpty() ? "—" : note);
        return m;
    }

    private static String statusText(TournamentRegistrationRowDto row) {
        Integer st = row.getStatus();
        if (st != null && st == 1) {
            return "已通过";
        }
        if (st != null && st == 2) {
            return "已拒绝";
        }
        if (st != null && st == 0) {
            return row.isEffectiveApproved() ? "待审（截止视同同意）" : "待审";
        }
        return "-";
    }
}

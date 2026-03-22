package com.example.service;

import com.example.dto.TournamentRegistrationPreviewDto;
import com.example.dto.TournamentRegistrationRowDto;
import com.example.entity.Tournament;
import com.example.entity.TournamentRegistrationSetting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报名接龙页「复制为文本」与 PDF 导出共用数据结构。
 */
@Component
public class TournamentRegistrationExportAssembler {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private TournamentService tournamentService;
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

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("documentTitle", "赛事报名接龙");
        root.put("tournamentId", t.getId());
        root.put("levelCode", t.getLevelCode() != null ? t.getLevelCode() : "-");
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

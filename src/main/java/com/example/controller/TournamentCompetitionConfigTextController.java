package com.example.controller;

import com.example.service.TournamentCompetitionConfigExportAssembler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 赛事配置文本导出（无权限限制，所有用户均可访问）
 */
@RestController
@RequestMapping("/tournament/detail")
public class TournamentCompetitionConfigTextController {

    @Autowired
    private TournamentCompetitionConfigExportAssembler configExportAssembler;

    @GetMapping("/{id}/export/competition-config/text")
    public ResponseEntity<String> exportText(@PathVariable Long id) {
        Map<String, Object> data = configExportAssembler.assemble(id);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        String text = buildTextExport(data);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=competition-config.txt")
                .body(text);
    }

    private String buildTextExport(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(data.get("documentTitle")).append(" ===\n");
        sb.append("赛事ID: ").append(data.get("tournamentId")).append("\n");
        sb.append("等级代码: ").append(data.get("levelCode")).append("\n");
        sb.append("赛季期数: ").append(data.get("tournamentEditionTitle")).append("\n");
        sb.append("赛事状态: ").append(statusText((Integer) data.get("tournamentStatus"))).append("\n");
        sb.append("开始时间: ").append(data.get("startDate")).append("\n");
        sb.append("结束时间: ").append(data.get("endDate")).append("\n");
        sb.append("\n");
        
        sb.append("--- 赛制规则 ---\n");
        String ruleType = (String) data.get("competitionRuleType");
        sb.append("赛制规则: ").append("NORMAL".equals(ruleType) ? "正常5壶保护制" : "自定义").append("\n");
        if ("CUSTOM".equals(ruleType)) {
            Integer centerLimit = (Integer) data.get("customCenterLineLimit");
            Integer deadLimit = (Integer) data.get("customDeadStoneLimit");
            if (centerLimit != null || deadLimit != null) {
                sb.append("中线壶限制: ").append(centerLimit != null ? centerLimit : "默认5").append("\n");
                sb.append("死壶限制: ").append(deadLimit != null ? deadLimit : "默认5").append("\n");
            }
        }
        sb.append("\n");
        
        sb.append("--- 比赛配置 ---\n");
        sb.append("参赛人数: ").append(data.get("participantCount")).append("\n");
        sb.append("报名模式: ").append(data.get("entryModeText")).append("\n");
        sb.append("赛事模式: ").append(data.get("matchModeText")).append("\n");
        sb.append("淘汰赛首轮: ").append(data.get("knockoutStartRoundText")).append("\n");
        sb.append("淘汰赛种子模式: ").append(data.get("knockoutBracketModeText")).append("\n");
        sb.append("小组赛自动生成淘汰赛: ").append(data.get("knockoutAutoFromGroup") != null && (Boolean) data.get("knockoutAutoFromGroup") ? "是" : "否").append("\n");
        sb.append("首轮淘汰赛附加资格赛: ").append(data.get("qualifierRoundText")).append("\n");
        if (data.get("entryModeText").toString().contains("资格赛")) {
            sb.append("正赛资格赛晋级名额: ").append(data.get("knockoutQualifyCount")).append("\n");
        }
        sb.append("小组赛模式: ").append(data.get("groupModeText")).append("\n");
        sb.append("每组人数: ").append(data.get("groupSize") != null ? data.get("groupSize") : "-").append("\n");
        sb.append("小组赛允许平局: ").append(data.get("groupAllowDraw") != null && (Boolean) data.get("groupAllowDraw") ? "是" : "否").append("\n");
        sb.append("小组赛截止时间: ").append(data.get("groupStageDeadline")).append("\n");
        sb.append("小组赛局数: ").append(data.get("groupStageSets")).append("\n");
        sb.append("淘汰赛局数: ").append(data.get("knockoutStageSets") != null ? data.get("knockoutStageSets") : "-").append("\n");
        sb.append("决赛局数: ").append(data.get("finalStageSets") != null ? data.get("finalStageSets") : "-").append("\n");
        sb.append("资格赛局数: ").append(data.get("qualifierSets")).append("\n");
        sb.append("手动锁定: ").append(data.get("manualLocked") != null && (Boolean) data.get("manualLocked") ? "是" : "否").append("\n");
        
        return sb.toString();
    }

    private String statusText(Integer status) {
        if (status == null) return "-";
        return switch (status) {
            case 0 -> "筹备中";
            case 1 -> "进行中";
            case 2 -> "已结束";
            default -> "-";
        };
    }
}
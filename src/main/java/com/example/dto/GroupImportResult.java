package com.example.dto;

import java.util.Collections;
import java.util.List;

/**
 * 小组名单导入解析结果。
 */
public class GroupImportResult {
    private final List<Long> userIds;
    private final List<String> unknownUsernames;

    public GroupImportResult(List<Long> userIds, List<String> unknownUsernames) {
        this.userIds = userIds == null ? List.of() : List.copyOf(userIds);
        this.unknownUsernames = unknownUsernames == null ? List.of() : List.copyOf(unknownUsernames);
    }

    public List<Long> getUserIds() {
        return userIds;
    }

    public List<String> getUnknownUsernames() {
        return unknownUsernames;
    }

    public static GroupImportResult empty() {
        return new GroupImportResult(Collections.emptyList(), Collections.emptyList());
    }
}

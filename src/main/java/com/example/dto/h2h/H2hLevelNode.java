package com.example.dto.h2h;

import java.util.List;

public record H2hLevelNode(String levelCode, String levelName, List<H2hMatchRow> matches) {
}

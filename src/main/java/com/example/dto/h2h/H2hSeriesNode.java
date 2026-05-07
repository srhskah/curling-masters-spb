package com.example.dto.h2h;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

public record H2hSeriesNode(
        @JsonFormat(shape = JsonFormat.Shape.STRING) long seriesId,
        String seriesLabel,
        List<H2hLevelNode> levels) {
}

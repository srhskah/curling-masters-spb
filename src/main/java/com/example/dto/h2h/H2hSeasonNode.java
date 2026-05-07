package com.example.dto.h2h;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

public record H2hSeasonNode(
        @JsonFormat(shape = JsonFormat.Shape.STRING) long seasonId,
        String seasonLabel,
        List<H2hSeriesNode> series) {
}

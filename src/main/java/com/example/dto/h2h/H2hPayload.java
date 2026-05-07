package com.example.dto.h2h;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 显式 JSON 属性名，避免部分 Jackson/序列化配置下 seasons 缺失导致前端读不到数据。
 */
public record H2hPayload(@JsonProperty("seasons") List<H2hSeasonNode> seasons) {
}

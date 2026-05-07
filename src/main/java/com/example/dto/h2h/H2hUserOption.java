package com.example.dto.h2h;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * H2H 选择器中的选手项。
 * id 以 JSON 字符串输出，避免前端 JavaScript 双精度丢失（Snowflake 等 &gt; 2^53-1）。
 */
public record H2hUserOption(
        @JsonFormat(shape = JsonFormat.Shape.STRING) long id,
        String username) {
}

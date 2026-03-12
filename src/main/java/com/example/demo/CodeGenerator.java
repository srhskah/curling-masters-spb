package com.example.demo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;

import java.util.Collections;

public class CodeGenerator {
    public static void main(String[] args) {
        FastAutoGenerator.create("jdbc:mysql://localhost:3306/curling_masters?useSSL=false&serverTimezone=UTC",
                                "cmofficial", "Win3000.375")
            .globalConfig(builder ->
                builder.author("Curling Masters") // 设置作者
                       .outputDir(System.getProperty("user.dir") + "/src/main/java") // 输出路径
                       .disableOpenDir() // 生成后不打开文件夹
            )
            .packageConfig(builder ->
                builder.parent("com.example") // 父包名
                       .entity("entity")      // 实体类包名（你已存在）
                       .mapper("mapper")      // Mapper 包名（你已存在）
                       .service("service")    // Service 包名（将生成）
                       .controller("controller") // Controller 包名（将生成）
                       .pathInfo(Collections.singletonMap(OutputFile.xml, System.getProperty("user.dir") + "/src/main/resources/mapper")) // XML 路径（你已存在）
            )
            .strategyConfig(builder ->
                builder.addInclude("user", "season", "series", "tournament", "tournament_entry", "match", "set_score", "user_tournament_points", "notification", "withdrawal_request") // 需要生成的表名
                        .entityBuilder().enableLombok()   // 如果你用 Lombok
                        .mapperBuilder()
                           .superClass(BaseMapper.class)  // 让 Mapper 继承 BaseMapper
                           .enableBaseResultMap()         // 生成通用的 resultMap
                           .enableBaseColumnList()        // 生成通用列
                        .serviceBuilder().formatServiceFileName("%sService")
                        .controllerBuilder().enableRestStyle()
            )
            .templateEngine(new VelocityTemplateEngine()) // 使用 Velocity 模板
            .execute();
    }
}
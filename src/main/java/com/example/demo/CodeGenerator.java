package com.example.demo;

import java.util.Map;
import java.util.HashMap;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;

import java.util.Collections;

public class CodeGenerator {
    public static void main(String[] args) {
        
        Map<String, String> env = new HashMap<>();
        try {
            Files.lines(Paths.get(".env"))
                .filter(line -> line.contains("=") && !line.trim().startsWith("#"))
                .forEach(line -> {
                    String[] parts = line.split("=", 2);
                    env.put(parts[0].trim(), parts[1].trim());
                });
        } catch (IOException e) {
            throw new RuntimeException("读取 .env 文件失败: " + e.getMessage(), e);
        }
        // String dbUrl = System.getenv("MYSQL_DB_URL");
        // String dbUser = System.getenv("MYSQL_USER");
        // String dbPassword = System.getenv("MYSQL_ROOT_PASSWORD");

        String dbUrl = env.get("MYSQL_DB_URL");
        String dbUser = env.get("MYSQL_USER");
        String dbPassword = env.get("MYSQL_ROOT_PASSWORD");
        
        if (dbUrl == null || dbUser == null || dbPassword == null) {
            throw new RuntimeException("请设置环境变量 MYSQL_DB_URL, MYSQL_DB_USERNAME, MYSQL_DB_PASSWORD");
        }

        // Fallback to system properties if environment variables are not set
        // if (dbUrl == null) {
        //     dbUrl = System.getProperty("MYSQL_DB_URL", "${MYSQL_DB_URL}");
        // }
        // if (dbUser == null) {
        //     dbUser = System.getProperty("MYSQL_USER", "${MYSQL_USER}");
        // }
        // if (dbPassword == null) {
        //     dbPassword = System.getProperty("MYSQL_ROOT_PASSWORD", "${MYSQL_ROOT_PASSWORD}");
        // }
        
        FastAutoGenerator.create(dbUrl, dbUser, dbPassword)
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
            .strategyConfig(builder -> {
                // builder.addInclude("user", "season", "series", "tournament", "tournament_entry", "match", "set_score", "user_tournament_points", "notification", "withdrawal_request") // 需要生成的表名
                //     .entityBuilder()
                //     .enableLombok()
                //     .enableFileOverride()   // **关键：对这些表的实体启用覆盖**
                //     .controllerBuilder()
                //     .enableRestStyle()
                //     .enableFileOverride()   // 覆盖 Controller
                //     .serviceBuilder()
                //     .enableFileOverride()   // 覆盖 Service
                //     .mapperBuilder()
                //     .enableBaseResultMap()
                //     .enableBaseColumnList()
                //     .enableFileOverride();  // 覆盖 Mapper
                builder.addInclude("tournament_level")
                    .entityBuilder()
                    .enableLombok()
                    .enableFileOverride()   // **关键：对这些表的实体启用覆盖**
                    .controllerBuilder()
                    .enableRestStyle()
                    .enableFileOverride()   // 覆盖 Controller
                    .serviceBuilder()
                    .enableFileOverride()   // 覆盖 Service
                    .mapperBuilder()
                    .enableBaseResultMap()
                    .enableBaseColumnList()
                    .enableFileOverride();  // 覆盖 Mapper
            })
            .templateEngine(new VelocityTemplateEngine()) // 使用 Velocity 模板
            .execute();
    }
}
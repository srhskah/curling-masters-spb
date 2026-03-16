package com.example.demo;

import com.example.util.TimezoneUtil;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.example") // 扫描 com.example 及其子包
@MapperScan("com.example.mapper")            // 扫描 Mapper 接口
public class DemoApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		// 设置默认时区为Asia/Shanghai
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
		System.setProperty("user.timezone", "Asia/Shanghai");
		
		// 验证时区设置
		TimezoneUtil.logTimezoneInfo();
		
		SpringApplication.run(DemoApplication.class, args);
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(DemoApplication.class);
	}

}
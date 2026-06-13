package com.noto.zhihui.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notoOpenApi() {
        final String bearerScheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Noto · 知微 API")
                        .description("个人知识管理与行动清单平台 REST API。鉴权：登录后 Header `Authorization: Bearer {token}`。")
                        .version("v1")
                        .contact(new Contact().name("Noto Zhihui")))
                .components(new Components()
                        .addSecuritySchemes(bearerScheme, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT，来自 POST /api/v1/auth/login 或 register")))
                .addSecurityItem(new SecurityRequirement().addList(bearerScheme));
    }
}

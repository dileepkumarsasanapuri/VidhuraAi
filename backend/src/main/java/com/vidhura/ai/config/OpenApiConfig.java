package com.vidhura.ai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI vidhuraOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Vidhura AI — Insurance Intelligence Platform API")
                        .description("High-performance REST API for insurance policy parsing, LLM-powered clause extraction, deterministic claim adjudication, scenario simulation, and portfolio management.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Vidhura AI Engineering")
                                .email("engineering@vidhura.ai"))
                        .license(new License().name("Proprietary").url("https://vidhura.ai")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT Bearer token obtained from /api/auth/login or /api/auth/register")));
    }
}

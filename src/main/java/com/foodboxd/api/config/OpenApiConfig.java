package com.foodboxd.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger'a "Authorize" düğmesi ekler (2.11a): girişten alınan accessToken
 * oraya yapıştırılınca bütün isteklere "Authorization: Bearer …" eklenir.
 * Bildirilen yorumlara müdahale şimdilik buradan admin hesabıyla yapılıyor.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Decouva API"),
        security = @SecurityRequirement(name = "bearer"))
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}

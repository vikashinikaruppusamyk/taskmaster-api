package com.vikashini.taskmaster.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI shows an "Authorize" button for the bearer token, and every endpoint
 * needs it unless it says otherwise.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "TaskMaster API", version = "1.0",
                description = "Collaborative task tracking: accounts, teams, tasks, comments, "
                        + "attachments and notifications."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class OpenApiConfig {
}

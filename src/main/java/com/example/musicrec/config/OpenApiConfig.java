package com.example.musicrec.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Music Recommendation Backend API",
                version = "v1",
                description = "Spring Boot backend + offline Python ML module (analyze/train)"
        )
)
public class OpenApiConfig {
}

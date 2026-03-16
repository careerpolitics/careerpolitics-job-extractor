package com.careerpolitics.scraper.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI careerPoliticsOpenApi(
            @Value("${api.title:CareerPolitics Job Scraper API}") String title,
            @Value("${api.description:REST API for job scraping and content workflows}") String description,
            @Value("${api.version:1.0.0}") String version,
            @Value("${api.contact.name:CareerPolitics Team}") String contactName,
            @Value("${api.contact.email:support@careerpolitics.com}") String contactEmail,
            @Value("${api.contact.url:https://careerpolitics.com}") String contactUrl
    ) {
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description(description)
                        .version(version)
                        .contact(new Contact()
                                .name(contactName)
                                .email(contactEmail)
                                .url(contactUrl)))
                .servers(List.of(
                        new Server().url("/").description("Current environment")
                ));
    }

    @Bean
    public OpenApiCustomizer defaultApiResponsesCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation -> {
                        if (operation.getResponses() == null) {
                            return;
                        }
                        operation.getResponses().putIfAbsent("400", new ApiResponse().description("Bad Request"));
                        operation.getResponses().putIfAbsent("404", new ApiResponse().description("Not Found"));
                        operation.getResponses().putIfAbsent("500", new ApiResponse().description("Internal Server Error"));
                    })
            );
        };
    }
}

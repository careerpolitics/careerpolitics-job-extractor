package com.careerpolitics.scraper.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI careerPoliticsOpenApi(
            @Value("${api.title:CareerPolitics Trending Service API}") String title,
            @Value("${api.description:Browser-friendly API for Selenium-based trending article workflows}") String description,
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
                .servers(List.of(new Server().url("/").description("Current environment")));
    }
}

package com.careerpolitics.scraper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@OpenAPIDefinition(
        info = @Info(
                title = "CareerPolitics Job Scraper API",
                version = "1.0.0",
                description = "REST API for government job scraping and management"
        )
)
public class CareerPoliticsScraperApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerPoliticsScraperApplication.class, args);
    }
}

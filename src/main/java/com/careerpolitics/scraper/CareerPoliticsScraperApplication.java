package com.careerpolitics.scraper;

import com.careerpolitics.scraper.config.TrendingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan(basePackageClasses = TrendingProperties.class)
public class CareerPoliticsScraperApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerPoliticsScraperApplication.class, args);
    }
}

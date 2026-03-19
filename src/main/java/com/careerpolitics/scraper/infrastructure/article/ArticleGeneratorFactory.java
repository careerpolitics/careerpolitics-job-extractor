package com.careerpolitics.scraper.infrastructure.article;

import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.port.ArticleGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Comparator;
import java.util.List;

@Configuration
public class ArticleGeneratorFactory {

    @Bean
    @Primary
    ArticleGenerator articleGenerator(List<ArticleGenerator> generators, TrendingProperties properties) {
        boolean aiEnabled = properties.generation().openRouterEnabled()
                && properties.generation().openRouterApiKey() != null
                && !properties.generation().openRouterApiKey().isBlank();

        return generators.stream()
                .filter(generator -> aiEnabled == generator.supportsAi())
                .min(Comparator.comparing(generator -> generator.getClass().getSimpleName()))
                .orElseGet(() -> generators.stream()
                        .filter(generator -> !generator.supportsAi())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No article generator is available.")));
    }
}

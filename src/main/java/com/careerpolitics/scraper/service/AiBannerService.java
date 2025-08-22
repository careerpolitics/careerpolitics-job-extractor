package com.careerpolitics.scraper.service;


import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AiBannerService {

    private final WebClient webClient;

    public byte[] generateBanner(String title, String tags) {
        try {
            String prompt = String.format("Professional news-style banner for government job post: %s. Include clean typography, subtle rail/tech theme, dark blue gradient background.", title);
            String encoded = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
            String url = "https://image.pollinations.ai/prompt/" + encoded + "?nologo=true&width=1200&height=630";
            return webClient.get()
                    .uri(url)
                    .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.ALL)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .onErrorResume(ex -> Mono.empty())
                    .blockOptional()
                    .orElse(new byte[0]);
        } catch (Exception ex) {
            return new byte[0];
        }
    }
}
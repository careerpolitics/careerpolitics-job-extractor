package com.careerpolitics.scraper.repository;


import com.careerpolitics.scraper.model.WebsiteConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebsiteConfigRepository extends JpaRepository<WebsiteConfig, String> {
}
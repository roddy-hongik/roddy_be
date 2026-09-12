package com.roddy.global.config;

import com.roddy.global.crawler.engine.CrawlHttpClient;
import com.roddy.global.crawler.engine.DeclarativeCrawler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CrawlerConfig {

    @Bean
    public CrawlHttpClient crawlHttpClient() {
        return CrawlHttpClient.create();
    }

    @Bean
    public DeclarativeCrawler declarativeCrawler(CrawlHttpClient crawlHttpClient) {
        return new DeclarativeCrawler(crawlHttpClient);
    }
}

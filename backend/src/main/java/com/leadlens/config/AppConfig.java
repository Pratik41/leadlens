package com.leadlens.config;

import com.leadlens.ingest.CsvLeadParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AppConfig {

    /**
     * Verification + enrichment workers. A bounded thread count keeps us polite to the sites we read
     * and stops a 5,000-row import from opening 5,000 sockets; the queue absorbs the burst.
     */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService enrichmentExecutor(@Value("${leadlens.pipeline.threads:6}") int threads) {
        AtomicInteger n = new AtomicInteger();
        return new ThreadPoolExecutor(Math.max(1, threads), Math.max(1, threads), 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, "enrich-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
    }

    @Bean
    public CsvLeadParser csvLeadParser(@Value("${leadlens.import.max-rows:10000}") int maxRows) {
        return new CsvLeadParser(maxRows);
    }
}

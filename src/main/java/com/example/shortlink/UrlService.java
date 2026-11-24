package com.example.shortlink;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UrlService {
    private record Entry(String url, Instant createdAt, AtomicLong clicks) {}
    public record LinkInfo(String code, String url, Instant createdAt, long clicks) {}

    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Counter created;
    private final Counter redirects;

    public UrlService(MeterRegistry registry) {
        this.created = registry.counter("shortlink.shortened");
        this.redirects = registry.counter("shortlink.redirects");
        Gauge.builder("shortlink.active", store, Map::size).register(registry);
    }

    /** Crée un lien ; vide si l'alias demandé est déjà pris. */
    public Optional<String> shorten(String url, String alias) {
        Entry entry = new Entry(url, Instant.now(), new AtomicLong());
        String code = alias;
        if (code == null || code.isBlank()) {
            do {
                code = randomCode();
            } while (store.putIfAbsent(code, entry) != null);
        } else if (store.putIfAbsent(code, entry) != null) {
            return Optional.empty();
        }
        created.increment();
        return Optional.of(code);
    }

    public Optional<String> resolve(String code) {
        Entry entry = store.get(code);
        if (entry == null) return Optional.empty();
        entry.clicks().incrementAndGet();
        redirects.increment();
        return Optional.of(entry.url());
    }

    public Optional<LinkInfo> info(String code) {
        return Optional.ofNullable(store.get(code)).map(e -> toInfo(code, e));
    }

    public List<LinkInfo> top(int limit) {
        return store.entrySet().stream().map(e -> toInfo(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(LinkInfo::clicks).reversed()).limit(limit).toList();
    }

    public boolean delete(String code) {
        return store.remove(code) != null;
    }

    private LinkInfo toInfo(String code, Entry e) {
        return new LinkInfo(code, e.url(), e.createdAt(), e.clicks().get());
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        return sb.toString();
    }
}
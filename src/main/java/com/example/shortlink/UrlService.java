package com.example.shortlink;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UrlService {
    private record Entry(String url, AtomicLong clicks) {}

    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Counter created;
    private final Counter redirects;

    public UrlService(MeterRegistry registry) {
        this.created = registry.counter("shortlink.created");
        this.redirects = registry.counter("shortlink.redirects");
    }

    public String shorten(String url) {
        String code;
        do {
            code = randomCode();
        } while (store.putIfAbsent(code, new Entry(url, new AtomicLong())) != null);
        created.increment();
        return code;
    }

    public Optional<String> resolve(String code) {
        Entry entry = store.get(code);
        if (entry == null) return Optional.empty();
        entry.clicks().incrementAndGet();
        redirects.increment();
        return Optional.of(entry.url());
    }

    public Optional<Long> clicks(String code) {
        return Optional.ofNullable(store.get(code)).map(e -> e.clicks().get());
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        return sb.toString();
    }
}
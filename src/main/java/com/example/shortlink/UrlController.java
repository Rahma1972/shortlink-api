package com.example.shortlink;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
public class UrlController {
    private static final Logger log = LoggerFactory.getLogger(UrlController.class);
    private final UrlService service;

    public UrlController(UrlService service) {
        this.service = service;
    }

    public record ShortenRequest(@NotBlank @Pattern(regexp = "^https?://\\S+") String url) {}

    @PostMapping("/shorten")
    public ResponseEntity<Map<String, String>> shorten(@Valid @RequestBody ShortenRequest req) {
        try {
            URI.create(req.url());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        String code = service.shorten(req.url());
        log.info("url shortened code={}", code);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("code", code, "shortUrl", "/" + code));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        return service.resolve(code)
                .map(url -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/stats/{code}")
    public ResponseEntity<Map<String, Long>> stats(@PathVariable String code) {
        return service.clicks(code)
                .map(c -> ResponseEntity.ok(Map.of("clicks", c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
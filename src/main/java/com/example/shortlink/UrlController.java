package com.example.shortlink;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
public class UrlController {
    private static final Logger log = LoggerFactory.getLogger(UrlController.class);
    private final UrlService service;

    public UrlController(UrlService service) { this.service = service; }

    public record ShortenRequest(
            @NotBlank @Pattern(regexp = "^https?://\\S+") String url,
            @Pattern(regexp = "^(?!(links|actuator)$)[a-zA-Z0-9_-]{3,20}$") String alias) {}

    @PostMapping("/shorten")
    public ResponseEntity<Map<String, String>> shorten(@Valid @RequestBody ShortenRequest req) {
        try {
            URI.create(req.url());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return service.shorten(req.url(), req.alias())
                .map(code -> {
                    log.info("url shortened code={}", code);
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("code", code, "shortUrl", "/" + code));
                })
                .orElseGet(() -> {
                    log.warn("alias unavailable alias={}", req.alias());
                    return ResponseEntity.status(HttpStatus.CONFLICT).build();
                });
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        return service.resolve(code)
                .map(url -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/stats/{code}")
    public ResponseEntity<UrlService.LinkInfo> stats(@PathVariable String code) {
        return service.info(code).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/links")
    public List<UrlService.LinkInfo> top(@RequestParam(defaultValue = "10") int limit) {
        return service.top(Math.min(Math.max(limit, 1), 100));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        if (!service.delete(code)) return ResponseEntity.notFound().build();
        log.info("link deleted code={}", code);
        return ResponseEntity.noContent().build();
    }
}
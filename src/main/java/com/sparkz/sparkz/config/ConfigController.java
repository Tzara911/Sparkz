package com.sparkz.sparkz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Hands the login page the two PUBLIC values it needs to talk to Supabase Auth. */
@RestController
public class ConfigController {

    private final String url;
    private final String key;

    public ConfigController(@Value("${SUPABASE_URL}") String url,
                            @Value("${SUPABASE_PUBLISHABLE_KEY}") String key) {
        this.url = url;
        this.key = key;
    }

    @GetMapping("/api/config")
    public Map<String, String> config() {
        return Map.of("supabaseUrl", url, "supabaseKey", key);
    }
}

package com.sparkz.sparkz.discovery;

import java.security.Principal;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {
    private final DiscoveryService service;
    public DiscoveryController(DiscoveryService service) { this.service = service; }
    @GetMapping
    public DiscoveryService.Page browse(Principal principal,
            @RequestParam(required = false) @Positive Long skillId,
            @RequestParam(required = false) DiscoveryService.SkillType skillType,
            @RequestParam(required = false) @Min(1) @Max(7) Integer day,
            @RequestParam(defaultValue = "false") boolean recommended,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.browse(UUID.fromString(principal.getName()), skillId, skillType, day, recommended, offset, limit);
    }
    @GetMapping("/{userId}")
    public DiscoveryService.Person profile(Principal principal, @PathVariable UUID userId) {
        return service.profile(UUID.fromString(principal.getName()), userId);
    }
}

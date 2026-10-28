package com.sparkz.sparkz.discovery;

import com.sparkz.sparkz.matching.CurrentUser;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {
    public enum Mode { recommended, browse }
    private final DiscoveryService service;
    public DiscoveryController(DiscoveryService service) { this.service = service; }
    @GetMapping
    public DiscoveryProfile.Page discover(Principal principal,
            @RequestParam(defaultValue = "recommended") Mode mode,
            @RequestParam(required = false) @Positive Long teaches,
            @RequestParam(required = false) @Positive Long learns,
            @RequestParam(required = false) @Min(1) @Max(7) Integer day,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return service.discover(CurrentUser.id(principal), mode == Mode.recommended, teaches, learns, day, page, size);
    }
    @GetMapping("/skills")
    public List<DiscoveryProfile.Skill> skills(Principal principal) { return service.catalog(CurrentUser.id(principal)); }
}

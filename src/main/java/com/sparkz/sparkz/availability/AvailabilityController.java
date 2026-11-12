package com.sparkz.sparkz.availability;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.LocalTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profiles/me/availability")
public class AvailabilityController {
    private final AvailabilityService service;
    public AvailabilityController(AvailabilityService service) { this.service = service; }
    public record Days(@NotNull @Size(max = 7) List<@NotNull @Min(1) @Max(7) Integer> days) {}
    public record SlotInput(@NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}
    @GetMapping
    public AvailabilityService.Availability own(Principal principal) { return service.own(UUID.fromString(principal.getName())); }
    @PutMapping("/days")
    public AvailabilityService.Availability days(Principal principal, @Valid @RequestBody Days body) {
        return service.replaceDays(UUID.fromString(principal.getName()), body.days());
    }
    @PostMapping("/slots")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityService.Slot add(Principal principal, @Valid @RequestBody SlotInput body) {
        return service.saveSlot(UUID.fromString(principal.getName()), null, body.dayOfWeek(), body.startTime(), body.endTime());
    }
    @PutMapping("/slots/{id}")
    public AvailabilityService.Slot update(Principal principal, @PathVariable @Positive long id, @Valid @RequestBody SlotInput body) {
        return service.saveSlot(UUID.fromString(principal.getName()), id, body.dayOfWeek(), body.startTime(), body.endTime());
    }
    @DeleteMapping("/slots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal principal, @PathVariable @Positive long id) {
        service.deleteSlot(UUID.fromString(principal.getName()), id);
    }
}

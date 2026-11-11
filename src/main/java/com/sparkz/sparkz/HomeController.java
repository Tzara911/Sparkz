package com.sparkz.sparkz;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    @GetMapping({"/", "/index.html"})
    public ResponseEntity<Void> home() {
        // Keep the redirect relative so a deployment proxy controls the public scheme/host.
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/browse.html")).build();
    }
}

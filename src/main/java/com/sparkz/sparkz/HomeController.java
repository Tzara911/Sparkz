package com.sparkz.sparkz;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping({"/", "/index.html"})
    public String home() {
        return "redirect:/browse.html";
    }
}

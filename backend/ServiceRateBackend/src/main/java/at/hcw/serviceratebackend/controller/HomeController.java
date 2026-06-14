package at.hcw.serviceratebackend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        // Redirect root to Swagger UI so users can explore the API easily
        return "redirect:/swagger-ui/index.html";
    }
}

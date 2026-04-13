package com.example.musicrec.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping({"/", "/player"})
    public String playerPage() {
        return "forward:/player.html";
    }

    @GetMapping("/auth")
    public String authPage() {
        return "forward:/auth.html";
    }

    @GetMapping("/admin")
    public String adminPage() {
        return "forward:/admin";
    }
}
package com.example.musicrec.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping({"/", "/player"})
    public String playerPage() {
        return "player";
    }

    @GetMapping("/admin")
    public String adminPage() {
        return "admin";
    }
}

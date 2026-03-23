package com.gorani.gorani_pay.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sample")
public class SampleController {

    @GetMapping("/test")
    public String test() {
        return "고라니 pay is found!";
    }
}

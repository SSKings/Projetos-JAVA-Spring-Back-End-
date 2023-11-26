package com.sskings.websecurity.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ControllerExample {
	
	@GetMapping("/login")
	public String login() {
		return "login";
	}
	
	@GetMapping({"/", "/home"}) 
	public String home() {
		return "home";
	}
	
	@GetMapping("hello")
	public String hello() {
		return "hello";
	}
}

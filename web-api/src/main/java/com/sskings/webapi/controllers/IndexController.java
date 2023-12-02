package com.sskings.webapi.controllers;

import java.util.Properties;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/")
public class IndexController {
	
	@GetMapping("/")
	public String index() {
		return "index";
	}

	@GetMapping("properties")
	@ResponseBody
	Properties properties() {
		return System.getProperties();
	}
}

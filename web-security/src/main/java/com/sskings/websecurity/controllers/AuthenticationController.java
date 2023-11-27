package com.sskings.websecurity.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestParam;

import com.sskings.websecurity.models.User;
import com.sskings.websecurity.repositories.UserRepository;

@Controller
public class AuthenticationController {
	
	@Autowired
	private AuthenticationManager authenticationManager;
	
	@Autowired
	private UserRepository userRepository;
	
	@GetMapping("/login")
	public String getLoginPage() {
		return "login";
	}
	
	@PostMapping("/login")
	public String login(@RequestParam("username") String username,
    		@RequestParam("password") String password) {
		var usernamePassword = new UsernamePasswordAuthenticationToken(
				username, password);
		
		this.authenticationManager.authenticate(usernamePassword);
		
		return "redirect:/home";
	}
	
	
	@GetMapping({"/", "/home"}) 
	public String home() {
		return "home";
	}
	
	@GetMapping("/hello")
	public String hello() {
		return "hello";
	}
	
	@GetMapping("/register")
	public String registerForm() {
		return "register";
	}
	
	@PostMapping("/register")
    public String registerUser(@RequestParam("username") String username,
    		@RequestParam("password") String password, Model model) {
		
		String encryptedPassword = new BCryptPasswordEncoder().encode(password);
		System.out.println("Username: " + username);
		System.out.println("Senha criptografada: " + encryptedPassword);
        User newUser = new User(username, encryptedPassword);
        userRepository.save(newUser);
        model.addAttribute("registrationSuccess", "registrado com sucesso.");
        return "redirect:/login";
    }
}

package com.sskings.webapi.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sskings.webapi.models.Cliente;
import com.sskings.webapi.services.ClienteService;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/clientes")
public class ClienteController {
	
	private final ClienteService clienteService;
	private final String CLIENTE_URI = "clientes/";

	public ClienteController(ClienteService clienteRepository) {
		this.clienteService = clienteRepository;
	}

	@GetMapping("/")
	public ModelAndView list() {
		Iterable<Cliente> clientes = this.clienteService.findAll();
		return new ModelAndView(CLIENTE_URI + "list","clientes",clientes);
	}

	@GetMapping("{id}")
	public ModelAndView view(@PathVariable("id") Cliente cliente) {
		return new ModelAndView(CLIENTE_URI + "view","cliente",cliente);
	}

	@GetMapping("/novo")
	public String createForm(@ModelAttribute Cliente cliente) {
		return CLIENTE_URI + "form";
	}

	@PostMapping("/form")
	public ModelAndView create(@Valid Cliente cliente,BindingResult result,RedirectAttributes redirect) {
		if (result.hasErrors()) { 
			return new ModelAndView(CLIENTE_URI + "form","formErrors",result.getAllErrors()); 
		}
		cliente = this.clienteService.save(cliente);
		redirect.addFlashAttribute("globalMessage","Cliente gravado com sucesso.");
		return new ModelAndView("redirect:/" + CLIENTE_URI + "{cliente.id}","cliente.id",cliente.getId());
	}

	@GetMapping(value = "remover/{id}")
	public ModelAndView remover(@PathVariable("id") Long id,RedirectAttributes redirect) {
		this.clienteService.deleteById(id);
		Iterable<Cliente> clientes = this.clienteService.findAll();		
		return new ModelAndView(CLIENTE_URI + "list","clientes",clientes)
				.addObject("globalMessage","Cliente removido com sucesso.");
	}

	@GetMapping(value = "alterar/{id}")
	public ModelAndView alterarForm(@PathVariable("id") Cliente cliente) {
		return new ModelAndView(CLIENTE_URI + "form","cliente",cliente);
	}

    
    
}

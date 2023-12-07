package com.sskings.webapi.controllers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
import com.sskings.webapi.models.Item;
import com.sskings.webapi.models.Pedido;
import com.sskings.webapi.repositories.ClienteRepository;
import com.sskings.webapi.repositories.ItemRepository;
import com.sskings.webapi.repositories.PedidoRepository;
import com.sskings.webapi.services.ClienteService;
import com.sskings.webapi.services.ItemService;
import com.sskings.webapi.services.PedidoService;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/pedidos")
public class PedidoController {

	private final PedidoService pedidoService;
	private final ItemService itemService;
	private final ClienteService clienteService;
	private final String PEDIDO_URI = "pedidos/";

	public PedidoController(PedidoService pedidoService,ItemService itemService,
			ClienteService clienteService) {
		this.pedidoService = pedidoService;
		this.itemService = itemService;
		this.clienteService = clienteService;
	}

	@GetMapping("/")
	public ModelAndView list() {
		Iterable<Pedido> pedidos = this.pedidoService.findAll();
		return new ModelAndView(PEDIDO_URI + "list","pedidos",pedidos);
	}

	@GetMapping("{id}")
	public ModelAndView view(@PathVariable("id") Pedido pedido) {
		return new ModelAndView(PEDIDO_URI + "view","pedido",pedido);
	}

	@GetMapping("/novo")
	public ModelAndView createForm(@ModelAttribute Pedido pedido) {

		Map<String,Object> model = new HashMap<String,Object>();
		model.put("todosItens",itemService.findAll());
		model.put("todosClientes",clienteService.findAll());
		return new ModelAndView(PEDIDO_URI + "form",model);
		 
	}

	@PostMapping("/form")
	public ModelAndView create(@Valid Pedido pedido,BindingResult result,RedirectAttributes redirect) {
		if (result.hasErrors()) { return new ModelAndView(PEDIDO_URI + "form","formErrors",result.getAllErrors()); }

		if (pedido.getId() != null) {
			Optional<Pedido> pedidoParaAlterarOpt = pedidoService.findById(pedido.getId());
			Pedido pedidoParaAlterar = pedidoParaAlterarOpt.get();
			
			Optional<Cliente> clienteOpt = clienteService.findById(pedidoParaAlterar.getCliente().getId());
			Cliente c = clienteOpt.get();
			
			pedidoParaAlterar.setItens(pedido.getItens());
			BigDecimal valorTotal = BigDecimal.ZERO;
			for (Item i : pedido.getItens()) {
				valorTotal = valorTotal.add(i.getPreco());
			}
			pedidoParaAlterar.setData(pedido.getData());
			pedidoParaAlterar.setValorTotal(valorTotal);			
			c.getPedidos().remove(pedidoParaAlterar);
			c.getPedidos().add(pedidoParaAlterar);
			this.clienteService.save(c);
		} else {
			Optional<Cliente> clienteOpt = clienteService.findById(pedido.getCliente().getId());
			Cliente c = clienteOpt.get();
			BigDecimal valorTotal = BigDecimal.ZERO;
			for (Item i : pedido.getItens()) {
				valorTotal = valorTotal.add(i.getPreco());
			}
			pedido.setValorTotal(valorTotal);
			pedido = this.pedidoService.save(pedido);
			c.getPedidos().add(pedido);
			this.clienteService.save(c);
		}
		redirect.addFlashAttribute("globalMessage","Pedido gravado com sucesso");
		return new ModelAndView("redirect:/" + PEDIDO_URI + "{pedido.id}","pedido.id",pedido.getId());
	}

	@GetMapping("remover/{id}")
	public ModelAndView remover(@PathVariable("id") Long id,RedirectAttributes redirect) {

		Optional<Pedido> pedidoParaRemoverOpt = pedidoService.findById(id);
		Pedido pedidoParaRemover = pedidoParaRemoverOpt.get();

		Optional<Cliente> clienteOpt = clienteService.findById(pedidoParaRemover.getCliente().getId());
		Cliente c = clienteOpt.get();
		c.getPedidos().remove(pedidoParaRemover);

		this.clienteService.save(c);
		this.pedidoService.deleteById(id);

		Iterable<Pedido> pedidos = this.pedidoService.findAll();

		return new ModelAndView(PEDIDO_URI + "list","pedidos",pedidos)
				.addObject("globalMessage","Pedido removido com sucesso");

		
	}

	@GetMapping("alterar/{id}")
	public ModelAndView alterarForm(@PathVariable("id") Pedido pedido) {

		Map<String,Object> model = new HashMap<String,Object>();
		model.put("todosItens",itemService.findAll());
		model.put("todosClientes",clienteService.findAll());
		model.put("pedido",pedido);

		return new ModelAndView(PEDIDO_URI + "form",model);
	}

}


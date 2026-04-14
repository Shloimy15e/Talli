package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.repository.ClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/clients")
public class ClientController {

    private final ClientRepository clientRepository;

    public ClientController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    // List page
    @GetMapping
    public String index(Model model) {
        model.addAttribute("clients", clientRepository.findAll());
        return "clients/index";
    }

    // Returns just the form fragment — loaded into modal via HTMX
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("client", new Client());
        model.addAttribute("action", "/clients");
        model.addAttribute("title", "New Client");
        return "clients/_form :: form";
    }

    // Same fragment, pre-filled for editing
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Client client = clientRepository.findById(id).orElseThrow();
        model.addAttribute("client", client);
        model.addAttribute("action", "/clients/" + id);
        model.addAttribute("title", "Edit Client");
        return "clients/_form :: form";
    }

    // Create
    @PostMapping
    public String create(@ModelAttribute Client client) {
        clientRepository.save(client);
        return "redirect:/clients";
    }

    // Update
    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute Client client) {
        Client existing = clientRepository.findById(id).orElseThrow();
        existing.setName(client.getName());
        existing.setEmail(client.getEmail());
        existing.setNotes(client.getNotes());
        clientRepository.save(existing);
        return "redirect:/clients";
    }

    // Delete
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        clientRepository.deleteById(id);
        return "redirect:/clients";
    }
}

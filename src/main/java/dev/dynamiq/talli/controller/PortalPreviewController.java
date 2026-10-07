package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/** Read-only admin preview using the same rendering as the client's portal. */
@Controller
@RequestMapping("/clients/{clientId}/portal")
public class PortalPreviewController {
    private final ClientRepository clients;
    private final InvoiceRepository invoices;
    private final ProjectRepository projects;
    private final PortalController portal;
    private final PortalWebsiteController website;

    public PortalPreviewController(ClientRepository clients, InvoiceRepository invoices,
                                   ProjectRepository projects, PortalController portal,
                                   PortalWebsiteController website) {
        this.clients = clients;
        this.invoices = invoices;
        this.projects = projects;
        this.portal = portal;
        this.website = website;
    }

    @GetMapping
    public String dashboard(@PathVariable Long clientId, Model model) {
        return portal.renderDashboard(previewClient(clientId, model), model);
    }

    @GetMapping("/invoices/{id}")
    public String invoice(@PathVariable Long clientId, @PathVariable Long id, Model model) {
        Client client = previewClient(clientId, model);
        var invoice = invoices.findById(id)
                .filter(item -> item.getClient().getId().equals(client.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return portal.renderInvoice(invoice, model);
    }

    @GetMapping("/statement")
    public ResponseEntity<byte[]> statement(@PathVariable Long clientId) {
        return portal.renderStatement(findClient(clientId));
    }

    @GetMapping("/projects/{projectId}/website")
    public String website(@PathVariable Long clientId, @PathVariable Long projectId, Model model) {
        Client client = previewClient(clientId, model);
        var project = projects.findById(projectId)
                .filter(item -> item.getClient() != null
                        && item.getClient().getId().equals(client.getId())
                        && Boolean.TRUE.equals(item.getWebsiteEnabled()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return website.renderEditor(project, model);
    }

    private Client previewClient(Long clientId, Model model) {
        Client client = findClient(clientId);
        model.addAttribute("portalPreview", true);
        model.addAttribute("previewClient", client);
        model.addAttribute("portalBase", "/clients/" + clientId + "/portal");
        return client;
    }

    private Client findClient(Long clientId) {
        return clients.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}

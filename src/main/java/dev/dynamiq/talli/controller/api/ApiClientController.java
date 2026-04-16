package dev.dynamiq.talli.controller.api;

import dev.dynamiq.talli.controller.api.dto.ClientResponse;
import dev.dynamiq.talli.controller.api.dto.CreateClientRequest;
import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.repository.ClientRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clients")
public class ApiClientController {

    private final ClientRepository clientRepository;

    public ApiClientController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @GetMapping
    public List<ClientResponse> list(@RequestParam(required = false) String q) {
        return clientRepository.findAll().stream()
                .filter(c -> q == null || q.isBlank()
                        || c.getName().toLowerCase().contains(q.toLowerCase())
                        || (c.getEmail() != null && c.getEmail().toLowerCase().contains(q.toLowerCase())))
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> get(@PathVariable Long id) {
        return clientRepository.findById(id)
                .map(c -> ResponseEntity.ok(toResponse(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody CreateClientRequest req) {
        Client c = new Client();
        c.setName(req.name());
        c.setEmail(req.email());
        c.setPhone(req.phone());
        c.setPaymentTermsDays(req.paymentTermsDays() != null ? req.paymentTermsDays() : 30);
        clientRepository.save(c);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(c));
    }

    private ClientResponse toResponse(Client c) {
        return new ClientResponse(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getPaymentTermsDays());
    }
}

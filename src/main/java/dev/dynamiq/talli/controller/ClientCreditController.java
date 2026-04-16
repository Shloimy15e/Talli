package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.service.ClientCreditService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/clients/{clientId}/credits")
public class ClientCreditController {

    private final ClientCreditService clientCreditService;

    public ClientCreditController(ClientCreditService clientCreditService) {
        this.clientCreditService = clientCreditService;
    }

    @PostMapping
    public String create(@PathVariable("clientId") Long clientId,
                         @RequestParam("amount") BigDecimal amount,
                         @RequestParam("currency") String currency,
                         @RequestParam("receivedAt") String receivedAt,
                         @RequestParam(value = "projectId", required = false) Long projectId,
                         @RequestParam(value = "description", required = false) String description,
                         @RequestParam(value = "returnTo", required = false) String returnTo,
                         RedirectAttributes flash) {
        try {
            clientCreditService.create(clientId, projectId, amount, currency,
                    receivedAt != null && !receivedAt.isBlank() ? LocalDate.parse(receivedAt) : LocalDate.now(),
                    description);
            flash.addFlashAttribute("creditSuccess", "Credit recorded.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("creditError", e.getMessage());
        }
        if (returnTo != null && returnTo.startsWith("/")) {
            return "redirect:" + returnTo;
        }
        return "redirect:/clients/" + clientId;
    }

    @PostMapping("/{creditId}/delete")
    public String delete(@PathVariable("clientId") Long clientId,
                         @PathVariable("creditId") Long creditId,
                         RedirectAttributes flash) {
        try {
            clientCreditService.delete(creditId);
            flash.addFlashAttribute("creditSuccess", "Credit deleted.");
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("creditError", e.getMessage());
        }
        return "redirect:/clients/" + clientId;
    }
}

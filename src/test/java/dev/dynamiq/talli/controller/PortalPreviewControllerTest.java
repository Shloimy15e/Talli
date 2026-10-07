package dev.dynamiq.talli.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.PdfService;
import dev.dynamiq.talli.service.website.NorthlightWebsiteAdapter;
import dev.dynamiq.talli.service.website.WebsiteContentService;
import dev.dynamiq.talli.support.RefreshDatabaseTest;
import dev.dynamiq.talli.support.factory.NorthlightWebsiteFactory;
import dev.dynamiq.talli.support.factory.ProjectFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@RefreshDatabaseTest
@AutoConfigureMockMvc
class PortalPreviewControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ClientRepository clients;
    @Autowired InvoiceRepository invoices;
    @Autowired ProjectRepository projects;
    @Autowired UserRepository users;
    @MockitoBean WebsiteContentService websiteContent;
    @MockitoBean PdfService pdf;

    Client client;
    Client other;
    Invoice invoice;
    Invoice otherInvoice;
    Project project;
    Project otherProject;

    @BeforeEach
    void createRecords() {
        client = saveClient("Preview client");
        other = saveClient("Other client");
        invoice = saveInvoice(client, "PREVIEW-101");
        otherInvoice = saveInvoice(other, "OTHER-202");
        project = saveProject(client);
        otherProject = saveProject(other);
        User user = new User();
        user.setEmail("client@example.test");
        user.setName("Client user");
        user.setPassword("unused");
        user.setClient(client);
        users.saveAndFlush(user);
    }

    @Test
    void adminDashboardReusesClientContentAndKeepsNavigationInPreview() throws Exception {
        var preview = mvc.perform(get(base()).session(admin())).andExpect(status().isOk())
                .andExpect(view().name("portal/dashboard")).andReturn();
        var normal = mvc.perform(get("/portal").session(clientSession())).andExpect(status().isOk()).andReturn();
        assertThat(preview.getResponse().getContentAsString())
                .contains("Preview client", "PREVIEW-101", "Portal preview", "View only",
                        "href=\"" + base() + "/invoices/" + invoice.getId() + "\"",
                        "href=\"" + base() + "/projects/" + project.getId() + "/website\"",
                        "href=\"" + base() + "/statement\"", "href=\"/clients/" + client.getId() + "\"")
                .doesNotContain("OTHER-202", "href=\"/portal");
        for (String attribute : new String[]{"totalBilled", "totalPaid", "outstanding", "summaryCurrency", "aging"}) {
            assertThat(preview.getModelAndView().getModel().get(attribute))
                    .isEqualTo(normal.getModelAndView().getModel().get(attribute));
        }
        assertThat(normal.getResponse().getContentAsString())
                .contains("PREVIEW-101", "href=\"/portal/invoices/" + invoice.getId() + "\"")
                .doesNotContain("OTHER-202", "Portal preview");
    }

    @Test
    void allPreviewPathsRequireAdminEvenWhenUserHasClientAndPortalAccess() throws Exception {
        for (String path : new String[]{base(), base() + "/invoices/" + invoice.getId(),
                base() + "/statement", base() + "/projects/" + project.getId() + "/website"}) {
            mvc.perform(get(path).session(session("client@example.test", "view-clients", "portal-access")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void missingClientsAndOtherClientsResourcesAreNotFound() throws Exception {
        for (String path : new String[]{"/clients/999999/portal", "/clients/999999/portal/statement",
                base() + "/invoices/" + otherInvoice.getId(),
                base() + "/projects/" + otherProject.getId() + "/website"}) {
            mvc.perform(get(path).session(admin())).andExpect(status().isNotFound());
        }
        verify(websiteContent, never()).load(any());
    }

    @Test
    void invoicePreviewUsesSameViewAndClientNavigation() throws Exception {
        var preview = mvc.perform(get(base() + "/invoices/" + invoice.getId()).session(admin()))
                .andExpect(status().isOk()).andExpect(view().name("portal/invoice")).andReturn();
        var normal = mvc.perform(get("/portal/invoices/" + invoice.getId()).session(clientSession()))
                .andExpect(status().isOk()).andReturn();
        assertThat(preview.getModelAndView().getModel().get("balance"))
                .isEqualTo(normal.getModelAndView().getModel().get("balance"));
        assertThat(preview.getResponse().getContentAsString()).contains("PREVIEW-101", "href=\"" + base() + "\"");
        mvc.perform(get("/portal/invoices/" + otherInvoice.getId()).session(clientSession()))
                .andExpect(view().name("portal/error"));
    }

    @Test
    void statementPreviewRendersSelectedClientsInvoices() throws Exception {
        when(pdf.renderStatement(eq(client), any(), any(), eq("USD"))).thenReturn(new byte[]{1, 2, 3});
        mvc.perform(get(base() + "/statement").session(admin())).andExpect(status().isOk());
        verify(pdf).renderStatement(eq(client), eq(java.util.List.of(invoice)), any(), eq("USD"));
    }

    @Test
    void websitePreviewLoadsSameFormAndCannotPublishEvenWithCsrf() throws Exception {
        var form = new NorthlightWebsiteAdapter(new ObjectMapper()).toEditorForm(NorthlightWebsiteFactory.repoFiles());
        when(websiteContent.load(project)).thenReturn(form);
        var session = admin();
        var result = mvc.perform(get(base() + "/projects/" + project.getId() + "/website").session(session))
                .andExpect(status().isOk()).andExpect(view().name("portal/website")).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains("data-preview=\"true\"", "disabled=\"disabled\"", "href=\"" + base() + "\"",
                        "action=\"" + base() + "/projects/" + project.getId() + "/website?")
                .doesNotContain("action=\"/portal/projects/");
        var csrf = (CsrfToken) result.getRequest().getAttribute("_csrf");
        mvc.perform(post(base() + "/projects/" + project.getId() + "/website").session(session)
                        .param(csrf.getParameterName(), csrf.getToken()).param("homeHeroHeadline", "Change"))
                .andExpect(status().isMethodNotAllowed());
        verify(websiteContent, never()).save(any(), any(), any());
        mvc.perform(get("/portal/projects/" + otherProject.getId() + "/website").session(clientSession()))
                .andExpect(view().name("portal/error"));
    }

    @Test
    void clientPageShowsPreviewButtonOnlyForAdmin() throws Exception {
        var request = "/clients/" + client.getId();
        assertThat(mvc.perform(get(request).session(session("admin@example.test", "ROLE_admin", "view-clients")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .contains("View portal", "href=\"" + base() + "\"");
        assertThat(mvc.perform(get(request).session(session("client@example.test", "view-clients")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).doesNotContain("View portal");
    }

    private String base() { return "/clients/" + client.getId() + "/portal"; }
    private MockHttpSession admin() { return session("admin@example.test", "ROLE_admin"); }
    private MockHttpSession clientSession() { return session("client@example.test", "portal-access"); }

    private MockHttpSession session(String email, String... authorities) {
        var auth = new UsernamePasswordAuthenticationToken(email, null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private Client saveClient(String name) {
        var client = new Client();
        client.setName(name);
        return clients.saveAndFlush(client);
    }

    private Invoice saveInvoice(Client client, String reference) {
        var invoice = new Invoice();
        invoice.setClient(client);
        invoice.setReference(reference);
        invoice.setAmount(new BigDecimal("120.00"));
        invoice.setIssuedAt(LocalDate.now());
        return invoices.saveAndFlush(invoice);
    }

    private Project saveProject(Client client) {
        var project = ProjectFactory.connectedWebsiteProject();
        project.setId(null);
        project.setClient(client);
        project.setRateType("fixed");
        project.setCurrentRate(new BigDecimal("120.00"));
        return projects.saveAndFlush(project);
    }
}

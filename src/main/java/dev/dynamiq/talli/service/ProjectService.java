package dev.dynamiq.talli.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.InvoiceItemRepository;
import dev.dynamiq.talli.repository.ProjectRepository;

@Service
public class ProjectService {

    private final InvoiceItemRepository invoiceItemRepository;
    private final TimeEntryService timeEntryService;
    private final ProjectRepository projectRepository;

    public ProjectService(InvoiceItemRepository invoiceItemRepository, TimeEntryService timeEntryService,
            ProjectRepository projectRepository) {
        this.invoiceItemRepository = invoiceItemRepository;
        this.projectRepository = projectRepository;
        this.timeEntryService = timeEntryService;
    }

    public ProjectSummary summary(Long projectId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        BigDecimal billedToDate = invoiceItemRepository.sumTotalByProjectId(projectId);

        TimeEntryService.ProjectTimeTotals timeTotals =
                timeEntryService.totalsForProject(projectId, project.hourlyRate());
        long invoiceCount = invoiceItemRepository.findInvoicesByProjectId(projectId).size();

        BigDecimal unbilledValue = null;
        BigDecimal remaining = null;
        BigDecimal monthBilled = null;

        if (project.isHourly()) {
            unbilledValue = timeTotals.unbilledValue();
        } else if (project.isFixed()) {
            remaining = project.contractAmount().subtract(billedToDate);
        } else if (project.isRetainer()) {
            LocalDate from = LocalDate.now().withDayOfMonth(1);
            LocalDate to = from.plusMonths(1);
            monthBilled = invoiceItemRepository.sumTotalByProjectIdBetween(projectId, from, to);
        }

        return new ProjectSummary(billedToDate, unbilledValue, remaining, monthBilled,
                timeTotals.entryCount(), invoiceCount);
    }

    /**
     * Change a project's rate/contract amount, appending a dated change-order note
     * to project.notes so the history is preserved inline.
     */
    @Transactional
    public void changeContractAmount(Long projectId, BigDecimal newAmount, String reason) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        BigDecimal old = project.getCurrentRate();
        project.setCurrentRate(newAmount);

        String entry = "Change order " + LocalDate.now() + ": "
                + old + " → " + newAmount
                + (reason != null && !reason.isBlank() ? " — " + reason.trim() : "");
        String existing = project.getNotes();
        project.setNotes(existing == null || existing.isBlank() ? entry : existing + "\n" + entry);
    }
}

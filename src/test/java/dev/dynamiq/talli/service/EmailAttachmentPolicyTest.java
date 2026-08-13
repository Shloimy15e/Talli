package dev.dynamiq.talli.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailAttachmentPolicyTest {

    private final EmailAttachmentPolicy policy = new EmailAttachmentPolicy("20MB", "25MB");

    @Test
    void acceptsAttachmentsWithinIndividualAndCombinedLimits() {
        MultipartFile first = file("first.pdf", 12L * 1024 * 1024);
        MultipartFile second = file("second.pdf", 13L * 1024 * 1024);

        assertThat(policy.validationError(List.of(first, second))).isEmpty();
    }

    @Test
    void rejectsAttachmentAboveIndividualLimit() {
        MultipartFile upload = file("large.pdf", 20L * 1024 * 1024 + 1);

        assertThat(policy.validationError(List.of(upload)))
                .hasValueSatisfying(error -> assertThat(error).contains("large.pdf", "20 MB"));
    }

    @Test
    void rejectsAttachmentsAboveCombinedLimit() {
        MultipartFile first = file("first.pdf", 13L * 1024 * 1024);
        MultipartFile second = file("second.pdf", 13L * 1024 * 1024);

        assertThat(policy.validationError(List.of(first, second)))
                .hasValueSatisfying(error -> assertThat(error).contains("25 MB", "Remove a file"));
    }

    private MultipartFile file(String filename, long size) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getSize()).thenReturn(size);
        return file;
    }
}

package dev.dynamiq.talli.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Component
public class EmailAttachmentPolicy {

    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private final DataSize maxFileSize;
    private final DataSize maxTotalSize;

    public EmailAttachmentPolicy(
            @Value("${app.email.attachments.max-file-size:20MB}") String maxFileSize,
            @Value("${app.email.attachments.max-total-size:25MB}") String maxTotalSize) {
        this.maxFileSize = DataSize.parse(maxFileSize);
        this.maxTotalSize = DataSize.parse(maxTotalSize);
    }

    public Optional<String> validationError(List<MultipartFile> files) {
        if (files == null) return Optional.empty();

        long totalSize = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            if (file.getSize() > maxFileSize.toBytes()) {
                String filename = StringUtils.hasText(file.getOriginalFilename())
                        ? StringUtils.getFilename(file.getOriginalFilename())
                        : "This file";
                return Optional.of("\"" + filename + "\" is too large. Each attachment must be "
                        + maxFileSizeLabel() + " or smaller.");
            }

            totalSize += file.getSize();
            if (totalSize > maxTotalSize.toBytes()) {
                return Optional.of("The selected attachments exceed the " + maxTotalSizeLabel()
                        + " total limit. Remove a file or choose smaller files.");
            }
        }

        return Optional.empty();
    }

    public long maxFileBytes() {
        return maxFileSize.toBytes();
    }

    public long maxTotalBytes() {
        return maxTotalSize.toBytes();
    }

    public String limitDescription() {
        return "Up to " + maxFileSizeLabel() + " per file, " + maxTotalSizeLabel() + " total.";
    }

    public String rejectedUploadMessage() {
        return "Attachments are too large. Use files up to " + maxFileSizeLabel()
                + " each and " + maxTotalSizeLabel() + " total, then try again.";
    }

    private String maxFileSizeLabel() {
        return sizeLabel(maxFileSize);
    }

    private String maxTotalSizeLabel() {
        return sizeLabel(maxTotalSize);
    }

    private String sizeLabel(DataSize size) {
        long bytes = size.toBytes();
        return bytes % BYTES_PER_MEGABYTE == 0
                ? (bytes / BYTES_PER_MEGABYTE) + " MB"
                : bytes + " bytes";
    }
}

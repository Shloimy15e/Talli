package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.service.EmailAttachmentPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class MultipartUploadExceptionHandler {

    private final EmailAttachmentPolicy attachmentPolicy;

    public MultipartUploadExceptionHandler(EmailAttachmentPolicy attachmentPolicy) {
        this.attachmentPolicy = attachmentPolicy;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleOversizedUpload(HttpServletRequest request,
                                        RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", attachmentPolicy.rejectedUploadMessage());

        if (request.getRequestURI().startsWith(request.getContextPath() + "/emails")) {
            return "redirect:/emails";
        }
        return "redirect:/dashboard";
    }
}

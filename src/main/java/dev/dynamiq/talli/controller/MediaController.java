package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Media;
import dev.dynamiq.talli.repository.MediaRepository;
import dev.dynamiq.talli.service.MediaService;
import dev.dynamiq.talli.service.MediaStorage;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/media")
public class MediaController {

    private final MediaRepository mediaRepository;
    private final MediaStorage storage;
    private final MediaService mediaService;

    public MediaController(MediaRepository mediaRepository, MediaStorage storage, MediaService mediaService) {
        this.mediaRepository = mediaRepository;
        this.storage = storage;
        this.mediaService = mediaService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable("id") Long id) {
        Media media = mediaRepository.findById(id).orElseThrow();
        Resource body = new InputStreamResource(storage.read(media.getDiskKey()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + media.getFilename() + "\"")
                .contentLength(media.getSizeBytes())
                .body(body);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id,
                         @RequestParam(value = "return", required = false) String returnTo) {
        Media media = mediaRepository.findById(id).orElseThrow();
        mediaService.delete(media);
        return "redirect:" + (returnTo != null && returnTo.startsWith("/") ? returnTo : "/dashboard");
    }
}

package dev.dynamiq.talli.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import dev.dynamiq.talli.model.HasMedia;
import dev.dynamiq.talli.model.Media;
import dev.dynamiq.talli.repository.MediaRepository;

@Service
public class MediaService {

    private final MediaRepository mediaRepository;
    private final MediaStorage storage;

    public MediaService(MediaRepository mediaRepository, MediaStorage storage) {
        this.mediaRepository = mediaRepository;
        this.storage = storage;
    }

    @Transactional
    public Media attach(HasMedia owner, MultipartFile file, String collection) {
        Media media = new Media();
        media.setOwnerType(owner.mediaOwnerType());
        media.setOwnerId(owner.getId());
        media.setCollectionName(collection != null ? collection : "default");
        media.setFilename(file.getOriginalFilename());
        media.setMimeType(file.getContentType());
        media.setSizeBytes(file.getSize());

        // First save to get an auto-generated id we can use in the disk key.
        // disk_key is nullable — gets populated below before storage.store runs,
        // and the UPDATE flushes on @Transactional commit via JPA dirty checking.
        media = mediaRepository.save(media);

        String diskKey = media.getId() + "/" + file.getOriginalFilename();
        media.setDiskKey(diskKey);

        try {
            storage.store(diskKey, file.getInputStream(), file.getSize());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }

        return media;
    }

    /**
     * Attach raw bytes (e.g. a generated PDF) — mirrors {@link #attach} but skips MultipartFile.
     */
    @Transactional
    public Media attachBytes(HasMedia owner, byte[] bytes, String filename, String mimeType, String collection) {
        Media media = new Media();
        media.setOwnerType(owner.mediaOwnerType());
        media.setOwnerId(owner.getId());
        media.setCollectionName(collection != null ? collection : "default");
        media.setFilename(filename);
        media.setMimeType(mimeType);
        media.setSizeBytes((long) bytes.length);

        media = mediaRepository.save(media);

        String diskKey = media.getId() + "/" + filename;
        media.setDiskKey(diskKey);

        storage.store(diskKey, new ByteArrayInputStream(bytes), bytes.length);

        return media;
    }

    public List<Media> forOwner(HasMedia owner) {
        return mediaRepository.findByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(owner.mediaOwnerType(), owner.getId());
    }

    public List<Media> forOwner(HasMedia owner, String collection) {
        return mediaRepository.findByOwnerTypeAndOwnerIdAndCollectionNameOrderByCreatedAtDesc(owner.mediaOwnerType(),
                owner.getId(), collection);
    }

    public void delete(Media media) {
        String diskKey = media.getDiskKey();
        mediaRepository.delete(media);
        if (storage.exists(diskKey)) {
            storage.delete(diskKey);
        }
    }

    public void deleteAll(HasMedia owner) {
        List<Media> medias = mediaRepository.findByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(owner.mediaOwnerType(),
                owner.getId());
        medias.forEach(this::delete);
    }

}

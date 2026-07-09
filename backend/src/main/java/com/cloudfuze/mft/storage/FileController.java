package com.cloudfuze.mft.storage;

import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Upload a file into the object store (the ingress behind "Ad-hoc Send"). The file is staged to a
 * temp file, its SHA-256 is recorded, and the returned key can then be pushed to a partner.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final StorageService storage;
    private final AuditService audit;

    public FileController(StorageService storage, AuditService audit) {
        this.storage = storage;
        this.audit = audit;
    }

    public record UploadResult(String key, String sha256, long size, String filename) {
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public UploadResult upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        String filename = file.getOriginalFilename();
        Path stage;
        try {
            stage = Files.createTempFile("mft-upload-", ".stage");
            file.transferTo(stage);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to stage upload");
        }
        try {
            StorageService.StoredObject stored = storage.putFile(stage, filename, file.getContentType());
            audit.record("file.uploaded", "object", stored.key(),
                    Map.of("filename", filename == null ? "" : filename,
                            "size", stored.size(), "sha256", stored.sha256()));
            return new UploadResult(stored.key(), stored.sha256(), stored.size(), filename);
        } finally {
            try {
                Files.deleteIfExists(stage);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }
}

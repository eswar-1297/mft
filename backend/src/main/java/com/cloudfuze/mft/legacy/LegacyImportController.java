package com.cloudfuze.mft.legacy;

import com.cloudfuze.mft.common.ApiException;
import com.cloudfuze.mft.legacy.dto.ImportSummary;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/legacy-import")
public class LegacyImportController {

    private final LegacyImportService importService;

    public LegacyImportController(LegacyImportService importService) {
        this.importService = importService;
    }

    /** Upload a legacy config export (MOVEit/GoAnywhere XML) and convert its jobs to workflows. */
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ImportSummary importConfig(@RequestParam("file") MultipartFile file,
                                      @RequestParam("source") String source) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        try {
            return importService.importConfig(source, file.getBytes());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read upload");
        }
    }
}

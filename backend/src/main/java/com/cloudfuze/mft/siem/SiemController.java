package com.cloudfuze.mft.siem;

import com.cloudfuze.mft.siem.dto.SiemConfigRequest;
import com.cloudfuze.mft.siem.dto.SiemConfigView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/siem")
public class SiemController {

    private final SiemService siemService;

    public SiemController(SiemService siemService) {
        this.siemService = siemService;
    }

    /** Current SIEM config + live forwarding status (cursor, last status/time). */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','AUDITOR')")
    public SiemConfigView get() {
        return SiemConfigView.of(siemService.current());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SiemConfigView configure(@RequestBody SiemConfigRequest req) {
        return SiemConfigView.of(siemService.configure(req));
    }
}

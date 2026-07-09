package com.cloudfuze.mft.connector.cloud;

import com.cloudfuze.mft.connector.cloud.dto.ConnectorRequest;
import com.cloudfuze.mft.connector.cloud.dto.ConnectorView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/connectors")
public class ConnectorController {

    private final ConnectorService connectorService;

    public ConnectorController(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','AUDITOR')")
    public List<ConnectorView> list() {
        return connectorService.list().stream().map(ConnectorView::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<ConnectorView> create(@Valid @RequestBody ConnectorRequest req) {
        CloudConnector c = connectorService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ConnectorView.of(c));
    }

    /** Round-trip a probe object to confirm the connector's credentials and reachability. */
    @PostMapping("/{id}/test")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public Map<String, Object> test(@PathVariable UUID id) {
        connectorService.test(id);
        return Map.of("ok", true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        connectorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

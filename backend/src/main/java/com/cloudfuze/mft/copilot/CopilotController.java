package com.cloudfuze.mft.copilot;

import com.cloudfuze.mft.copilot.dto.AskRequest;
import com.cloudfuze.mft.copilot.dto.AskResponse;
import com.cloudfuze.mft.copilot.dto.DraftWorkflowRequest;
import com.cloudfuze.mft.copilot.dto.DraftWorkflowResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/copilot")
public class CopilotController {

    private final CopilotService copilot;
    private final LlmClient llm;

    public CopilotController(CopilotService copilot, LlmClient llm) {
        this.copilot = copilot;
        this.llm = llm;
    }

    /** Whether Claude is wired (the UI shows an "AI-assisted" badge; the engine works either way). */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','AUDITOR')")
    public Map<String, Object> status() {
        return Map.of("aiAvailable", llm.isAvailable());
    }

    /** Draft a workflow from natural language. Returns a proposal to review — nothing is created. */
    @PostMapping("/draft-workflow")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public DraftWorkflowResponse draftWorkflow(@Valid @RequestBody DraftWorkflowRequest req) {
        return copilot.draftWorkflow(req.prompt());
    }

    /** Answer a plain-English question about the audit trail, with cited evidence. */
    @PostMapping("/ask")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','AUDITOR')")
    public AskResponse ask(@Valid @RequestBody AskRequest req) {
        return copilot.askAudit(req.question());
    }
}

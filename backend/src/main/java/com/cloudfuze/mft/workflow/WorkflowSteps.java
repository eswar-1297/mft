package com.cloudfuze.mft.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/** Serializes the ordered step list to/from the JSON stored on a {@link WorkflowDef}. */
public final class WorkflowSteps {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WorkflowSteps() {
    }

    public static String toJson(List<WorkflowStep> steps) {
        try {
            return MAPPER.writeValueAsString(steps);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workflow steps", e);
        }
    }

    public static List<WorkflowStep> fromJson(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<List<WorkflowStep>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Corrupt workflow step definition", e);
        }
    }
}

package com.cloudfuze.mft.workflow;

import java.util.Map;

/**
 * One configured step in a pipeline. {@code config} holds step-specific settings, e.g.:
 * <ul>
 *   <li>PICKUP: {@code source} = "STORAGE" (with {@code storageKey}) or "SFTP" (with
 *       {@code partnerId}, {@code remotePath})</li>
 *   <li>PGP_ENCRYPT / PGP_DECRYPT: {@code passphrase}</li>
 *   <li>VALIDATE: {@code minBytes}, {@code maxBytes}</li>
 *   <li>SEND: {@code dest} = "SFTP" (with {@code partnerId}, {@code remotePath}) or "STORAGE"</li>
 *   <li>ARCHIVE: (none)</li>
 *   <li>NOTIFY: {@code message}</li>
 * </ul>
 * Serialized to JSON inside the workflow definition.
 */
public record WorkflowStep(StepType type, Map<String, String> config) {

    public String cfg(String key) {
        return config == null ? null : config.get(key);
    }

    public String cfg(String key, String defaultValue) {
        String v = cfg(key);
        return v != null ? v : defaultValue;
    }
}

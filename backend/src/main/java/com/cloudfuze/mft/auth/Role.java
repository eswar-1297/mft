package com.cloudfuze.mft.auth;

/**
 * Coarse role-based access control. Fine-grained permissions can be layered later,
 * but these five cover the MFT operating model:
 *
 * <ul>
 *   <li>OWNER    — full control incl. billing and user management</li>
 *   <li>ADMIN    — manage workflows, partners, connectors, users (no billing)</li>
 *   <li>OPERATOR — run/retry transfers, manage day-to-day operations</li>
 *   <li>AUDITOR  — read-only access to audit log and compliance reports</li>
 *   <li>PARTNER  — external partner limited to their own portal and history</li>
 * </ul>
 */
public enum Role {
    OWNER,
    ADMIN,
    OPERATOR,
    AUDITOR,
    PARTNER;

    /** Spring Security authority name, e.g. {@code ROLE_ADMIN}. */
    public String authority() {
        return "ROLE_" + name();
    }
}

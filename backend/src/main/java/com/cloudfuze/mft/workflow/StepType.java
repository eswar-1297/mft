package com.cloudfuze.mft.workflow;

/**
 * The kinds of steps a transfer pipeline can contain. Each maps to a real operation in
 * {@code PipelineActivitiesImpl}. The canonical order in a typical flow is:
 * PICKUP → PGP_ENCRYPT → VALIDATE → SEND → ARCHIVE → NOTIFY.
 */
public enum StepType {
    /** Bring a file in: from an uploaded object-store key, or an SFTP pull from a partner. */
    PICKUP,
    /** Encrypt the current artifact with OpenPGP (passphrase-based). */
    PGP_ENCRYPT,
    /** Decrypt a PGP-encrypted artifact. */
    PGP_DECRYPT,
    /** Assert constraints on the artifact (min/max size, non-empty). */
    VALIDATE,
    /** Deliver the artifact out: SFTP push to a partner, or leave in the object store. */
    SEND,
    /** Copy the current artifact to the tenant's archive namespace. */
    ARCHIVE,
    /** Record an in-app notification + audit event. */
    NOTIFY
}

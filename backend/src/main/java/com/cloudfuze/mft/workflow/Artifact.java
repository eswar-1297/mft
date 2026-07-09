package com.cloudfuze.mft.workflow;

/**
 * The file flowing through a pipeline, identified by its object-store key. Steps read the current
 * artifact from storage, optionally produce a new one, and pass the reference to the next step.
 * Passing a key (not bytes) keeps large files out of the Temporal payload.
 *
 * @param key      object-store key, or null before PICKUP / after a terminal SEND-to-partner
 * @param filename logical filename carried through the pipeline
 * @param sha256   hash of the current artifact, or null if not in storage
 * @param bytes    size of the current artifact
 */
public record Artifact(String key, String filename, String sha256, long bytes) {

    public static Artifact none() {
        return new Artifact(null, "file", null, 0);
    }
}

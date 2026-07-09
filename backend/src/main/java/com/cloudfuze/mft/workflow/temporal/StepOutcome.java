package com.cloudfuze.mft.workflow.temporal;

import com.cloudfuze.mft.workflow.Artifact;

/**
 * Result of executing one step: the (possibly new) artifact to feed the next step, plus a
 * human-readable detail line for the run history/UI.
 */
public record StepOutcome(Artifact artifact, String detail) {
}

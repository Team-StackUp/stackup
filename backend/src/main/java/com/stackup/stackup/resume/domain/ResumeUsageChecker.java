package com.stackup.stackup.resume.domain;

public interface ResumeUsageChecker {

    /**
     * Returns true when the resume's analyzed document is referenced by an active
     * (READY or IN_PROGRESS) interview session.
     */
    boolean isInUse(Long resumeId);
}

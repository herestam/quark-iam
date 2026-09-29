package com.example.emailapp.campaign.entity;

/** Lifecycle of a campaign. Kept separate from {@code JobStatus} on purpose. */
public enum CampaignStatus {
    DRAFT,
    READY,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}

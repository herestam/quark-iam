package com.example.emailapp.campaign.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import com.example.emailapp.common.entity.AuditableEntity;
import com.example.emailapp.recipient.entity.Recipient;
import com.example.emailapp.template.entity.EmailTemplate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A named send: one template, one subject, and the set of recipients to target. */
@Entity
@Table(name = "campaign")
public class Campaign extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 200)
    public String name;

    @Column(name = "subject", nullable = false, length = 500)
    public String subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, foreignKey = @ForeignKey(name = "fk_campaign_template"))
    public EmailTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public CampaignStatus status = CampaignStatus.DRAFT;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "campaign_recipient",
            joinColumns = @JoinColumn(
                    name = "campaign_id",
                    foreignKey = @ForeignKey(name = "fk_campaign_recipient_campaign")),
            inverseJoinColumns = @JoinColumn(
                    name = "recipient_id",
                    foreignKey = @ForeignKey(name = "fk_campaign_recipient_recipient")))
    public Set<Recipient> recipients = new LinkedHashSet<>();

    @Override
    public String toString() {
        return "Campaign{id=" + id + ", name='" + name + "', status=" + status + "}";
    }
}

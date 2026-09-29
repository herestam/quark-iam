package com.example.emailapp.recipient.entity;

import com.example.emailapp.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A person or mailbox a campaign can be sent to. Addresses are unique. */
@Entity
@Table(name = "recipient")
public class Recipient extends AuditableEntity {

    @Column(name = "email", nullable = false, length = 320, unique = true)
    public String email;

    @Column(name = "name", length = 200)
    public String name;

    @Column(name = "company", length = 200)
    public String company;

    @Override
    public String toString() {
        return "Recipient{id=" + id + ", email='" + email + "'}";
    }
}

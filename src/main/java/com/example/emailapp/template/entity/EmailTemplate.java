package com.example.emailapp.template.entity;

import com.example.emailapp.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A reusable email body. Content is authored by an administrator and may use
 * the simple {@code {{variable}}} placeholders understood by
 * {@code TemplateRenderService}.
 */
@Entity
@Table(name = "email_template")
public class EmailTemplate extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 200)
    public String name;

    @Column(name = "subject", nullable = false, length = 500)
    public String subject;

    @Column(name = "html_content", nullable = false, length = 32000)
    public String htmlContent;

    @Column(name = "text_content", length = 32000)
    public String textContent;

    @Override
    public String toString() {
        return "EmailTemplate{id=" + id + ", name='" + name + "'}";
    }
}

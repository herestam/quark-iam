package com.example.emailapp.email.entity;

/** Supported transport. Both speak SMTP; only the default settings differ. */
public enum EmailProvider {
    /** Generic SMTP server. */
    SMTP,
    /** SendGrid's SMTP relay (smtp.sendgrid.net:587, user "apikey"). */
    SENDGRID
}

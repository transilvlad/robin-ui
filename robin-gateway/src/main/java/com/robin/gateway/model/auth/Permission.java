package com.robin.gateway.model.auth;

/**
 * Fine-grained permissions for domain management operations.
 * These are stored as plain strings in the user_permissions table
 * and included in JWT claims under the "permissions" key.
 *
 * Route guards on the frontend mirror these values.
 */
public enum Permission {

    // Domain visibility and basic management
    VIEW_DOMAINS,
    MANAGE_DOMAINS,

    // DNS record CRUD on a domain
    MANAGE_DNS_RECORDS,

    // DNS provider profile management (credentials, test-connection)
    MANAGE_DNS_PROVIDERS,

    // DKIM key generation, rotation, and retirement
    MANAGE_DKIM
}

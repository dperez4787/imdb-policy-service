package org.perez_f_daniel.imdb.policy.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * The access decision for one governed field: which roles may read it.
 * Written by the admin API. A governed field with no policy (or a disabled
 * one) denies everyone — fields are born locked.
 */
@Document("policies")
public class FieldPolicy {

    @Id
    private String coordinate;
    private List<String> allowedRoles;
    private boolean enabled = true;
    private String updatedBy;
    private Instant updatedAt;

    public FieldPolicy() {
    }

    public FieldPolicy(String coordinate, List<String> allowedRoles, boolean enabled,
                       String updatedBy, Instant updatedAt) {
        this.coordinate = coordinate;
        this.allowedRoles = allowedRoles;
        this.enabled = enabled;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public String getCoordinate() { return coordinate; }
    public void setCoordinate(String coordinate) { this.coordinate = coordinate; }
    public List<String> getAllowedRoles() { return allowedRoles; }
    public void setAllowedRoles(List<String> allowedRoles) { this.allowedRoles = allowedRoles; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

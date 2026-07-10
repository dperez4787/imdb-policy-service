package org.perez_f_daniel.imdb.policy.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A field coordinate a subgraph has declared sensitive via {@code @governed}.
 * Written only by subgraph registration (or the demo seed) — never by the
 * admin UI. The coordinate ("Type.field") is the natural key across the whole
 * federated graph, so it is the Mongo _id.
 */
@Document("governed_fields")
public class GovernedField {

    public enum Status { ACTIVE, ORPHANED }

    @Id
    private String coordinate;
    private String subgraph;
    private String reason;
    private Status status;
    /** "registration" for directive-sourced entries, "seed" for demo fixtures. */
    private String source;
    private Instant firstSeenAt;
    private Instant lastRegisteredAt;

    public GovernedField() {
    }

    public GovernedField(String coordinate, String subgraph, String reason,
                         Status status, String source, Instant firstSeenAt, Instant lastRegisteredAt) {
        this.coordinate = coordinate;
        this.subgraph = subgraph;
        this.reason = reason;
        this.status = status;
        this.source = source;
        this.firstSeenAt = firstSeenAt;
        this.lastRegisteredAt = lastRegisteredAt;
    }

    public String getCoordinate() { return coordinate; }
    public void setCoordinate(String coordinate) { this.coordinate = coordinate; }
    public String getSubgraph() { return subgraph; }
    public void setSubgraph(String subgraph) { this.subgraph = subgraph; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public Instant getLastRegisteredAt() { return lastRegisteredAt; }
    public void setLastRegisteredAt(Instant lastRegisteredAt) { this.lastRegisteredAt = lastRegisteredAt; }
}

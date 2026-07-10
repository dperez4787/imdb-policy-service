package org.perez_f_daniel.imdb.policy.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Append-only record of every change that can alter the policy bundle. */
@Document("audit_log")
public class AuditEntry {

    @Id
    private String id;
    private Instant at;
    private String actor;
    /** e.g. "policy.updated", "registration.applied", "persona.updated", "seed.applied" */
    private String action;
    private String target;
    private String detail;
    private long revision;

    public AuditEntry() {
    }

    public AuditEntry(Instant at, String actor, String action, String target, String detail, long revision) {
        this.at = at;
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.detail = detail;
        this.revision = revision;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
}

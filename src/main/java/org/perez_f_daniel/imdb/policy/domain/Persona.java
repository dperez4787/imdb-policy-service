package org.perez_f_daniel.imdb.policy.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * A demo identity with a role set. Two ways a request maps to a persona:
 * the persona token endpoint mints a JWT carrying the roles claim directly,
 * or — for Google/Firebase-signed tokens the router already trusts — the
 * subjects list maps the token's email/sub onto this persona via the
 * bundle's principals map.
 */
@Document("personas")
public class Persona {

    @Id
    private String id;
    private String displayName;
    private List<String> roles;
    /** Google identity subjects (emails or OIDC subs) that resolve to this persona. */
    private List<String> subjects;

    public Persona() {
    }

    public Persona(String id, String displayName, List<String> roles, List<String> subjects) {
        this.id = id;
        this.displayName = displayName;
        this.roles = roles;
        this.subjects = subjects;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public List<String> getSubjects() { return subjects; }
    public void setSubjects(List<String> subjects) { this.subjects = subjects; }
}

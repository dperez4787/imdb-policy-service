package org.perez_f_daniel.imdb.policy.bundle;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The compiled artifact the router's fieldauth module consumes. This shape is
 * the contract between the governance plane and the enforcement point — keep
 * it backward-compatible (additive changes only).
 *
 * <ul>
 *   <li>{@code fields}: every ACTIVE governed coordinate. An empty allowedRoles
 *       list means deny-everyone. Ungoverned fields never appear here — the
 *       posture is allow-unless-governed.</li>
 *   <li>{@code principals}: Google identity subject (email or OIDC sub) →
 *       roles, for tokens that carry no roles claim of their own. Tokens
 *       minted by this service carry a roles claim directly and skip this map.</li>
 * </ul>
 */
public record PolicyBundle(
        long revision,
        Instant generatedAt,
        String defaultPosture,
        Map<String, FieldEntry> fields,
        Map<String, List<String>> principals) {

    public record FieldEntry(List<String> allowedRoles, String subgraph) {
    }
}

package org.perez_f_daniel.imdb.policy.bundle;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.perez_f_daniel.imdb.policy.core.GoogleIdentityVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class BundleController {

    private static final Logger log = LoggerFactory.getLogger(BundleController.class);

    private final BundleService bundles;
    private final GoogleIdentityVerifier verifier;
    private final Set<String> principalsEmails;

    public BundleController(BundleService bundles, GoogleIdentityVerifier verifier,
                            @Value("${policy.bundle.principals-emails:}") String principalsEmailsCsv) {
        this.bundles = bundles;
        this.verifier = verifier;
        this.principalsEmails = Arrays.stream(principalsEmailsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @PostConstruct
    void warnIfOpen() {
        if (principalsEmails.isEmpty()) {
            log.warn("POLICY_BUNDLE_PRINCIPALS_EMAILS not set: the principals map "
                    + "(real emails) is served to ANONYMOUS bundle readers");
        }
    }

    /**
     * The router polls this with If-None-Match. The ETag is the bundle
     * revision (suffixed for the principals variant so the two shapes never
     * share a cache entry), so unchanged policy costs a 304 and no body.
     *
     * The principals map (identity → roles, real emails) is included only
     * for callers whose Google ID token verifies against the allowlist —
     * in production, the router's runtime SA. An empty allowlist serves it
     * to everyone (local dev; warned at startup).
     */
    @GetMapping("/v1/bundle")
    public ResponseEntity<PolicyBundle> bundle(
            HttpServletRequest request,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        boolean includePrincipals = principalsEmails.isEmpty()
                || verifier.verifiedEmail(request).map(principalsEmails::contains).orElse(false);
        PolicyBundle bundle = bundles.build(includePrincipals);
        String etag = "\"" + bundle.revision() + (includePrincipals ? "+p" : "") + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.noCache())
                .body(bundle);
    }
}

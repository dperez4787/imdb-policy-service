package org.perez_f_daniel.imdb.policy.bundle;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BundleController {

    private final BundleService bundles;

    public BundleController(BundleService bundles) {
        this.bundles = bundles;
    }

    /**
     * The router polls this with If-None-Match. The ETag is the bundle
     * revision, so unchanged policy costs a 304 and no body.
     */
    @GetMapping("/v1/bundle")
    public ResponseEntity<PolicyBundle> bundle(
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        PolicyBundle bundle = bundles.build();
        String etag = "\"" + bundle.revision() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.noCache())
                .body(bundle);
    }
}

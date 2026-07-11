package org.perez_f_daniel.imdb.policy.ui;

import org.perez_f_daniel.imdb.policy.domain.PersonaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public persona listing for the UI playground: ids, names, and roles only —
 * never the subjects mapping (that's admin-plane data). Minting a token for
 * any of these personas is already public via POST /v1/token.
 */
@RestController
public class PublicPersonasController {

    private final PersonaRepository personas;

    public PublicPersonasController(PersonaRepository personas) {
        this.personas = personas;
    }

    public record PublicPersona(String id, String displayName, List<String> roles) {
    }

    @GetMapping("/v1/personas")
    public List<PublicPersona> list() {
        return personas.findAll().stream()
                .map(p -> new PublicPersona(p.getId(), p.getDisplayName(), p.getRoles()))
                .toList();
    }
}

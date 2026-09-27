package com.vijaypurohit.movietickets.shared.openapi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import tools.jackson.databind.ObjectMapper;

/**
 * Publishes the checked-in OpenAPI document.
 *
 * <p>The specification is the source of truth for this API: the request and response models
 * and the controller interfaces are generated from it at build time. The document is
 * therefore served verbatim rather than reconstructed from the running code, so there is
 * exactly one definition of the contract.
 *
 * <p>Parsing it at startup also fails fast: a specification that is not valid YAML stops the
 * application rather than surfacing later as a broken documentation page.
 */
@RestController
public class OpenApiDocumentController {

    static final String SPECIFICATION = "openapi/openapi.yaml";

    private final String yaml;
    private final String json;

    public OpenApiDocumentController() {
        Resource resource = new ClassPathResource(SPECIFICATION);
        try (InputStream stream = resource.getInputStream()) {
            this.yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "OpenAPI specification " + SPECIFICATION + " is missing", exception);
        }
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(Integer.MAX_VALUE);
        Object document = new Yaml(options).load(this.yaml);
        if (document == null) {
            throw new IllegalStateException("OpenAPI specification " + SPECIFICATION + " is empty");
        }
        this.json = new ObjectMapper().writeValueAsString(document);
    }

    @GetMapping(value = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    public String document() {
        return json;
    }

    @GetMapping(value = "/v3/api-docs.yaml", produces = "application/vnd.oai.openapi;charset=UTF-8")
    public String documentAsYaml() {
        return yaml;
    }
}

package com.auraops.analyzer.domain.model;

public record Resource(
    String kind,
    String name,
    String namespace
) {
    public Resource {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("resource kind cannot be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("resource name cannot be blank");
        }
        namespace = namespace == null ? "" : namespace;
    }
}

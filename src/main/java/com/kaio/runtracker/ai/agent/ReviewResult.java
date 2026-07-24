package com.kaio.runtracker.ai.agent;

import java.util.List;

public record ReviewResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        String summary) {

    public ReviewResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        summary = summary == null ? "" : summary;
    }

    public static ReviewResult approved() {
        return new ReviewResult(true, List.of(), List.of(), "Plano aprovado.");
    }
}

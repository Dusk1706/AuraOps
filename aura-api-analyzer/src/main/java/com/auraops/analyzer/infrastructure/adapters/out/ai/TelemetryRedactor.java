package com.auraops.analyzer.infrastructure.adapters.out.ai;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;
import java.util.List;

@Component
public class TelemetryRedactor {

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
        Pattern.compile("(?i)(password|passwd|pwd|api_key|apikey|secret|token|credential|access_key|private_key)(\"\\s*:\\s*\"|\\s*[:=]\\s*)([^\\s\"',;]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(Authorization|Bearer)\\s+([^\\s\"',;]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(https?://)([^:]+):([^@]+)@", Pattern.CASE_INSENSITIVE) // URL auth
    );

    public String redact(String input) {
        if (input == null) return null;
        String redacted = input;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            redacted = pattern.matcher(redacted).replaceAll("$1$2[REDACTED]");
        }
        return redacted;
    }

    public List<String> redact(List<String> inputs) {
        if (inputs == null) return List.of();
        return inputs.stream().map(this::redact).toList();
    }
}

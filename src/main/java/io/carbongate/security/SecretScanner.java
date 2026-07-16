package io.carbongate.security;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SecretScanner {
    private static final List<SecretPattern> PATTERNS = List.of(
            new SecretPattern("PRIVATE_KEY", Pattern.compile("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----[\\s\\S]*?-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
            new SecretPattern("JWT", Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b")),
            new SecretPattern("AWS_ACCESS_KEY", Pattern.compile("\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b")),
            new SecretPattern("GITHUB_TOKEN", Pattern.compile("\\bgh[opusr]_[A-Za-z0-9_]{20,}\\b")),
            new SecretPattern("OPENAI_API_KEY", Pattern.compile("\\bsk-[A-Za-z0-9_-]{20,}\\b")),
            new SecretPattern("BEARER_TOKEN", Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[A-Za-z0-9._~+/-]{12,}")),
            new SecretPattern("ASSIGNED_SECRET", Pattern.compile("(?i)(password|passwd|secret|api[_-]?key|token)\\s*[:=]\\s*['\"]?([^\\s'\",;]{8,})"))
    );

    public ScanResult scan(String input) {
        if (input == null || input.isEmpty()) return new ScanResult("", List.of());
        String redacted = input;
        List<String> findings = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger();
        for (SecretPattern secretPattern : PATTERNS) {
            Matcher matcher = secretPattern.pattern.matcher(redacted);
            StringBuffer output = new StringBuffer();
            boolean found = false;
            while (matcher.find()) {
                found = true;
                String placeholder = "<SECRET:" + secretPattern.name + ":" + counter.incrementAndGet() + ">";
                matcher.appendReplacement(output, Matcher.quoteReplacement(placeholder));
            }
            matcher.appendTail(output);
            if (found) findings.add(secretPattern.name);
            redacted = output.toString();
        }
        return new ScanResult(redacted, findings);
    }

    public record ScanResult(String redacted, List<String> findings) {
        public ScanResult {
            findings = List.copyOf(findings);
        }

        public boolean containsSecrets() {
            return !findings.isEmpty();
        }
    }

    private record SecretPattern(String name, Pattern pattern) {}
}

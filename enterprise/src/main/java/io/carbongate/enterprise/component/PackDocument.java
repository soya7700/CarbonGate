package io.carbongate.enterprise.component;

import io.carbongate.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Validated, data-only rule document carried by a Pack component. */
public record PackDocument(String apiVersion, List<Rule> rules) {
    public static final String API_VERSION = "carbongate.pack/v1";
    private static final int MAX_BYTES = 1_048_576;
    private static final int MAX_RULES = 256;

    public PackDocument {
        if (!API_VERSION.equals(apiVersion)) throw new IllegalArgumentException("Unsupported Pack apiVersion");
        rules = rules == null ? List.of() : List.copyOf(rules);
        if (rules.size() > MAX_RULES) throw new IllegalArgumentException("Pack exceeds 256 rules");
        Set<String> ids = new HashSet<>();
        for (Rule rule : rules) {
            if (!ids.add(rule.id())) throw new IllegalArgumentException("Pack contains duplicate rule id: " + rule.id());
        }
    }

    public static PackDocument read(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Pack requires payload/pack.json");
        if (Files.size(path) > MAX_BYTES) throw new IllegalArgumentException("Pack document exceeds 1 MiB");
        return from(Json.object(Files.readString(path, StandardCharsets.UTF_8)));
    }

    public static PackDocument from(Map<String, Object> root) {
        String apiVersion = text(root.get("apiVersion"), "Pack apiVersion");
        Object rawRules = root.get("rules");
        if (!(rawRules instanceof List<?> values)) throw new IllegalArgumentException("Pack rules must be an array");
        List<Rule> rules = new ArrayList<>();
        for (Object value : values) rules.add(Rule.from(object(value, "Pack rule")));
        return new PackDocument(apiVersion, rules);
    }

    public record Rule(String id, Audience audience, String category, Severity severity,
                       Detector detector, List<String> terms) {
        private static final Set<Detector> FIXED = Set.of(Detector.EMAIL, Detector.PHONE_CN,
                Detector.ID_CN, Detector.BANK_CARD, Detector.API_SECRET);

        public Rule {
            if (id == null || !id.matches("[a-z][a-z0-9.-]{2,95}")) {
                throw new IllegalArgumentException("Pack rule id is invalid");
            }
            if (audience == null || severity == null || detector == null) {
                throw new IllegalArgumentException("Pack rule audience, severity, and detector are required");
            }
            if (category == null || !category.matches("[a-z][a-z0-9._-]{2,95}")) {
                throw new IllegalArgumentException("Pack rule category is invalid");
            }
            terms = terms == null ? List.of() : List.copyOf(terms);
            if (detector == Detector.KEYWORDS) {
                if (terms.isEmpty() || terms.size() > 64) {
                    throw new IllegalArgumentException("Keyword rules require 1 to 64 terms");
                }
                Set<String> unique = new HashSet<>();
                for (String term : terms) {
                    if (term == null || term.isBlank() || term.length() > 128 || !unique.add(term)) {
                        throw new IllegalArgumentException("Pack keyword term is invalid or duplicated");
                    }
                }
            } else if (FIXED.contains(detector) && !terms.isEmpty()) {
                throw new IllegalArgumentException("Fixed detectors cannot declare terms");
            }
        }

        private static Rule from(Map<String, Object> value) {
            Map<String, Object> match = object(value.get("match"), "Pack rule match");
            Detector detector = Detector.parse(text(match.get("type"), "Pack detector type"));
            return new Rule(text(value.get("id"), "Pack rule id"),
                    Audience.parse(text(value.get("audience"), "Pack rule audience")),
                    text(value.get("category"), "Pack rule category"),
                    Severity.parse(text(value.get("severity"), "Pack rule severity")), detector,
                    strings(match.get("terms")));
        }
    }

    public enum Audience {
        PERSONAL, ENTERPRISE, BOTH;

        private static Audience parse(String value) {
            return parseEnum(Audience.class, value, "Pack audience must be personal, enterprise, or both");
        }
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL;

        private static Severity parse(String value) {
            return parseEnum(Severity.class, value, "Pack severity must be low, medium, high, or critical");
        }
    }

    public enum Detector {
        KEYWORDS, EMAIL, PHONE_CN, ID_CN, BANK_CARD, API_SECRET;

        private static Detector parse(String value) {
            return parseEnum(Detector.class, value.replace('-', '_'), "Unknown Pack detector type");
        }
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, String message) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(message);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String name) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException(name + " must be an object");
    }

    private static String text(Object value, String name) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(name + " is required");
        return text.trim();
    }

    private static List<String> strings(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Pack terms must be an array");
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String text)) throw new IllegalArgumentException("Pack term must be text");
            result.add(text);
        }
        return List.copyOf(result);
    }
}

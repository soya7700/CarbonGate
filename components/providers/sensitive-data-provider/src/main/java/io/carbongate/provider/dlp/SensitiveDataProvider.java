package io.carbongate.provider.dlp;

import io.carbongate.json.Json;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Out-of-process sensitive-data detector driven only by validated active Packs. */
public final class SensitiveDataProvider {
    private static final String API_VERSION = "carbongate.provider/v1";
    private static final int MAX_TEXT_CHARS = 262_144;
    private static final Pattern EMAIL = Pattern.compile("(?i)(?<![a-z0-9._%+-])[a-z0-9._%+-]{1,64}@[a-z0-9.-]{1,190}\\.[a-z]{2,24}(?![a-z0-9._%+-])");
    private static final Pattern PHONE_CN = Pattern.compile("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CN = Pattern.compile("(?<![0-9A-Za-z])\\d{17}[0-9Xx](?![0-9A-Za-z])");
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)");
    private static final Pattern API_SECRET = Pattern.compile("(?i)(?:\\b(?:api[_-]?key|access[_-]?token|secret|token)\\b\\s*[:=]\\s*['\"]?[A-Za-z0-9_./+\\-]{8,}|\\bsk-[A-Za-z0-9_-]{12,})");
    private static final String[] ID_CHECK = {"1", "0", "X", "9", "8", "7", "6", "5", "4", "3", "2"};
    private static final int[] ID_WEIGHT = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};

    private SensitiveDataProvider() {}

    public static void main(String[] args) throws Exception {
        String line = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        Map<String, Object> request = Json.object(line == null ? "{}" : line);
        String id = text(request.get("id"), "request id");
        String operation = text(request.get("operation"), "operation");
        Map<String, Object> result = switch (operation) {
            case "health" -> Map.of("health", "pass", "provider", "sensitive-data-provider", "version", "1.0.0");
            case "inspect" -> inspect(object(request.get("payload"), "payload"));
            default -> throw new IllegalArgumentException("Unsupported operation");
        };
        System.out.println(Json.stringify(Map.of("apiVersion", API_VERSION, "id", id,
                "status", "ok", "result", result)));
    }

    static Map<String, Object> inspect(Map<String, Object> payload) {
        String text = text(payload.get("text"), "text");
        if (text.length() > MAX_TEXT_CHARS) throw new IllegalArgumentException("text exceeds 262144 characters");
        Map<String, Object> context = object(payload.get("_carbongate"), "_carbongate context");
        List<?> packs = list(context.get("activePacks"), "activePacks");
        List<Map<String, Object>> findings = new ArrayList<>();
        int decisionRank = 0;
        for (Object rawPack : packs) {
            Map<String, Object> pack = object(rawPack, "active Pack");
            Map<String, Object> document = object(pack.get("document"), "Pack document");
            for (Object rawRule : list(document.get("rules"), "Pack rules")) {
                Map<String, Object> rule = object(rawRule, "Pack rule");
                Map<String, Object> match = object(rule.get("match"), "Pack match");
                int count = count(text, text(match.get("type"), "match type"), match.get("terms"));
                if (count == 0) continue;
                String severity = text(rule.get("severity"), "severity").toLowerCase(Locale.ROOT);
                decisionRank = Math.max(decisionRank, rank(severity));
                Map<String, Object> finding = new LinkedHashMap<>();
                finding.put("ruleId", text(rule.get("id"), "rule id"));
                finding.put("category", text(rule.get("category"), "category"));
                finding.put("audience", text(rule.get("audience"), "audience"));
                finding.put("severity", severity);
                finding.put("count", count);
                findings.add(Map.copyOf(finding));
            }
        }
        return Map.of("decision", decision(decisionRank), "findingCount", findings.size(),
                "findings", List.copyOf(findings), "contentReturned", false);
    }

    private static int count(String text, String detector, Object rawTerms) {
        return switch (detector.toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "keywords" -> keywordCount(text, list(rawTerms, "keyword terms"));
            case "email" -> matches(EMAIL, text, false);
            case "phone_cn" -> matches(PHONE_CN, text, false);
            case "id_cn" -> idCount(text);
            case "bank_card" -> matches(BANK_CARD, text, true);
            case "api_secret" -> matches(API_SECRET, text, false);
            default -> throw new IllegalArgumentException("Unsupported Pack detector");
        };
    }

    private static int keywordCount(String value, List<?> terms) {
        String lower = value.toLowerCase(Locale.ROOT);
        int count = 0;
        for (Object raw : terms) {
            String term = text(raw, "keyword").toLowerCase(Locale.ROOT);
            int from = 0;
            while ((from = lower.indexOf(term, from)) >= 0) {
                count++;
                from += Math.max(1, term.length());
            }
        }
        return count;
    }

    private static int matches(Pattern pattern, String value, boolean luhn) {
        int count = 0;
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            if (!luhn || validLuhn(matcher.group())) count++;
        }
        return count;
    }

    private static int idCount(String value) {
        int count = 0;
        Matcher matcher = ID_CN.matcher(value);
        while (matcher.find()) if (validChineseId(matcher.group())) count++;
        return count;
    }

    private static boolean validChineseId(String value) {
        int sum = 0;
        for (int i = 0; i < 17; i++) sum += (value.charAt(i) - '0') * ID_WEIGHT[i];
        return ID_CHECK[sum % 11].equals(String.valueOf(Character.toUpperCase(value.charAt(17))));
    }

    private static boolean validLuhn(String raw) {
        String digits = raw.replace(" ", "").replace("-", "");
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleDigit && (digit *= 2) > 9) digit -= 9;
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return digits.length() >= 13 && digits.length() <= 19 && sum % 10 == 0;
    }

    private static int rank(String severity) {
        return switch (severity) {
            case "critical" -> 3;
            case "high" -> 2;
            case "medium" -> 1;
            default -> 0;
        };
    }

    private static String decision(int rank) {
        return switch (rank) {
            case 3 -> "block";
            case 2 -> "review";
            case 1 -> "warn";
            default -> "allow";
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String name) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException(name + " must be an object");
    }

    private static List<?> list(Object value, String name) {
        if (value instanceof List<?> list) return list;
        throw new IllegalArgumentException(name + " must be an array");
    }

    private static String text(Object value, String name) {
        if (value instanceof String text && !text.isBlank()) return text;
        throw new IllegalArgumentException(name + " is required");
    }
}

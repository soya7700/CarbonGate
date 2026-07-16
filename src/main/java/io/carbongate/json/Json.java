package io.carbongate.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON codec used to keep the security core dependency-free. */
public final class Json {
    private Json() {}

    public static Object parse(String input) {
        Parser parser = new Parser(input);
        Object value = parser.value();
        parser.whitespace();
        if (!parser.done()) throw parser.error("Unexpected trailing content");
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String input) {
        Object value = parse(input);
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("JSON value must be an object");
        return (Map<String, Object>) value;
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            quote(text, out);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                quote(String.valueOf(entry.getKey()), out);
                out.append(':');
                write(entry.getValue(), out);
                first = false;
            }
            out.append('}');
        } else if (value instanceof Iterable<?> values) {
            out.append('[');
            boolean first = true;
            for (Object item : values) {
                if (!first) out.append(',');
                write(item, out);
                first = false;
            }
            out.append(']');
        } else {
            quote(String.valueOf(value), out);
        }
    }

    private static void quote(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) {
            this.input = input == null ? "" : input;
        }

        private Object value() {
            whitespace();
            if (done()) throw error("Expected JSON value");
            return switch (input.charAt(position)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (take('}')) return result;
            while (true) {
                whitespace();
                String key = string();
                whitespace();
                expect(':');
                result.put(key, value());
                whitespace();
                if (take('}')) return result;
                expect(',');
            }
        }

        private List<Object> array() {
            expect('[');
            List<Object> result = new ArrayList<>();
            whitespace();
            if (take(']')) return result;
            while (true) {
                result.add(value());
                whitespace();
                if (take(']')) return result;
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (!done()) {
                char c = input.charAt(position++);
                if (c == '"') return result.toString();
                if (c != '\\') {
                    result.append(c);
                    continue;
                }
                if (done()) throw error("Unterminated escape");
                char escaped = input.charAt(position++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        if (position + 4 > input.length()) throw error("Invalid unicode escape");
                        result.append((char) Integer.parseInt(input.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default -> throw error("Invalid escape");
                }
            }
            throw error("Unterminated string");
        }

        private Object number() {
            int start = position;
            if (take('-')) {}
            while (!done() && Character.isDigit(input.charAt(position))) position++;
            if (!done() && input.charAt(position) == '.') {
                position++;
                while (!done() && Character.isDigit(input.charAt(position))) position++;
            }
            if (!done() && (input.charAt(position) == 'e' || input.charAt(position) == 'E')) {
                position++;
                if (!done() && (input.charAt(position) == '+' || input.charAt(position) == '-')) position++;
                while (!done() && Character.isDigit(input.charAt(position))) position++;
            }
            if (start == position) throw error("Expected JSON value");
            String text = input.substring(start, position);
            try {
                return text.contains(".") || text.contains("e") || text.contains("E")
                        ? Double.parseDouble(text) : Long.parseLong(text);
            } catch (NumberFormatException e) {
                throw error("Invalid number");
            }
        }

        private Object literal(String literal, Object value) {
            if (!input.startsWith(literal, position)) throw error("Invalid literal");
            position += literal.length();
            return value;
        }

        private void whitespace() {
            while (!done() && Character.isWhitespace(input.charAt(position))) position++;
        }

        private boolean take(char expected) {
            if (!done() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) throw error("Expected '" + expected + "'");
        }

        private boolean done() {
            return position >= input.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at character " + position);
        }
    }
}

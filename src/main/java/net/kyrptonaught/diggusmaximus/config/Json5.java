package net.kyrptonaught.diggusmaximus.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON5 reader/writer for the legacy configuration files. */
public final class Json5 {
    private Json5() {
    }

    public static Map<String, Object> parseObject(String input) {
        Object value = new Parser(input).parseDocument();
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("JSON5 root must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, entry) -> result.put(String.valueOf(key), entry));
        return result;
    }

    public static String writeObject(Map<String, Object> value) {
        StringBuilder builder = new StringBuilder();
        writeValue(builder, value, 0);
        return builder.append(System.lineSeparator()).toString();
    }

    private static void writeValue(StringBuilder out, Object value, int indent) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String string) {
            writeString(out, string);
        } else if (value instanceof Boolean || value instanceof Number) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            if (!map.isEmpty()) {
                out.append(System.lineSeparator());
                int index = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    indent(out, indent + 2);
                    writeString(out, String.valueOf(entry.getKey()));
                    out.append(": ");
                    writeValue(out, entry.getValue(), indent + 2);
                    if (++index < map.size()) {
                        out.append(',');
                    }
                    out.append(System.lineSeparator());
                }
                indent(out, indent);
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            List<Object> entries = new ArrayList<>();
            iterable.forEach(entries::add);
            out.append('[');
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                writeValue(out, entries.get(i), indent);
            }
            out.append(']');
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static void indent(StringBuilder out, int amount) {
        out.append(" ".repeat(amount));
    }

    private static final class Parser {
        private final String input;
        private int cursor;

        private Parser(String input) {
            this.input = input.startsWith("\uFEFF") ? input.substring(1) : input;
        }

        private Object parseDocument() {
            skipTrivia();
            Object result = parseValue();
            skipTrivia();
            if (cursor != input.length()) {
                fail("Unexpected trailing content");
            }
            return result;
        }

        private Object parseValue() {
            skipTrivia();
            if (cursor >= input.length()) {
                fail("Expected value");
            }
            return switch (input.charAt(cursor)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '\'', '"' -> parseString();
                default -> parseBareValue();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipTrivia();
            while (!consume('}')) {
                String key = peek('\'') || peek('"') ? parseString() : parseBareKey();
                skipTrivia();
                expect(':');
                result.put(key, parseValue());
                skipTrivia();
                if (consume('}')) {
                    break;
                }
                expect(',');
                skipTrivia();
                if (consume('}')) {
                    break;
                }
            }
            return result;
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipTrivia();
            while (!consume(']')) {
                result.add(parseValue());
                skipTrivia();
                if (consume(']')) {
                    break;
                }
                expect(',');
                skipTrivia();
                if (consume(']')) {
                    break;
                }
            }
            return result;
        }

        private String parseString() {
            char quote = input.charAt(cursor++);
            StringBuilder out = new StringBuilder();
            while (cursor < input.length()) {
                char c = input.charAt(cursor++);
                if (c == quote) {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (cursor >= input.length()) {
                    fail("Unterminated escape");
                }
                char escaped = input.charAt(cursor++);
                switch (escaped) {
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'v' -> out.append('\u000B');
                    case '0' -> out.append('\0');
                    case '\n' -> { }
                    case '\r' -> {
                        if (peek('\n')) {
                            cursor++;
                        }
                    }
                    case 'u' -> out.append(parseUnicode());
                    case 'x' -> out.append(parseHexEscape(2, "hex"));
                    default -> out.append(escaped);
                }
            }
            fail("Unterminated string");
            return "";
        }

        private char parseUnicode() {
            return parseHexEscape(4, "unicode");
        }

        private char parseHexEscape(int length, String kind) {
            if (cursor + length > input.length()) {
                fail("Incomplete " + kind + " escape");
            }
            String digits = input.substring(cursor, cursor + length);
            cursor += length;
            try {
                return (char) Integer.parseInt(digits, 16);
            } catch (NumberFormatException exception) {
                fail("Invalid " + kind + " escape");
                return 0;
            }
        }

        private String parseBareKey() {
            int start = cursor;
            while (cursor < input.length()) {
                char c = input.charAt(cursor);
                if (Character.isWhitespace(c) || c == ':' || c == ',' || c == '}' || c == '/') {
                    break;
                }
                cursor++;
            }
            if (start == cursor) {
                fail("Expected object key");
            }
            return input.substring(start, cursor);
        }

        private Object parseBareValue() {
            int start = cursor;
            while (cursor < input.length()) {
                char c = input.charAt(cursor);
                if (Character.isWhitespace(c) || c == ',' || c == ']' || c == '}' || c == '/') {
                    break;
                }
                cursor++;
            }
            String token = input.substring(start, cursor);
            return switch (token) {
                case "true" -> true;
                case "false" -> false;
                case "null" -> null;
                case "Infinity", "+Infinity" -> Double.POSITIVE_INFINITY;
                case "-Infinity" -> Double.NEGATIVE_INFINITY;
                case "NaN", "+NaN", "-NaN" -> Double.NaN;
                default -> parseNumberOrString(token);
            };
        }

        private Object parseNumberOrString(String token) {
            if (token.isEmpty()) {
                fail("Expected value");
            }
            try {
                String normalized = token.replace("_", "");
                boolean negative = normalized.startsWith("-");
                String unsigned = normalized.startsWith("+") || negative ? normalized.substring(1) : normalized;
                if (unsigned.startsWith("0x") || unsigned.startsWith("0X")) {
                    BigInteger value = new BigInteger(unsigned.substring(2), 16);
                    return narrowInteger(negative ? value.negate() : value);
                }
                if (normalized.contains(".") || normalized.contains("e") || normalized.contains("E")) {
                    return new BigDecimal(normalized);
                }
                return narrowInteger(new BigInteger(normalized));
            } catch (NumberFormatException ignored) {
                return token;
            }
        }

        private static Number narrowInteger(BigInteger value) {
            try {
                return value.longValueExact();
            } catch (ArithmeticException ignored) {
                return value;
            }
        }

        private void skipTrivia() {
            while (cursor < input.length()) {
                char c = input.charAt(cursor);
                if (Character.isWhitespace(c)) {
                    cursor++;
                    continue;
                }
                if (c == '/' && cursor + 1 < input.length()) {
                    char next = input.charAt(cursor + 1);
                    if (next == '/') {
                        cursor += 2;
                        while (cursor < input.length() && input.charAt(cursor) != '\n' && input.charAt(cursor) != '\r') {
                            cursor++;
                        }
                        continue;
                    }
                    if (next == '*') {
                        int end = input.indexOf("*/", cursor + 2);
                        if (end < 0) {
                            fail("Unterminated block comment");
                        }
                        cursor = end + 2;
                        continue;
                    }
                }
                break;
            }
        }

        private boolean peek(char expected) {
            return cursor < input.length() && input.charAt(cursor) == expected;
        }

        private boolean consume(char expected) {
            if (peek(expected)) {
                cursor++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            skipTrivia();
            if (!consume(expected)) {
                fail("Expected '" + expected + "'");
            }
        }

        private void fail(String message) {
            throw new IllegalArgumentException(message + " at character " + cursor);
        }
    }
}

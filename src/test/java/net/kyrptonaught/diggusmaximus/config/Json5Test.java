package net.kyrptonaught.diggusmaximus.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Json5Test {
    @Test
    void readsLegacyJson5SyntaxAndRoundTripsUnknownValues() {
        String legacy = """
                // Legacy comments and unquoted keys
                {
                  enabled: false,
                  keybinding: {
                    rawKey: 'key.keyboard.k',
                    unknownFutureField: 0x2a,
                  },
                  tools: ['minecraft:stick',],
                  futureObject: { nested: true },
                }
                """;

        Map<String, Object> parsed = Json5.parseObject(legacy);
        assertEquals(false, parsed.get("enabled"));
        assertEquals(List.of("minecraft:stick"), parsed.get("tools"));
        Map<?, ?> key = assertInstanceOf(Map.class, parsed.get("keybinding"));
        assertEquals("key.keyboard.k", key.get("rawKey"));
        assertEquals(42L, key.get("unknownFutureField"));

        Map<String, Object> reparsed = Json5.parseObject(Json5.writeObject(parsed));
        assertEquals(parsed, reparsed);
        assertTrue(reparsed.containsKey("futureObject"));
    }

    @Test
    void preservesHexEscapesAndNumbersLargerThanLong() {
        Map<String, Object> parsed = Json5.parseObject("""
                {
                  escaped: '\\x41\\u0042',
                  hugeInteger: 18446744073709551615,
                  hugeHex: 0xffffffffffffffff,
                  preciseDecimal: 1234567890.12345678901234567890,
                }
                """);

        assertEquals("AB", parsed.get("escaped"));
        assertEquals(new BigInteger("18446744073709551615"), parsed.get("hugeInteger"));
        assertEquals(new BigInteger("18446744073709551615"), parsed.get("hugeHex"));
        assertEquals(new BigDecimal("1234567890.12345678901234567890"), parsed.get("preciseDecimal"));
        assertEquals(parsed, Json5.parseObject(Json5.writeObject(parsed)));
    }
}

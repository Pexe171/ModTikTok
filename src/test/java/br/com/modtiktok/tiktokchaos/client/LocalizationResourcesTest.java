package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.ActionRunState;
import br.com.modtiktok.tiktokchaos.live.ConnectionStatus;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import br.com.modtiktok.tiktokchaos.rule.ActionType;
import br.com.modtiktok.tiktokchaos.rule.ExecutionMode;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationResourcesTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?s");

    @Test
    void englishAndPortugueseContainExactlyTheSameKeysAndPlaceholders() throws Exception {
        JsonObject english = read("en_us.json");
        JsonObject portuguese = read("pt_br.json");

        assertEquals(english.keySet(), portuguese.keySet());
        assertTrue(english.size() >= 250, "The full dashboard must remain localized");
        for (String key : english.keySet()) {
            String en = english.get(key).getAsString();
            String pt = portuguese.get(key).getAsString();
            assertFalse(en.isBlank(), key + " is blank in en_us");
            assertFalse(pt.isBlank(), key + " is blank in pt_br");
            assertEquals(placeholders(en), placeholders(pt), key + " has incompatible placeholders");
            assertFalse(looksMojibaked(en), key + " is not valid readable UTF-8 in en_us");
            assertFalse(looksMojibaked(pt), key + " is not valid readable UTF-8 in pt_br");
        }
    }

    @Test
    void englishIsTheFallbackAndPortugueseIsActuallyTranslated() throws Exception {
        JsonObject english = read("en_us.json");
        JsonObject portuguese = read("pt_br.json");

        assertEquals("Connection", english.get("gui.tiktokchaos.tab.connection").getAsString());
        assertEquals("Conexão", portuguese.get("gui.tiktokchaos.tab.connection").getAsString());
        assertEquals("Spawn mob", english.get("action.tiktokchaos.spawn_entity").getAsString());
        assertEquals("Invocar mob", portuguese.get("action.tiktokchaos.spawn_entity").getAsString());
    }

    @Test
    void everyEnumLabelHasATranslation() throws Exception {
        JsonObject english = read("en_us.json");
        for (ConnectionStatus value : ConnectionStatus.values()) {
            assertTrue(english.has(enumKey("status.tiktokchaos.connection", value)), value.name());
        }
        for (ActionRunState value : ActionRunState.values()) {
            assertTrue(english.has(enumKey("status.tiktokchaos.actions", value)), value.name());
        }
        for (LiveEventType value : LiveEventType.values()) {
            assertTrue(english.has(enumKey("event.tiktokchaos", value)), value.name());
        }
        for (ActionType value : ActionType.values()) {
            assertTrue(english.has(enumKey("action.tiktokchaos", value)), value.name());
        }
        for (ExecutionMode value : ExecutionMode.values()) {
            assertTrue(english.has(enumKey("execution.tiktokchaos", value)), value.name());
        }
    }

    private static JsonObject read(String file) throws IOException {
        String resource = "assets/tiktokchaos/lang/" + file;
        InputStream stream = LocalizationResourcesTest.class.getClassLoader().getResourceAsStream(resource);
        assertTrue(stream != null, resource + " must be on the runtime classpath");
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            assertTrue(element.isJsonObject(), file + " must contain a JSON object");
            return element.getAsJsonObject();
        }
    }

    private static List<String> placeholders(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        return matcher.results().map(result -> result.group()).toList();
    }

    private static String enumKey(String prefix, Enum<?> value) {
        return prefix + "." + value.name().toLowerCase(Locale.ROOT);
    }

    private static boolean looksMojibaked(String value) {
        return value.contains("Ãƒ") || value.contains("Ã§") || value.contains("Ã£") || value.contains("Ã¡")
                || value.contains("Ã©") || value.contains("Ã­") || value.contains("Ã³") || value.contains("Ãº")
                || value.contains("Â ") || value.contains("â€") || value.contains("ï¿½") || value.contains("�");
    }
}

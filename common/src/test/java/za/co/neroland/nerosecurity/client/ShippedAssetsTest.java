package za.co.neroland.nerosecurity.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;

/**
 * The shipped client resources hang together: every blockstate variant, item definition and model parent points
 * at a model that exists, every texture a model names exists, every texture is 32 px wide (animated strips are
 * whole frames with an {@code .mcmeta}), and the keycard's {@code select} cases are exactly the strings
 * {@code KeycardItem.displayTier} writes.
 */
class ShippedAssetsTest {

    private static final String NS = "nerosecurity:";

    @Test
    void everyReferencedModelAndTextureExists() throws IOException {
        Path assets = assets();
        Set<String> roots = new TreeSet<>();
        try (Stream<Path> files = Files.list(assets.resolve("blockstates"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject variants = read(f).getAsJsonObject("variants");
                assertTrue(variants != null && !variants.isEmpty(), "no variants in " + f.getFileName());
                for (Map.Entry<String, JsonElement> v : variants.entrySet()) {
                    roots.add(v.getValue().getAsJsonObject().get("model").getAsString());
                }
            }
        }
        try (Stream<Path> files = Files.list(assets.resolve("items"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                List<String> refs = new ArrayList<>();
                collectItemModels(read(f), refs);
                assertTrue(!refs.isEmpty(), "item definition without a model: " + f.getFileName());
                roots.addAll(refs);
            }
        }
        List<String> missing = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<String> todo = new ArrayDeque<>(roots);
        while (!todo.isEmpty()) {
            String ref = todo.pop();
            if (!ref.startsWith(NS) || !seen.add(ref)) {
                continue;
            }
            Path model = assets.resolve("models/" + ref.substring(NS.length()) + ".json");
            if (!Files.isRegularFile(model)) {
                missing.add("model " + ref);
                continue;
            }
            JsonObject json = read(model);
            if (json.has("parent")) {
                todo.push(json.get("parent").getAsString());
            }
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> t : json.getAsJsonObject("textures").entrySet()) {
                    String tex = t.getValue().getAsString();
                    if (tex.startsWith(NS) && !Files.isRegularFile(
                            assets.resolve("textures/" + tex.substring(NS.length()) + ".png"))) {
                        missing.add("texture " + tex + " (in " + ref + ")");
                    }
                }
            }
            if (json.has("elements")) {
                for (JsonElement e : json.getAsJsonArray("elements")) {
                    for (Map.Entry<String, JsonElement> face : e.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                        String key = face.getValue().getAsJsonObject().get("texture").getAsString();
                        assertTrue(key.startsWith("#") && json.has("textures")
                                && json.getAsJsonObject("textures").has(key.substring(1))
                                || parentDefines(assets, json, key.substring(1)),
                                ref + " face " + face.getKey() + " uses undefined " + key);
                    }
                }
            }
        }
        assertEquals(new ArrayList<>(), missing);
    }

    @Test
    void keycardSelectCasesAreTheDisplayTiers() throws IOException {
        JsonObject select = read(assets().resolve("items/keycard.json")).getAsJsonObject("model");
        assertEquals("minecraft:select", select.get("type").getAsString());
        assertEquals("minecraft:custom_model_data", select.get("property").getAsString());
        Set<String> cases = new TreeSet<>();
        for (JsonElement c : select.getAsJsonArray("cases")) {
            JsonElement when = c.getAsJsonObject().get("when");
            if (when.isJsonArray()) {
                for (JsonElement w : when.getAsJsonArray()) {
                    cases.add(w.getAsString());
                }
            } else {
                cases.add(when.getAsString());
            }
        }
        // KeycardItem cannot be class-initialised in a plain JVM test (it is a registered Item), so the tier
        // strings are rebuilt from the same sources displayTier uses: AccessLevel.jsonName() and TIER_ENCRYPTED.
        Set<String> tiers = new TreeSet<>();
        for (AccessLevel level : AccessLevel.values()) {
            tiers.add(level.jsonName());
        }
        tiers.add(encryptedTier());
        assertEquals(tiers, cases);
        // the banded case tints layer 1 from colour 0, default PUBLIC grey
        String banded = select.toString();
        assertTrue(banded.contains("\"index\":0,\"default\":" + AccessLevel.PUBLIC.colour), banded);
    }

    @Test
    void texturesAreThirtyTwoWideAndStripsAreAnimated() throws IOException {
        Path textures = assets().resolve("textures");
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.walk(textures)) {
            for (Path png : files.filter(p -> p.toString().endsWith(".png")).toList()) {
                int[] size = pngSize(png);
                String name = textures.relativize(png).toString().replace('\\', '/');
                boolean animated = Files.isRegularFile(Path.of(png + ".mcmeta"));
                if (size[0] == 16) {
                    continue; // a retired 16x16 placeholder no model references (listed for deletion)
                }
                if (size[0] != 32) {
                    bad.add(name + " is " + size[0] + " px wide");
                } else if (animated ? size[1] % 32 != 0 || size[1] == 32 : size[1] != 32) {
                    bad.add(name + " is " + size[0] + "x" + size[1] + (animated ? " (animated)" : " without .mcmeta"));
                }
            }
        }
        assertEquals(new ArrayList<>(), bad);
    }

    private static String encryptedTier() throws IOException {
        Path src = SecurityColoursTest.locate(Path.of("common", "src", "main", "java", "za", "co", "neroland",
                "nerosecurity", "item", "KeycardItem.java"));
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("TIER_ENCRYPTED\\s*=\\s*\"([^\"]+)\"").matcher(Files.readString(src, StandardCharsets.UTF_8));
        assertTrue(m.find(), "KeycardItem.TIER_ENCRYPTED not found");
        assertTrue(Files.readString(src, StandardCharsets.UTF_8).contains("encrypted ? TIER_ENCRYPTED : level.jsonName()"),
                "displayTier no longer maps to jsonName()/TIER_ENCRYPTED; update this test and the item definition");
        return m.group(1);
    }

    private static boolean parentDefines(Path assets, JsonObject json, String key) {
        // Inherited texture variables (none of our element models rely on this, but stay correct if one does).
        JsonObject current = json;
        for (int depth = 0; depth < 8 && current.has("parent"); depth++) {
            String parent = current.get("parent").getAsString();
            if (!parent.startsWith(NS)) {
                return false;
            }
            Path p = assets.resolve("models/" + parent.substring(NS.length()) + ".json");
            if (!Files.isRegularFile(p)) {
                return false;
            }
            try {
                current = read(p);
            } catch (IOException e) {
                return false;
            }
            if (current.has("textures") && current.getAsJsonObject("textures").has(key)) {
                return true;
            }
        }
        return false;
    }

    private static void collectItemModels(JsonElement e, List<String> out) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("type") && "minecraft:model".equals(o.get("type").getAsString()) && o.has("model")) {
                out.add(o.get("model").getAsString());
            }
            for (Map.Entry<String, JsonElement> child : o.entrySet()) {
                collectItemModels(child.getValue(), out);
            }
        } else if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (JsonElement child : a) {
                collectItemModels(child, out);
            }
        }
    }

    private static int[] pngSize(Path png) throws IOException {
        try (InputStream in = Files.newInputStream(png)) {
            byte[] head = in.readNBytes(24);
            assertTrue(head.length == 24 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G', png + " is not a PNG");
            ByteBuffer b = ByteBuffer.wrap(head);
            return new int[] {b.getInt(16), b.getInt(20)};
        }
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Path assets() {
        return SecurityColoursTest.locate(Path.of("common", "src", "main", "resources", "assets", "nerosecurity"));
    }
}

package za.co.neroland.nerosecurity.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * The shipped data for the devices: every device is in the {@code nerosecurity:devices} tag (the
 * linking tool refuses anything outside it), mineable with a pickaxe, iron-tier where the spec says so,
 * has a loot table, a name and a tooltip; and every literal translation key the code uses exists in
 * {@code en_us.json}, so no screen or chat line shows a raw key.
 */
class ShippedDeviceContentTest {

    private static final List<String> DEVICES =
            List.of("keycard_programmer", "security_door", "blast_door", "secure_chest",
                    "laser_grid_node", "security_camera", "alarm", "security_monitor", "force_field_emitter");
    private static final List<String> IRON_TIER = List.of("security_door", "blast_door", "secure_chest",
            "laser_grid_node", "security_camera", "alarm", "security_monitor", "force_field_emitter");

    @Test
    void everyDeviceIsTaggedMineableAndDropsItself() throws IOException {
        Path data = resources().resolve(Path.of("data"));
        Set<String> devices = tagValues(data.resolve("nerosecurity/tags/block/devices.json"));
        Set<String> pickaxe = tagValues(data.resolve("minecraft/tags/block/mineable/pickaxe.json"));
        Set<String> iron = tagValues(data.resolve("minecraft/tags/block/needs_iron_tool.json"));
        for (String device : DEVICES) {
            String id = "nerosecurity:" + device;
            assertTrue(devices.contains(id), id + " must be a linkable device");
            assertTrue(pickaxe.contains(id), id + " must be mineable with a pickaxe");
            assertTrue(Files.isRegularFile(data.resolve("nerosecurity/loot_table/blocks/" + device + ".json")),
                    id + " must have a loot table so it drops when broken");
        }
        for (String device : IRON_TIER) {
            assertTrue(iron.contains("nerosecurity:" + device), device + " must need an iron tool");
        }
        assertTrue(!devices.contains("nerosecurity:security_hub"), "the hub is the installation, not a device");
        assertTrue(!devices.contains("nerosecurity:force_field"), "a field block is projected, never linked");
        assertTrue(!pickaxe.contains("nerosecurity:force_field"), "a field block is not mined");
        assertTrue(!Files.exists(data.resolve("nerosecurity/loot_table/blocks/force_field.json")),
                "a field block drops nothing");
    }

    @Test
    void theHubTheFieldAndEveryDeviceAreBossImmune() throws IOException {
        Path data = resources().resolve(Path.of("data"));
        for (String tag : List.of("wither_immune", "dragon_immune")) {
            Set<String> immune = tagValues(data.resolve("minecraft/tags/block/" + tag + ".json"));
            assertTrue(immune.contains("nerosecurity:force_field"), "the field must be in minecraft:" + tag);
            // A wither or the dragon must not delete the hub (orphaning the installation) or walk through devices;
            // raiding stays mining and overloading, which these tags do not touch.
            assertTrue(immune.contains("nerosecurity:security_hub"), "the hub must be in minecraft:" + tag);
            for (String device : DEVICES) {
                assertTrue(immune.contains("nerosecurity:" + device), device + " must be in minecraft:" + tag);
            }
        }
        Set<String> upgrades = tagValues(data.resolve("neroland/tags/item/highlight/upgrades.json"));
        assertTrue(upgrades.contains("nerosecurity:strength_module"), "the strength module is highlighted as an upgrade");
        assertTrue(upgrades.contains("nerosecurity:range_module"), "the range module is highlighted as an upgrade");
    }

    @Test
    void everyLiteralTranslationKeyInTheCodeExists() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(
                resources().resolve("assets/nerosecurity/lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        for (String device : DEVICES) {
            assertTrue(lang.has("block.nerosecurity." + device), device + " needs a name");
            assertTrue(lang.has("block.nerosecurity." + device + ".tooltip"), device + " needs a tooltip");
        }

        Path java = resources().getParent().resolve("java");
        Pattern key = Pattern.compile("\"([a-z_]+\\.nerosecurity\\.[a-z0-9_.]*[a-z0-9_])\"");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(java)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = key.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (m.find()) {
                    if (!lang.has(m.group(1))) {
                        missing.add(m.group(1) + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(new ArrayList<>(), new ArrayList<>(missing), "translation keys used in code but not in en_us.json");
    }

    private static Set<String> tagValues(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), "missing tag " + file);
        Set<String> out = new TreeSet<>();
        for (JsonElement e : JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("values")) {
            out.add(e.getAsString());
        }
        return out;
    }

    private static Path resources() {
        Path relative = Path.of("common", "src", "main", "resources");
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return fail("could not locate " + relative + " from " + System.getProperty("user.dir"));
    }
}

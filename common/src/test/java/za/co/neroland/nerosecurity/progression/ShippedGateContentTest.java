package za.co.neroland.nerosecurity.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.progression.Gate;
import za.co.neroland.nerolandcore.progression.GateScope;

/**
 * The shipped gate definitions must decode with Core's real {@link Gate#DATA_CODEC} — the exact
 * codec Core runs at load — and keep the milestone contract: exactly one gate file per
 * {@link SecurityMilestones.Milestone}, each player-scoped, carrying <em>no</em> {@code requires}
 * (milestones are records, never locks or access checks) and a display title.
 *
 * <p>None of the gates depends on the keycard item being registered, so this passes before the
 * keycard exists.
 */
class ShippedGateContentTest {

    @Test
    void shippedGatesAreExactlyTheMilestonesAsRequirementFreePlayerRecords() throws Exception {
        Path gatesDir = shippedDir(Path.of("data", "nerosecurity", "neroland_gates"));

        Map<String, Gate.Data> parsed = new LinkedHashMap<>();
        List<String> complaints = new ArrayList<>();
        try (Stream<Path> files = Files.walk(gatesDir)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String relative = gatesDir.relativize(file).toString().replace('\\', '/');
                if (!relative.endsWith(".json")) {
                    continue;
                }
                String gatePath = relative.substring(0, relative.length() - ".json".length());
                parsed.put(gatePath, decode(file, gatePath, complaints));
            }
        }
        assertEquals(List.of(), complaints,
                "shipped gate JSONs must decode against Core's Gate.DATA_CODEC without complaint");

        List<String> expected = new ArrayList<>();
        for (SecurityMilestones.Milestone milestone : SecurityMilestones.Milestone.values()) {
            assertEquals("nerosecurity", milestone.gate().getNamespace(),
                    milestone + " must open a NeroSecurity gate");
            expected.add(milestone.gate().getPath());
        }
        assertEquals(expected.stream().sorted().toList(), parsed.keySet().stream().sorted().toList(),
                "exactly one gate file per SecurityMilestones.Milestone ships");

        for (Map.Entry<String, Gate.Data> entry : parsed.entrySet()) {
            String gatePath = entry.getKey();
            Gate.Data gate = entry.getValue();
            assertEquals(GateScope.PLAYER, gate.scope(),
                    gatePath + " must be player-scoped: a milestone is personal, never server-wide");
            assertEquals(List.of(), gate.requires(),
                    gatePath + " is a record gate and must carry no requires");
            assertFalse(gate.title().isBlank(), gatePath + " must carry a display title");
        }
    }

    private static Gate.Data decode(Path file, String gatePath, List<String> complaints) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            JsonElement json = JsonParser.parseReader(reader);
            Optional<Gate.Data> data = Gate.DATA_CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> complaints.add(gatePath + ": " + error));
            if (data.isEmpty()) {
                return fail("Shipped gate " + gatePath + " did not decode at all");
            }
            return data.get();
        }
    }

    /** Walks up from the test JVM's working dir to the shipped resource tree (per-node cwds). */
    private static Path shippedDir(Path underResources) {
        Path relative = Path.of("common", "src", "main", "resources").resolve(underResources);
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return fail("could not locate " + underResources + " from " + System.getProperty("user.dir"));
    }
}

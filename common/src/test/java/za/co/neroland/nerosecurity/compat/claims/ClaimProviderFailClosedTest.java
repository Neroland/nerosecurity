package za.co.neroland.nerosecurity.compat.claims;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

/**
 * Every claim provider this build ships fails closed without a server: a claim check that cannot ask anything never
 * answers "allowed". Found through {@code META-INF/services} exactly as {@link ClaimsCompat} finds them, so the same
 * test runs in a build without the OPAC bridge (nothing listed, nothing to check) and in one with it (the listed
 * provider must load and must refuse).
 */
class ClaimProviderFailClosedTest {

    private static final String SERVICES = "META-INF/services/" + ClaimProvider.class.getName();

    @Test
    void everyShippedProviderRefusesWithoutAServer() throws IOException {
        List<String> listed = listed();
        List<ClaimProvider> loaded = new ArrayList<>();
        Iterator<ClaimProvider> it = ServiceLoader.load(ClaimProvider.class, ClaimsCompat.class.getClassLoader()).iterator();
        while (it.hasNext()) {
            loaded.add(it.next());
        }
        assertTrue(loaded.size() >= listed.size(), "every listed provider loads: " + listed);
        Identifier dimension = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        for (ClaimProvider provider : loaded) {
            assertFalse(provider.mayInteract(null, dimension, new BlockPos(8, 64, 8), UUID.randomUUID()),
                    provider.id() + " must fail closed for a null server");
        }
        if (listed.contains("za.co.neroland.nerosecurity.compat.claims.OpacClaimProvider")) {
            // By id, not by class: this test also compiles in a build that leaves the OPAC bridge out.
            Identifier opac = Identifier.fromNamespaceAndPath("nerosecurity", "opac");
            assertTrue(loaded.stream().anyMatch(p -> opac.equals(p.id())), "OPAC provider loaded");
        }
    }

    private static List<String> listed() throws IOException {
        List<String> names = new ArrayList<>();
        try (InputStream in = ClaimsCompat.class.getClassLoader().getResourceAsStream(SERVICES)) {
            if (in == null) {
                return names;
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String name = line.strip();
                if (!name.isEmpty() && !name.startsWith("#")) {
                    names.add(name);
                }
            }
        }
        return names;
    }
}

package za.co.neroland.nerosecurity.keycard;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.KeycardSigner;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** Shared fixtures: a real store, real installations, and cards issued and signed the production way. */
public final class KeycardTestSupport {

    public static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    private KeycardTestSupport() {
    }

    public static Installation create(SecurityInstallations store, UUID owner, String name, int x) {
        return store.create(owner, name, new DevicePos(OVERWORLD, new BlockPos(x, 64, 0)), 0L, 8).installation();
    }

    /** Issues a ledger row and returns the matching signed card. */
    public static KeycardData issue(SecurityInstallations store, Installation installation, AccessLevel level,
            boolean encrypted, UUID boundOwner) {
        long now = 1_000L;
        SecurityInstallations.IssueResult result = store.issueCard(installation.id(), level, encrypted, boundOwner,
                now, 512);
        if (result.status() != SecurityInstallations.IssueStatus.OK) {
            throw new IllegalStateException("issue failed: " + result.status());
        }
        KeycardPayload payload = new KeycardPayload(installation.id(), level, result.serial(), encrypted,
                boundOwner != null, now);
        return KeycardData.of(payload, KeycardSigner.sign(installation, payload), installation.name());
    }

    /** The same card with a different payload but the original tag (a forged edit). */
    public static KeycardData withPayload(KeycardData card, KeycardPayload payload) {
        return KeycardData.of(payload, card.signature(), card.label());
    }
}

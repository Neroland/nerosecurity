package za.co.neroland.nerosecurity.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.keycard.CardStatus;
import za.co.neroland.nerosecurity.keycard.KeycardData;
import za.co.neroland.nerosecurity.keycard.KeycardVerifier;
import za.co.neroland.nerosecurity.registry.NeroSecurityDataComponents;

/**
 * The keycard source ({@code nerosecurity:keycard}): grants the highest level among the valid cards the
 * acting player is holding (main hand, then off hand) for the installation being asked about.
 *
 * <ul>
 *   <li>The actor is looked up <b>by UUID</b> from the server's player list. Never by proximity: a
 *       card in someone else's hand next to the door does nothing for you.</li>
 *   <li>It never denies. A dead or foreign card in the off hand must not lock a member out of their own
 *       base; a card is a way in, never a way to be kept out.</li>
 *   <li>Every card is re-verified on every use against the installation's server-held secret and
 *       ledger. A card for this installation that fails (tampered, revoked, wrong holder) raises
 *       {@code KEYCARD_REJECTED}, at most once per installation and actor every
 *       {@link #REJECTED_COOLDOWN_MS} ms. Foreign cards raise nothing.</li>
 * </ul>
 */
public final class KeycardSource implements AccessSource {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "keycard");

    /** 100 ticks. */
    public static final long REJECTED_COOLDOWN_MS = 5_000L;
    private static final int COOLDOWN_PRUNE_THRESHOLD = 1_024;

    /** Which cards {@code actor} is presenting. Production reads the two hands; tests inject. */
    @FunctionalInterface
    public interface Presenter {
        List<KeycardData> presented(@Nullable MinecraftServer server, UUID actor);
    }

    /** The acting player's main-hand and off-hand cards, looked up by UUID. */
    public static final Presenter HANDS = (server, actor) -> {
        if (server == null || actor == null) {
            return List.of();
        }
        ServerPlayer player = server.getPlayerList().getPlayer(actor);
        if (player == null) {
            return List.of();
        }
        List<KeycardData> cards = new ArrayList<>(2);
        KeycardData main = player.getMainHandItem().get(NeroSecurityDataComponents.KEYCARD.get());
        if (main != null) {
            cards.add(main);
        }
        KeycardData off = player.getOffhandItem().get(NeroSecurityDataComponents.KEYCARD.get());
        if (off != null) {
            cards.add(off);
        }
        return cards;
    };

    public static final KeycardSource INSTANCE = new KeycardSource(HANDS, System::currentTimeMillis);

    private final Presenter presenter;
    private final LongSupplier clock;
    private final Map<CooldownKey, Long> lastRejected = new ConcurrentHashMap<>();

    private record CooldownKey(UUID installation, UUID actor) {
    }

    public KeycardSource(Presenter presenter, LongSupplier clock) {
        this.presenter = presenter;
        this.clock = clock;
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public AccessVote vote(AccessQuery query) {
        Installation installation = query.installation();
        UUID actor = query.actor();
        if (installation == null || actor == null || Installation.TOMBSTONE_OWNER.equals(actor)) {
            return AccessVote.Abstain.INSTANCE;
        }
        List<KeycardData> cards = presenter.presented(query.server(), actor);
        if (cards == null || cards.isEmpty()) {
            return AccessVote.Abstain.INSTANCE;
        }
        AccessLevel best = null;
        for (KeycardData card : cards) {
            CardStatus status = KeycardVerifier.verify(installation, card, actor);
            if (status == CardStatus.VALID) {
                best = best == null ? card.level() : AccessLevel.max(best, card.level());
            } else if (KeycardVerifier.isTamper(status)) {
                rejected(query, installation, actor);
            }
        }
        return best == null ? AccessVote.Abstain.INSTANCE : new AccessVote.Grant(best);
    }

    /** Publishes KEYCARD_REJECTED, rate-limited per installation and actor. Never throws. */
    private void rejected(AccessQuery query, Installation installation, UUID actor) {
        try {
            long now = clock.getAsLong();
            CooldownKey key = new CooldownKey(installation.id(), actor);
            Long last = lastRejected.get(key);
            if (last != null && now - last < REJECTED_COOLDOWN_MS) {
                return;
            }
            if (lastRejected.size() >= COOLDOWN_PRUNE_THRESHOLD) {
                lastRejected.values().removeIf(at -> now - at >= REJECTED_COOLDOWN_MS);
            }
            lastRejected.put(key, now);
            SecurityEventBus.publish(SecurityEvent.of(SecurityEventType.KEYCARD_REJECTED, installation.id(),
                    query.at(), actor, now));
        } catch (RuntimeException | LinkageError e) {
            // An event is a side effect; it must never change the vote.
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] keycard rejection event failed ({})",
                    e.getClass().getName());
        }
    }
}

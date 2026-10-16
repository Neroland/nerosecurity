package za.co.neroland.nerosecurity.keycard;

import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.KeycardSigner;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.installation.ServiceResult;
import za.co.neroland.nerosecurity.progression.SecurityMilestones;
import za.co.neroland.nerosecurity.registry.NeroSecurityDataComponents;
import za.co.neroland.nerosecurity.registry.NeroSecurityItems;

/**
 * Every keycard use case: writing, copying and wiping at a programmer, and revoking or setting the
 * automation policy by command. Each method resolves through {@link AccessResolver#DEFAULT} first,
 * then applies the card rules below to the decision's effective level (they only ever refuse more,
 * never allow what the resolver refused), writes the ledger through {@link SecurityInstallations},
 * publishes its event after the write, and returns a message safe to show the actor.
 *
 * <h2>Card rules</h2>
 * <ul>
 *   <li><b>Write</b> a level-L card: effective strictly above L, except that an OWNER writes OWNER.</li>
 *   <li><b>Encrypted</b> cards (write or copy): OWNER (an operator counts as OWNER) <b>and</b> an
 *       encryption module fitted to the programmer, both checked here on the server.</li>
 *   <li><b>Basic</b> (non-encrypted) cards may not exceed {@code keycardBasicMaxLevel}.</li>
 *   <li><b>Copy</b> a basic card: only a card the actor could have written, or one of the actor's own
 *       tier (so a MEMBER copies MEMBER cards, an OFFICER copies MEMBER and OFFICER cards); still within
 *       {@code keycardBasicMaxLevel}. Once the ledger is more than three quarters full, copying needs an
 *       effective OFFICER, so members cannot fill it. The copy gets its own serial and is never bound. A
 *       card for another installation cannot be copied here.</li>
 * </ul>
 *
 * <p>Server thread only. Slot handling (which stacks are where) belongs to the caller; these methods
 * take the stacks and edit their components in place.
 */
public final class KeycardService {

    private KeycardService() {
    }

    // --- programmer use cases -------------------------------------------------------------------

    /**
     * Writes a new card onto a blank keycard in {@code card}.
     *
     * @param hasModule whether the programmer has an encryption module fitted (read from the block
     *                  entity on the server, never from the client)
     */
    public static ServiceResult write(ServerPlayer actor, Installation installation, DevicePos at, ItemStack card,
            AccessLevel level, boolean encrypted, boolean bind, boolean hasModule) {
        if (!isKeycard(card)) {
            return fail("programmer.nerosecurity.no_card");
        }
        if (card.getCount() != 1) {
            return fail("programmer.nerosecurity.one_card");
        }
        if (!isBlank(card)) {
            return fail("programmer.nerosecurity.not_blank");
        }
        AccessDecision decision = resolve(actor, installation, AccessAction.PROGRAM_CARD,
                AccessAction.PROGRAM_CARD.defaultMinimum, at);
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        AccessLevel effective = decision.effective();
        if (level == null || !mayWrite(effective, level)) {
            return fail(level == AccessLevel.OWNER ? "programmer.nerosecurity.owner_only"
                    : "programmer.nerosecurity.level_too_high");
        }
        if (encrypted) {
            if (effective != AccessLevel.OWNER) {
                return fail("programmer.nerosecurity.needs_owner");
            }
            if (!hasModule) {
                return fail("programmer.nerosecurity.needs_module");
            }
        }
        AccessLevel basicMax = NeroSecurityConfig.keycardBasicMaxLevel();
        if (!basicLevelAllowed(level, encrypted, basicMax)) {
            return ServiceResult.failure(Component.translatable("programmer.nerosecurity.encrypt_required",
                    basicMax.displayName()));
        }
        long now = now();
        UUID boundOwner = bind ? actor.getUUID() : null;
        ServiceResult issued = issueOnto(actor, installation, card, level, encrypted, boundOwner, now);
        if (!issued.ok()) {
            return issued;
        }
        publish(SecurityEventType.KEYCARD_WRITTEN, installation, at, actor.getUUID());
        SecurityMilestones.record(actor, SecurityMilestones.Milestone.FIRST_KEYCARD);
        return ServiceResult.success(Component.translatable("programmer.nerosecurity.written", level.displayName()));
    }

    /**
     * Copies the card in {@code source} onto the blank in {@code blank}. The copy has a new serial in
     * the ledger and is never bound.
     */
    public static ServiceResult clone(ServerPlayer actor, Installation installation, DevicePos at, ItemStack source,
            ItemStack blank, boolean hasModule) {
        KeycardData original = isKeycard(source) ? source.get(NeroSecurityDataComponents.KEYCARD.get()) : null;
        if (original == null) {
            return fail("programmer.nerosecurity.no_card");
        }
        if (KeycardVerifier.verify(installation, original, actor.getUUID()) != CardStatus.VALID) {
            return fail("programmer.nerosecurity.foreign_card");
        }
        AccessDecision decision = resolve(actor, installation, AccessAction.PROGRAM_CARD, AccessLevel.MEMBER, at);
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        AccessLevel effective = decision.effective();
        if (!mayClone(effective, original.level(), original.encrypted(), hasModule)) {
            if (original.encrypted()) {
                return fail(effective != AccessLevel.OWNER ? "programmer.nerosecurity.needs_owner"
                        : "programmer.nerosecurity.needs_module");
            }
            return fail("programmer.nerosecurity.clone_too_high");
        }
        AccessLevel basicMax = NeroSecurityConfig.keycardBasicMaxLevel();
        if (!basicLevelAllowed(original.level(), original.encrypted(), basicMax)) {
            return ServiceResult.failure(Component.translatable("programmer.nerosecurity.encrypt_required",
                    basicMax.displayName()));
        }
        if (!cloneBudgetAllows(effective, installation.cardCount(), NeroSecurityConfig.keycardsPerInstallation())) {
            return fail("programmer.nerosecurity.ledger_nearly_full");
        }
        if (!isKeycard(blank) || !isBlank(blank)) {
            return fail("programmer.nerosecurity.no_blank");
        }
        if (blank.getCount() != 1) {
            return fail("programmer.nerosecurity.one_card");
        }
        ServiceResult issued = issueOnto(actor, installation, blank, original.level(), original.encrypted(), null, now());
        if (!issued.ok()) {
            return issued;
        }
        publish(SecurityEventType.KEYCARD_CLONED, installation, at, actor.getUUID());
        return ServiceResult.success(Component.translatable("programmer.nerosecurity.cloned",
                original.level().displayName()));
    }

    /**
     * Wipes the card in {@code card} back to blank. A card signed by this installation loses its ledger
     * row too, whoever holds it and whether or not it is bound, so a copy of it dies with it and no
     * orphan row is left counting toward the cap.
     */
    public static ServiceResult wipe(ServerPlayer actor, Installation installation, DevicePos at, ItemStack card) {
        if (!isKeycard(card)) {
            return fail("programmer.nerosecurity.no_card");
        }
        AccessDecision decision = resolve(actor, installation, AccessAction.PROGRAM_CARD, AccessLevel.MEMBER, at);
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        KeycardData data = card.get(NeroSecurityDataComponents.KEYCARD.get());
        if (KeycardVerifier.signedBy(installation, data)) {
            store(actor).revokeCard(installation.id(), data.serial());
        }
        card.remove(NeroSecurityDataComponents.KEYCARD.get());
        publish(SecurityEventType.KEYCARD_WIPED, installation, at, actor.getUUID());
        return ServiceResult.success(Component.translatable("programmer.nerosecurity.wiped"));
    }

    // --- command use cases ----------------------------------------------------------------------

    /** Revokes one card by serial. OWNER. */
    public static ServiceResult revoke(ServerPlayer actor, Installation installation, long serial) {
        AccessDecision decision = resolve(actor, installation, AccessAction.REVOKE_CARD,
                AccessAction.REVOKE_CARD.defaultMinimum, installation.hubPos());
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        if (!store(actor).revokeCard(installation.id(), serial)) {
            return fail("command.nerosecurity.keycards.not_found");
        }
        publish(SecurityEventType.KEYCARD_REVOKED, installation, null, actor.getUUID());
        return ServiceResult.success(Component.translatable("command.nerosecurity.keycards.revoked", serial));
    }

    /** Revokes every card the installation has issued so far. OWNER. One event. */
    public static ServiceResult revokeAll(ServerPlayer actor, Installation installation) {
        AccessDecision decision = resolve(actor, installation, AccessAction.REVOKE_CARD,
                AccessAction.REVOKE_CARD.defaultMinimum, installation.hubPos());
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        int removed = store(actor).revokeAllCards(installation.id());
        if (removed < 0) {
            return fail("tool.nerosecurity.hub_gone");
        }
        publish(SecurityEventType.KEYCARD_REVOKED, installation, null, actor.getUUID());
        return ServiceResult.success(Component.translatable("command.nerosecurity.keycards.revoked_all", removed));
    }

    /** Sets the secure-chest automation policy: null (off), MEMBER or OFFICER. OWNER. */
    public static ServiceResult setAutomation(ServerPlayer actor, Installation installation,
            @Nullable AccessLevel level) {
        AccessDecision decision = resolve(actor, installation, AccessAction.SET_AUTOMATION,
                AccessAction.SET_AUTOMATION.defaultMinimum, installation.hubPos());
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        if (!SecurityInstallations.isAutomationLevel(level)) {
            return fail("command.nerosecurity.automation.invalid");
        }
        if (!store(actor).setAutomationLevel(installation.id(), level)) {
            return fail("tool.nerosecurity.hub_gone");
        }
        publish(SecurityEventType.AUTOMATION_POLICY_CHANGED, installation, null, actor.getUUID());
        return ServiceResult.success(Component.translatable("command.nerosecurity.automation.set",
                automationName(level)));
    }

    /** The display name of an automation policy: the level, or "off". */
    public static Component automationName(@Nullable AccessLevel level) {
        return level == null ? Component.translatable("chest.nerosecurity.automation.off") : level.displayName();
    }

    // --- pure rule seams (plain-JVM tests) ------------------------------------------------------

    /**
     * Writing a {@code target} card needs effective strictly above it, except OWNER writes OWNER. A
     * MEMBER writes nothing (writing is an OFFICER action; members may only copy).
     */
    public static boolean mayWrite(AccessLevel effective, AccessLevel target) {
        if (effective == null || target == null || !effective.atLeast(AccessLevel.OFFICER)) {
            return false;
        }
        return target == AccessLevel.OWNER ? effective == AccessLevel.OWNER : effective.ordinal() > target.ordinal();
    }

    /**
     * Copying a {@code level} card: an encrypted card needs OWNER and a module; a basic card needs an
     * actor (MEMBER or above) who could have written it, or whose own tier it is.
     */
    public static boolean mayClone(AccessLevel effective, AccessLevel level, boolean encrypted, boolean hasModule) {
        if (effective == null || level == null) {
            return false;
        }
        if (encrypted) {
            return effective == AccessLevel.OWNER && hasModule;
        }
        return effective.atLeast(AccessLevel.MEMBER) && (mayWrite(effective, level) || effective == level);
    }

    /**
     * The copy budget: while the ledger holds at most three quarters of {@code cap} rows anyone allowed
     * to copy may; above that only an effective OFFICER or OWNER may, so a member cannot fill the ledger
     * with copies and lock everyone out of writing. The cap itself is still enforced by the store.
     */
    public static boolean cloneBudgetAllows(AccessLevel effective, int used, int cap) {
        if (effective == null) {
            return false;
        }
        return used * 4L <= cap * 3L || effective.atLeast(AccessLevel.OFFICER);
    }

    /** A basic (non-encrypted) card may not carry a level above {@code basicMax}; encrypted cards may. */
    public static boolean basicLevelAllowed(AccessLevel target, boolean encrypted, AccessLevel basicMax) {
        if (target == null) {
            return false;
        }
        return encrypted || basicMax == null || basicMax.atLeast(target);
    }

    // --- stack helpers --------------------------------------------------------------------------

    /** Whether {@code stack} is a NeroSecurity keycard (blank or encoded). */
    public static boolean isKeycard(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(NeroSecurityItems.KEYCARD.get());
    }

    /** Whether {@code stack} is a keycard with no card data (a blank). */
    public static boolean isBlank(@Nullable ItemStack stack) {
        return isKeycard(stack) && !stack.has(NeroSecurityDataComponents.KEYCARD.get());
    }

    // --- helpers --------------------------------------------------------------------------------

    /** Issues a ledger row and writes the signed card onto {@code target}. */
    private static ServiceResult issueOnto(ServerPlayer actor, Installation installation, ItemStack target,
            AccessLevel level, boolean encrypted, @Nullable UUID boundOwner, long now) {
        int cap = NeroSecurityConfig.keycardsPerInstallation();
        SecurityInstallations.IssueResult issue = store(actor).issueCard(installation.id(), level, encrypted,
                boundOwner, now, cap);
        switch (issue.status()) {
            case OK -> {
                KeycardPayload payload = new KeycardPayload(installation.id(), level, issue.serial(), encrypted,
                        boundOwner != null, now);
                byte[] signature = KeycardSigner.sign(installation, payload);
                target.set(NeroSecurityDataComponents.KEYCARD.get(), KeycardData.of(payload, signature, installation.name()));
                return ServiceResult.success(Component.empty());
            }
            case LEDGER_FULL -> {
                return ServiceResult.failure(Component.translatable("programmer.nerosecurity.ledger_full", cap));
            }
            default -> {
                return fail("tool.nerosecurity.hub_gone");
            }
        }
    }

    private static AccessDecision resolve(ServerPlayer actor, Installation installation, AccessAction action,
            AccessLevel required, @Nullable DevicePos at) {
        return AccessResolver.DEFAULT.resolve(actor, installation, action, required, at, null);
    }

    private static SecurityInstallations store(ServerPlayer actor) {
        return SecurityInstallations.get(actor.level().getServer());
    }

    private static ServiceResult fail(String key) {
        return ServiceResult.failure(Component.translatable(key));
    }

    private static void publish(SecurityEventType type, Installation installation, @Nullable DevicePos at,
            @Nullable UUID actor) {
        SecurityEventBus.publish(SecurityEvent.of(type, installation.id(), at, actor, now()));
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}

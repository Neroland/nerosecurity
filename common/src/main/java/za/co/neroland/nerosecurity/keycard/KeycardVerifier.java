package za.co.neroland.nerosecurity.keycard;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.installation.CardRecord;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.KeycardSigner;

/**
 * Decides whether a presented card is good for one installation. Pure logic over the installation's
 * server-side state and the card read from the server-side item stack; nothing the client says is
 * consulted. Runs on every use, never cached.
 */
public final class KeycardVerifier {

    private KeycardVerifier() {
    }

    /**
     * Checks, in order: present; same installation; signature (constant-time); not below the serial
     * floor; serial still in the ledger; ledger row matches the card; and for a bound card, that the
     * presenter is its recorded holder.
     */
    public static CardStatus verify(Installation installation, @Nullable KeycardData card, UUID presenter) {
        if (card == null) {
            return CardStatus.BLANK;
        }
        if (installation == null || !card.installationId().equals(installation.id())) {
            return CardStatus.WRONG_INSTALLATION;
        }
        if (!KeycardSigner.verifySignature(installation, card.payload(), card.signature())) {
            return CardStatus.BAD_SIGNATURE;
        }
        if (card.serial() < installation.minValidSerial()) {
            return CardStatus.BELOW_FLOOR;
        }
        CardRecord record = installation.card(card.serial());
        if (record == null) {
            return CardStatus.UNKNOWN_SERIAL;
        }
        if (record.level() != card.level() || record.encrypted() != card.encrypted()
                || (record.boundOwner() != null) != card.bound()) {
            return CardStatus.RECORD_MISMATCH;
        }
        if (card.bound()) {
            UUID holder = record.boundOwner();
            if (holder == null) {
                return CardStatus.REVOKED_BINDING;
            }
            if (!holder.equals(presenter)) {
                return CardStatus.NOT_BOUND_HOLDER;
            }
        }
        return CardStatus.VALID;
    }

    /**
     * Whether {@code card} was issued by {@code installation}: same installation and a signature that
     * verifies (constant-time). Nothing about the holder, the binding or the ledger is checked, so it is
     * only for cleaning up after a card that is going away (a wipe revokes its ledger row), never for
     * deciding access.
     */
    public static boolean signedBy(Installation installation, @Nullable KeycardData card) {
        return card != null && installation != null && card.installationId().equals(installation.id())
                && KeycardSigner.verifySignature(installation, card.payload(), card.signature());
    }

    /** Statuses that mean a card for this installation was refused, which is worth an event. */
    public static boolean isTamper(CardStatus status) {
        return switch (status) {
            case BAD_SIGNATURE, RECORD_MISMATCH, UNKNOWN_SERIAL, BELOW_FLOOR, REVOKED_BINDING, NOT_BOUND_HOLDER -> true;
            case VALID, BLANK, WRONG_INSTALLATION -> false;
        };
    }
}

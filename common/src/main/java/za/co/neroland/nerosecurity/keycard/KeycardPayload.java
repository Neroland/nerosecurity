package za.co.neroland.nerosecurity.keycard;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

import za.co.neroland.nerosecurity.access.AccessLevel;

/**
 * The signed part of a keycard, and its canonical byte layout. Pure JVM: no game types, so the
 * signer and verifier can be tested without a running game.
 *
 * <p>Layout (38 bytes, big-endian, fixed order):
 * <pre>
 *  [0..3]   MAGIC "NSK1"
 *  [4..19]  installation id (most significant bits, then least significant bits)
 *  [20]     level ordinal (0..3; operator override is not a level and can never be encoded)
 *  [21]     flags: bit0 = encrypted, bit1 = bound; all other bits zero
 *  [22..29] serial
 *  [30..37] issued-at (epoch ms)
 * </pre>
 * The magic carries a layout version so a future change can never be confused with this one.
 */
public record KeycardPayload(UUID installationId, AccessLevel level, long serial, boolean encrypted, boolean bound,
        long issuedAt) {

    /** Version 1 of the layout. */
    public static final byte[] MAGIC = {'N', 'S', 'K', '1'};
    public static final int LENGTH = 38;

    public KeycardPayload {
        Objects.requireNonNull(installationId, "installationId");
        Objects.requireNonNull(level, "level");
    }

    /** The exact bytes the signature covers. A fresh array on every call. */
    public byte[] canonicalBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(LENGTH);
        buffer.put(MAGIC);
        buffer.putLong(installationId.getMostSignificantBits());
        buffer.putLong(installationId.getLeastSignificantBits());
        buffer.put((byte) level.ordinal());
        buffer.put((byte) ((encrypted ? 1 : 0) | (bound ? 2 : 0)));
        buffer.putLong(serial);
        buffer.putLong(issuedAt);
        return buffer.array();
    }
}

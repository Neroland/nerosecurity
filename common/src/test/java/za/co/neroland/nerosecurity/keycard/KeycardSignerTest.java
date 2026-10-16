package za.co.neroland.nerosecurity.keycard;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.KeycardSigner;

/** The signer and the canonical layout, in a plain JVM. */
class KeycardSignerTest {

    private static byte[] secret(int seed) {
        byte[] bytes = new byte[Installation.SECRET_BYTES];
        Arrays.fill(bytes, (byte) seed);
        return bytes;
    }

    private final Installation a = new Installation(UUID.randomUUID(), UUID.randomUUID(), "A", 0L, secret(1));

    @Test
    void signatureIsDeterministicAndSixteenBytes() {
        KeycardPayload payload = new KeycardPayload(a.id(), AccessLevel.OFFICER, 7L, false, true, 123L);
        byte[] first = KeycardSigner.sign(a, payload);
        byte[] second = KeycardSigner.sign(a, payload);
        assertEquals(KeycardData.SIGNATURE_BYTES, first.length);
        assertArrayEquals(first, second);
        assertTrue(KeycardSigner.verifySignature(a, payload, first));

        Installation sameIdOtherSecret = new Installation(a.id(), UUID.randomUUID(), "A", 0L, secret(2));
        assertFalse(Arrays.equals(first, KeycardSigner.sign(sameIdOtherSecret, payload)),
                "the tag depends on the installation's secret");
    }

    @Test
    void canonicalBytesAreExactlyTheDocumentedLayout() {
        UUID id = new UUID(0x0102030405060708L, 0x090A0B0C0D0E0F10L);
        KeycardPayload payload = new KeycardPayload(id, AccessLevel.OFFICER, 0x1122334455667788L, true, true,
                0x0000018B2C3D4E5FL);
        ByteBuffer expected = ByteBuffer.allocate(38);
        expected.put(new byte[] {'N', 'S', 'K', '1'});
        expected.put(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        expected.put((byte) 2);       // OFFICER
        expected.put((byte) 0b11);    // encrypted | bound
        expected.put(new byte[] {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88});
        expected.put(new byte[] {0, 0, 1, (byte) 0x8B, 0x2C, 0x3D, 0x4E, 0x5F});
        assertArrayEquals(expected.array(), payload.canonicalBytes());

        byte[] plain = new KeycardPayload(id, AccessLevel.PUBLIC, 1L, false, false, 0L).canonicalBytes();
        assertEquals(38, plain.length);
        assertEquals(0, plain[20], "PUBLIC is level byte 0");
        assertEquals(0, plain[21], "no flags");
        assertEquals(3, new KeycardPayload(id, AccessLevel.OWNER, 1L, false, false, 0L).canonicalBytes()[20],
                "OWNER is the highest encodable level byte");
        assertEquals(1, new KeycardPayload(id, AccessLevel.MEMBER, 1L, true, false, 0L).canonicalBytes()[21]);
        assertEquals(2, new KeycardPayload(id, AccessLevel.MEMBER, 1L, false, true, 0L).canonicalBytes()[21]);
    }

    @Test
    void usesConstantTimeCompare() {
        KeycardPayload payload = new KeycardPayload(a.id(), AccessLevel.MEMBER, 1L, false, false, 0L);
        byte[] tag = KeycardSigner.sign(a, payload);
        byte[] shortTag = Arrays.copyOf(tag, 15);
        byte[] longTag = Arrays.copyOf(tag, 17);
        assertDoesNotThrow(() -> KeycardSigner.verifySignature(a, payload, shortTag));
        assertFalse(KeycardSigner.verifySignature(a, payload, shortTag), "a 15-byte tag is refused");
        assertFalse(KeycardSigner.verifySignature(a, payload, longTag), "a 17-byte tag is refused");
        assertFalse(KeycardSigner.verifySignature(a, payload, new byte[0]));
        assertFalse(KeycardSigner.verifySignature(a, payload, null));
        byte[] flipped = tag.clone();
        flipped[15] ^= 1;
        assertFalse(KeycardSigner.verifySignature(a, payload, flipped), "one flipped bit is refused");
    }
}

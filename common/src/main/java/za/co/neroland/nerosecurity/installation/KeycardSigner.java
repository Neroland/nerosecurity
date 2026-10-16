package za.co.neroland.nerosecurity.installation;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import za.co.neroland.nerosecurity.keycard.KeycardData;
import za.co.neroland.nerosecurity.keycard.KeycardPayload;

/**
 * Signs and verifies keycards with an installation's server-held secret (HMAC-SHA256, truncated to
 * {@link KeycardData#SIGNATURE_BYTES} bytes).
 *
 * <p>It lives in this package because the secret does: this class is the only reader of
 * {@link Installation#secret()} outside the store. The secret never leaves the server, is never
 * logged, and only the truncated tag is ever written to an item. Tag comparison is constant-time.
 */
public final class KeycardSigner {

    static final String ALGORITHM = "HmacSHA256";

    private KeycardSigner() {
    }

    /** The 16-byte tag for {@code payload} under {@code installation}'s secret. */
    public static byte[] sign(Installation installation, KeycardPayload payload) {
        try {
            return Arrays.copyOf(hmac(installation.secret(), payload.canonicalBytes()), KeycardData.SIGNATURE_BYTES);
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is mandatory on every Java platform; this cannot happen on a conforming JVM.
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    /**
     * Whether {@code signature} is the tag for {@code payload} under {@code installation}'s secret.
     * Wrong length or any crypto failure is a plain {@code false}.
     */
    public static boolean verifySignature(Installation installation, KeycardPayload payload, byte[] signature) {
        if (installation == null || payload == null || signature == null
                || signature.length != KeycardData.SIGNATURE_BYTES) {
            return false;
        }
        byte[] expected;
        try {
            expected = Arrays.copyOf(hmac(installation.secret(), payload.canonicalBytes()), KeycardData.SIGNATURE_BYTES);
        } catch (GeneralSecurityException | RuntimeException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, signature);
    }

    /** Test seam: the raw HMAC-SHA256 of {@code data} under {@code secret}. */
    static byte[] hmac(byte[] secret, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(secret, ALGORITHM));
        return mac.doFinal(data);
    }
}

package za.co.neroland.nerosecurity.client.feedback;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

import za.co.neroland.nerosecurity.client.anim.Blink;
import za.co.neroland.nerosecurity.feedback.ClientFeedbackHook;
import za.co.neroland.nerosecurity.sound.SecuritySounds;

/**
 * Client half of the deny/accept feedback. {@link #init()} installs the {@link ClientFeedbackHook} listener: a
 * {@code DENIED} packet plays {@link SecuritySounds#DENY} at the device and flashes it for
 * {@value #FLASH_TICKS} ticks; {@code ACCEPTED} plays {@link SecuritySounds#ACCEPT} and does not flash.
 *
 * <p>Renderers ask {@link #flashing(BlockPos, long)} (is a flash active?) or {@link #flashOn(BlockPos, long, float)}
 * (is the 8 Hz flash in its lit phase this frame?) and draw their LED / plate / ring in {@code LOCK} while it is.
 * Positions of multi-block devices: the server sends the position the player used, so a renderer should check every
 * part it draws for (doors: both lower and upper part positions).
 */
public final class ClientDeviceFeedback {

    /** Length of the deny flash. */
    public static final int FLASH_TICKS = 10;
    private static final int PURGE_THRESHOLD = 64;

    private static final Map<Long, Long> FLASH_UNTIL = new ConcurrentHashMap<>();

    private ClientDeviceFeedback() {
    }

    /** Loader client init entry point: installs the feedback listener. Idempotent. */
    public static void init() {
        ClientFeedbackHook.setListener(ClientDeviceFeedback::onFeedback);
    }

    /** The listener body (client thread). */
    static void onFeedback(BlockPos pos, boolean denied) {
        if (pos == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        SecuritySounds sound = denied ? SecuritySounds.DENY : SecuritySounds.ACCEPT;
        level.playLocalSound(pos, sound.event(), SoundSource.BLOCKS, sound.volume(), sound.pitch(), false);
        if (denied) {
            flash(pos, level.getGameTime(), FLASH_TICKS);
        }
    }

    /** Starts (or extends) a flash at {@code pos} until {@code gameTime + ticks}. */
    public static void flash(BlockPos pos, long gameTime, int ticks) {
        if (pos == null || ticks <= 0) {
            return;
        }
        if (FLASH_UNTIL.size() > PURGE_THRESHOLD) {
            FLASH_UNTIL.values().removeIf(until -> until <= gameTime);
        }
        FLASH_UNTIL.merge(pos.asLong(), gameTime + ticks, Math::max);
    }

    /** Whether a deny flash is active at {@code pos} at {@code gameTime}. */
    public static boolean flashing(BlockPos pos, long gameTime) {
        if (pos == null || FLASH_UNTIL.isEmpty()) {
            return false;
        }
        Long until = FLASH_UNTIL.get(pos.asLong());
        return until != null && gameTime < until;
    }

    /** Whether the deny flash at {@code pos} is in its lit 8 Hz phase this frame (false when not flashing). */
    public static boolean flashOn(BlockPos pos, long gameTime, float partial) {
        return flashing(pos, gameTime) && Blink.deny(gameTime, partial);
    }

    /** Drops every flash (level change, tests). */
    public static void clear() {
        FLASH_UNTIL.clear();
    }
}

package za.co.neroland.nerosecurity.sound;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Every sound NeroSecurity makes, in one table: a vanilla {@link SoundEvent}, a volume and a pitch. There is
 * no {@code sounds.json} and no custom audio in this release; a later art pass swaps the vanilla events for
 * custom ones here and nowhere else.
 *
 * <p>Server-side entries are played with {@link #play(Level, BlockPos)}, which is a no-op on the client
 * (the server's {@code playSound(null, ...)} reaches every nearby client, the acting player included). Three
 * entries are client-only ({@link #DENY}, {@link #ACCEPT}, {@link #FIELD_HUM}): the client renderer and the
 * device-feedback handler play them locally through {@link #event()}, {@link #volume()} and {@link #pitch()}.
 *
 * <p>The events are resolved lazily, so this class can be loaded (and its table tested) without the
 * vanilla registries being bootstrapped.
 */
public enum SecuritySounds {

    DOOR_OPEN(() -> SoundEvents.IRON_DOOR_OPEN, 0.8F, 1.15F),
    DOOR_CLOSE(() -> SoundEvents.IRON_DOOR_CLOSE, 0.8F, 1.15F),
    DOOR_SETTLE(() -> SoundEvents.IRON_TRAPDOOR_CLOSE, 0.35F, 0.7F),
    BLAST_OPEN(() -> SoundEvents.PISTON_EXTEND, 1.0F, 0.55F),
    BLAST_CLOSE(() -> SoundEvents.PISTON_CONTRACT, 1.0F, 0.55F),
    BLAST_SETTLE(() -> SoundEvents.ANVIL_LAND, 0.3F, 1.6F),
    CLAMP(() -> SoundEvents.ANVIL_LAND, 0.4F, 1.4F),
    UNCLAMP(() -> SoundEvents.IRON_TRAPDOOR_OPEN, 0.4F, 0.8F),
    /** Client only: the local player's own refusal (from {@code DeviceFeedback}). */
    DENY(() -> SoundEvents.DISPENSER_FAIL, 0.7F, 0.6F),
    /** Client only: the quiet click of a permitted action (from {@code DeviceFeedback}). */
    ACCEPT(() -> SoundEvents.NOTE_BLOCK_BIT.value(), 0.4F, 1.8F),
    LOCKDOWN_ON(() -> SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.5F),
    LOCKDOWN_OFF(() -> SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.2F),
    ALARM_TONE_A(() -> SoundEvents.BELL_BLOCK, 2.0F, 0.5F),
    ALARM_TONE_B(() -> SoundEvents.BELL_BLOCK, 2.0F, 0.7F),
    LASER_TRIP(() -> SoundEvents.ARROW_HIT_PLAYER, 0.9F, 0.5F),
    CARD_WRITE(() -> SoundEvents.NOTE_BLOCK_PLING.value(), 0.8F, 1.5F),
    CARD_WIPE(() -> SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.8F),
    CHEST_OPEN(() -> SoundEvents.IRON_TRAPDOOR_OPEN, 0.6F, 1.3F),
    CHEST_CLOSE(() -> SoundEvents.IRON_TRAPDOOR_CLOSE, 0.6F, 1.3F),
    /** Client only: the emitter renderer's hum while projecting and the camera is near. */
    FIELD_HUM(() -> SoundEvents.BEACON_AMBIENT, 0.25F, 1.4F),
    FIELD_COLLAPSE(() -> SoundEvents.GLASS_BREAK, 1.0F, 0.6F),
    FIELD_RESTORE(() -> SoundEvents.BEACON_ACTIVATE, 0.8F, 1.3F),
    LINK(() -> SoundEvents.NOTE_BLOCK_BIT.value(), 0.6F, 1.6F);

    private final Supplier<SoundEvent> event;
    private final float volume;
    private final float pitch;

    SecuritySounds(Supplier<SoundEvent> event, float volume, float pitch) {
        this.event = event;
        this.volume = volume;
        this.pitch = pitch;
    }

    /** The vanilla event (resolved on call). */
    public SoundEvent event() {
        return event.get();
    }

    public float volume() {
        return volume;
    }

    public float pitch() {
        return pitch;
    }

    /** Whether the entry is played locally on the client rather than by the server. */
    public boolean clientOnly() {
        return this == DENY || this == ACCEPT || this == FIELD_HUM;
    }

    /**
     * Plays the sound at {@code pos} for everyone nearby (server only; a client level does nothing). Never
     * throws: a missing event or an odd level just means no sound.
     */
    public void play(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) {
            return;
        }
        try {
            serverLevel.playSound(null, pos, event(), SoundSource.BLOCKS, volume, pitch);
        } catch (RuntimeException | LinkageError ignored) {
            // A sound is never worth a crash.
        }
    }
}

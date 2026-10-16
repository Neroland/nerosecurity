package za.co.neroland.nerosecurity.sound;

import org.jetbrains.annotations.Nullable;

/**
 * The alarm's two-tone siren as a pure schedule: while an alarm with the SOUND response is sounding, a tone
 * plays every {@link #PERIOD_TICKS} ticks, alternating {@link SecuritySounds#ALARM_TONE_A} and
 * {@link SecuritySounds#ALARM_TONE_B}, starting with A on the tick it fires.
 */
public final class AlarmTones {

    /** Ticks between two tones. */
    public static final int PERIOD_TICKS = 15;

    private AlarmTones() {
    }

    /**
     * The tone due {@code elapsedTicks} after the alarm started sounding (0 = the firing tick), or null
     * when no tone is due on that tick. Negative input is never due.
     */
    public static @Nullable SecuritySounds toneAt(int elapsedTicks) {
        if (elapsedTicks < 0 || elapsedTicks % PERIOD_TICKS != 0) {
            return null;
        }
        return (elapsedTicks / PERIOD_TICKS) % 2 == 0 ? SecuritySounds.ALARM_TONE_A : SecuritySounds.ALARM_TONE_B;
    }
}

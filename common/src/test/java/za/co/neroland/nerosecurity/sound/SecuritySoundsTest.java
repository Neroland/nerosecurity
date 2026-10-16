package za.co.neroland.nerosecurity.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** The sound table matches the visual-language spec, and the alarm's two-tone schedule alternates. */
class SecuritySoundsTest {

    @Test
    void tableMatchesTheSpecVolumesAndPitches() {
        Map<SecuritySounds, float[]> spec = new LinkedHashMap<>();
        spec.put(SecuritySounds.DOOR_OPEN, new float[] {0.8F, 1.15F});
        spec.put(SecuritySounds.DOOR_CLOSE, new float[] {0.8F, 1.15F});
        spec.put(SecuritySounds.DOOR_SETTLE, new float[] {0.35F, 0.7F});
        spec.put(SecuritySounds.BLAST_OPEN, new float[] {1.0F, 0.55F});
        spec.put(SecuritySounds.BLAST_CLOSE, new float[] {1.0F, 0.55F});
        spec.put(SecuritySounds.BLAST_SETTLE, new float[] {0.3F, 1.6F});
        spec.put(SecuritySounds.CLAMP, new float[] {0.4F, 1.4F});
        spec.put(SecuritySounds.UNCLAMP, new float[] {0.4F, 0.8F});
        spec.put(SecuritySounds.DENY, new float[] {0.7F, 0.6F});
        spec.put(SecuritySounds.ACCEPT, new float[] {0.4F, 1.8F});
        spec.put(SecuritySounds.LOCKDOWN_ON, new float[] {1.0F, 0.5F});
        spec.put(SecuritySounds.LOCKDOWN_OFF, new float[] {1.0F, 1.2F});
        spec.put(SecuritySounds.ALARM_TONE_A, new float[] {2.0F, 0.5F});
        spec.put(SecuritySounds.ALARM_TONE_B, new float[] {2.0F, 0.7F});
        spec.put(SecuritySounds.LASER_TRIP, new float[] {0.9F, 0.5F});
        spec.put(SecuritySounds.CARD_WRITE, new float[] {0.8F, 1.5F});
        spec.put(SecuritySounds.CARD_WIPE, new float[] {0.6F, 0.8F});
        spec.put(SecuritySounds.CHEST_OPEN, new float[] {0.6F, 1.3F});
        spec.put(SecuritySounds.CHEST_CLOSE, new float[] {0.6F, 1.3F});
        spec.put(SecuritySounds.FIELD_HUM, new float[] {0.25F, 1.4F});
        spec.put(SecuritySounds.FIELD_COLLAPSE, new float[] {1.0F, 0.6F});
        spec.put(SecuritySounds.FIELD_RESTORE, new float[] {0.8F, 1.3F});
        spec.put(SecuritySounds.LINK, new float[] {0.6F, 1.6F});
        assertEquals(EnumSet.allOf(SecuritySounds.class), EnumSet.copyOf(spec.keySet()), "every entry is specified");
        for (Map.Entry<SecuritySounds, float[]> entry : spec.entrySet()) {
            assertEquals(entry.getValue()[0], entry.getKey().volume(), 1.0E-6F, entry.getKey() + " volume");
            assertEquals(entry.getValue()[1], entry.getKey().pitch(), 1.0E-6F, entry.getKey() + " pitch");
        }
    }

    @Test
    void onlyTheLocalFeedbackAndTheHumAreClientSide() {
        EnumSet<SecuritySounds> client = EnumSet.noneOf(SecuritySounds.class);
        for (SecuritySounds sound : SecuritySounds.values()) {
            if (sound.clientOnly()) {
                client.add(sound);
            }
        }
        assertEquals(EnumSet.of(SecuritySounds.DENY, SecuritySounds.ACCEPT, SecuritySounds.FIELD_HUM), client);
    }

    @Test
    void alarmTonesAlternateEveryPeriod() {
        assertEquals(SecuritySounds.ALARM_TONE_A, AlarmTones.toneAt(0), "tone A on the firing tick");
        assertEquals(SecuritySounds.ALARM_TONE_B, AlarmTones.toneAt(AlarmTones.PERIOD_TICKS));
        assertEquals(SecuritySounds.ALARM_TONE_A, AlarmTones.toneAt(2 * AlarmTones.PERIOD_TICKS));
        int tones = 0;
        for (int t = 0; t < 10 * AlarmTones.PERIOD_TICKS; t++) {
            if (AlarmTones.toneAt(t) != null) {
                tones++;
                assertEquals(0, t % AlarmTones.PERIOD_TICKS, "a tone only on period boundaries");
            }
        }
        assertEquals(10, tones);
        assertNull(AlarmTones.toneAt(1));
        assertNull(AlarmTones.toneAt(-AlarmTones.PERIOD_TICKS), "never before it started");
        assertTrue(AlarmTones.PERIOD_TICKS == 15, "the spec's 15-tick cadence");
    }
}

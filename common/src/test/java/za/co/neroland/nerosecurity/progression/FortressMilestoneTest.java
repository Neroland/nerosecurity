package za.co.neroland.nerosecurity.progression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

/** The fortress milestone predicate: hub, four cameras, a force field emitter and a blast door. */
class FortressMilestoneTest {

    private static Identifier sec(String path) {
        return Identifier.fromNamespaceAndPath("nerosecurity", path);
    }

    private static List<Identifier> full() {
        List<Identifier> blocks = new ArrayList<>();
        for (int i = 0; i < SecurityMilestones.FORTRESS_MIN_CAMERAS; i++) {
            blocks.add(sec("security_camera"));
        }
        blocks.add(sec("force_field_emitter"));
        blocks.add(sec("blast_door"));
        return blocks;
    }

    @Test
    void fullInstallationWithHubIsAFortress() {
        assertTrue(SecurityMilestones.isFortress(true, full()));
    }

    @Test
    void noHubIsNeverAFortress() {
        assertFalse(SecurityMilestones.isFortress(false, full()));
    }

    @Test
    void eachMissingPieceBlocksIt() {
        List<Identifier> fewCameras = full();
        fewCameras.remove(sec("security_camera"));
        assertFalse(SecurityMilestones.isFortress(true, fewCameras));
        List<Identifier> noField = full();
        noField.remove(sec("force_field_emitter"));
        assertFalse(SecurityMilestones.isFortress(true, noField));
        List<Identifier> noDoor = full();
        noDoor.remove(sec("blast_door"));
        assertFalse(SecurityMilestones.isFortress(true, noDoor));
    }

    @Test
    void foreignNamespacesDoNotCount() {
        List<Identifier> blocks = full();
        blocks.remove(sec("blast_door"));
        blocks.add(Identifier.fromNamespaceAndPath("othermod", "blast_door"));
        assertFalse(SecurityMilestones.isFortress(true, blocks));
    }
}

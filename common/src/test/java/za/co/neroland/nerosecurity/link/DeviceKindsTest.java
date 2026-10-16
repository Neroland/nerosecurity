package za.co.neroland.nerosecurity.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;

class DeviceKindsTest {

    @Test
    void countsAndOrdinalLabelsAreStable() {
        FakeLinkContext ctx = new FakeLinkContext();
        Installation i = ctx.install(UUID.randomUUID(), "Lab", 0);
        DevicePos d1 = ctx.link(i, "security_door", 1, 64, 0);
        DevicePos l1 = ctx.link(i, "laser_grid_node", 2, 64, 0);
        DevicePos b1 = ctx.link(i, "blast_door", 3, 64, 0);
        DevicePos l2 = ctx.link(i, "laser_grid_node", 4, 64, 0);
        ctx.link(i, "secure_chest", 5, 64, 0);
        ctx.link(i, "alarm", 6, 64, 0);
        ctx.link(i, "security_camera", 7, 64, 0);
        ctx.link(i, "force_field_emitter", 8, 64, 0);
        ctx.link(i, "security_monitor", 9, 64, 0);
        Installation now = ctx.fresh(i);

        JsonObject counts = DeviceKinds.counts(now.devices());
        assertEquals(2, counts.get("doors").getAsInt());
        assertEquals(1, counts.get("fields").getAsInt());
        assertEquals(2, counts.get("lasers").getAsInt());
        assertEquals(1, counts.get("cameras").getAsInt());
        assertEquals(1, counts.get("alarms").getAsInt());
        assertEquals(1, counts.get("chests").getAsInt());
        assertEquals(1, counts.get("other").getAsInt());
        assertEquals(9, counts.get("total").getAsInt());

        for (int round = 0; round < 3; round++) {
            assertEquals("Door 1", DeviceKinds.label(now.devices(), d1).text());
            assertEquals("Laser grid 1", DeviceKinds.label(now.devices(), l1).text());
            assertEquals("Laser grid 2", DeviceKinds.label(now.devices(), l2).text());
            assertEquals("Blast door 1", DeviceKinds.label(now.devices(), b1).text());
            assertEquals("laser_grid_node", DeviceKinds.label(now.devices(), l2).kind());
        }
        assertNull(DeviceKinds.label(now.devices(), null));
        assertNull(DeviceKinds.label(now.devices(), new DevicePos(FakeLinkContext.OVERWORLD,
                new net.minecraft.core.BlockPos(99, 64, 0))));
        assertEquals(DeviceKinds.OTHER, DeviceKinds.kind(Identifier.fromNamespaceAndPath("minecraft", "chest")));
        assertEquals(DeviceKinds.OTHER, DeviceKinds.kind(Identifier.fromNamespaceAndPath("nerosecurity", "force_field")));
        assertEquals("Device 3", DeviceKinds.labelText("whatever", 3));
    }
}

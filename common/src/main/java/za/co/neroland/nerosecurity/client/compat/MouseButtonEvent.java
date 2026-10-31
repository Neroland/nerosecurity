package za.co.neroland.nerosecurity.client.compat;

// Minecraft before 1.21.9 has no input-event records: widgets and screens get raw (x, y, button) and
// (key, scancode, modifiers) arguments. Below 1.21.9 the build points the vanilla input-event imports at
// these look-alikes, so the GUI code stays one copy. Empty on 1.21.9 and later.
//? if <1.21.9 {
/*import net.minecraft.client.gui.screens.Screen;

public record MouseButtonEvent(double x, double y, int button) {

    public boolean hasShiftDown() {
        return Screen.hasShiftDown();
    }
}
*///?}

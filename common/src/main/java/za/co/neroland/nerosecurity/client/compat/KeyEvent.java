package za.co.neroland.nerosecurity.client.compat;

// Look-alike of the 1.21.9+ key event for older Minecraft lines (see MouseButtonEvent). Empty on 1.21.9 and later.
//? if <1.21.9 {
/*import net.minecraft.client.gui.navigation.CommonInputs;

import org.lwjgl.glfw.GLFW;

public record KeyEvent(int key, int scancode, int modifiers) {

    public boolean isSelection() {
        return CommonInputs.selected(key);
    }

    public boolean isEscape() {
        return key == GLFW.GLFW_KEY_ESCAPE;
    }

    public boolean isCycleFocus() {
        return key == GLFW.GLFW_KEY_TAB;
    }

    public boolean isLeft() {
        return key == GLFW.GLFW_KEY_LEFT;
    }

    public boolean isRight() {
        return key == GLFW.GLFW_KEY_RIGHT;
    }

    public boolean hasShiftDown() {
        return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }
}
*///?}

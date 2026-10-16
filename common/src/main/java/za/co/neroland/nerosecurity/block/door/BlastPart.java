package za.co.neroland.nerosecurity.block.door;

import net.minecraft.util.StringRepresentable;

/**
 * The four parts of a 2x2 Blast Door, named as seen by someone looking at the door's front.
 * {@link #BOTTOM_LEFT} is the controller and holds the block entity; the right-hand column lies in the
 * {@code facing.getCounterClockWise()} direction from it.
 */
public enum BlastPart implements StringRepresentable {
    BOTTOM_LEFT("bottom_left", false, false),
    BOTTOM_RIGHT("bottom_right", false, true),
    TOP_LEFT("top_left", true, false),
    TOP_RIGHT("top_right", true, true);

    private final String name;
    public final boolean top;
    public final boolean right;

    BlastPart(String name, boolean top, boolean right) {
        this.name = name;
        this.top = top;
        this.right = right;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

package za.co.neroland.nerosecurity.block.door;

import net.minecraft.util.StringRepresentable;

/** The two parts of a 1x2 Security Door. {@link #LOWER} is the controller and holds the block entity. */
public enum DoorPart implements StringRepresentable {
    LOWER("lower"),
    UPPER("upper");

    private final String name;

    DoorPart(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

package za.co.neroland.nerosecurity.data.compat;

// Minecraft 1.21.1 - 1.21.5 only (empty on every other line). Write-side twin of ValueInput: wraps the CompoundTag
// plus registry lookup a pre-1.21.6 block entity saves into, with the subset of the 1.21.6+ ValueOutput API
// NeroSecurity uses. The build renames the ValueOutput import to this class on those lines.
//? if <1.21.6 {
/*import com.mojang.serialization.Codec;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

public final class ValueOutput {

    private final CompoundTag tag;
    private final HolderLookup.Provider registries;

    private ValueOutput(CompoundTag tag, HolderLookup.Provider registries) {
        this.tag = tag;
        this.registries = registries;
    }

    public static ValueOutput of(CompoundTag tag, HolderLookup.Provider registries) {
        return new ValueOutput(tag, registries);
    }

    public CompoundTag tag() {
        return this.tag;
    }

    public HolderLookup.Provider registries() {
        return this.registries;
    }

    public void putInt(String key, int value) {
        this.tag.putInt(key, value);
    }

    public void putLong(String key, long value) {
        this.tag.putLong(key, value);
    }

    public void putFloat(String key, float value) {
        this.tag.putFloat(key, value);
    }

    public void putDouble(String key, double value) {
        this.tag.putDouble(key, value);
    }

    public void putBoolean(String key, boolean value) {
        this.tag.putBoolean(key, value);
    }

    public void putString(String key, String value) {
        this.tag.putString(key, value);
    }

    public ValueOutput child(String key) {
        CompoundTag child = new CompoundTag();
        this.tag.put(key, child);
        return new ValueOutput(child, this.registries);
    }

    public <T> void store(String key, Codec<T> codec, T value) {
        codec.encodeStart(this.registries.createSerializationContext(NbtOps.INSTANCE), value)
                .result().ifPresent(encoded -> this.tag.put(key, encoded));
    }
}
*///?}

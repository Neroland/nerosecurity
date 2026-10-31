package za.co.neroland.nerosecurity.block.field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

/**
 * The force field block's tiny block entity: it holds only the position of the emitter that projected it, so
 * collision can ask that emitter's verdict and an orphan can tell it has no emitter. No ticker, nothing else stored.
 * The emitter position is the one thing in the update tag (a client needs it to look up its own passability).
 */
public class ForceFieldBlockEntity extends BlockEntity {

    private static final String KEY_HAS_EMITTER = "HasEmitter";
    private static final String KEY_EMITTER = "Emitter";

    private @Nullable BlockPos emitterPos;

    public ForceFieldBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The emitter that owns this block, or null before the projector wrote it (treated as solid). */
    public @Nullable BlockPos emitterPos() {
        return emitterPos;
    }

    /** Written once by the projector right after placing the block; pushed to clients at once. */
    public void setEmitterPos(BlockPos pos) {
        this.emitterPos = pos == null ? null : pos.immutable();
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ValueOutput output = ValueOutput.of(tag, registries);
    *///?}
        output.putBoolean(KEY_HAS_EMITTER, emitterPos != null);
        if (emitterPos != null) {
            output.putLong(KEY_EMITTER, emitterPos.asLong());
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    //?} else {
    /*@Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ValueInput input = ValueInput.of(tag, registries);
    *///?}
        this.emitterPos = input.getBooleanOr(KEY_HAS_EMITTER, false)
                ? BlockPos.of(input.getLongOr(KEY_EMITTER, 0L)) : null;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Only the emitter position. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_HAS_EMITTER, emitterPos != null);
        if (emitterPos != null) {
            tag.putLong(KEY_EMITTER, emitterPos.asLong());
        }
        return tag;
    }
}

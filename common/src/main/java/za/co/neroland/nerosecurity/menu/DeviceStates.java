package za.co.neroland.nerosecurity.menu;

import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.block.alarm.AlarmBlock;
import za.co.neroland.nerosecurity.block.alarm.AlarmBlockEntity;
import za.co.neroland.nerosecurity.block.door.AbstractSecurityDoorBlock;
import za.co.neroland.nerosecurity.block.door.DoorRules;
import za.co.neroland.nerosecurity.block.door.SecurityDoorBlockEntity;
import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlockEntity;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.field.FieldState;
import za.co.neroland.nerosecurity.sensor.SensorHost;

/**
 * One status word per linked device, for the hub's DEVICES list. Computed on the server from the loaded block
 * entity only (an unloaded chunk is never loaded for this: it reads {@link DeviceState#UNLOADED}). Every rule
 * below {@link #of} is pure, so the mapping is tested in a plain JVM. A state is about the device, never about
 * a person.
 */
public final class DeviceStates {

    /** What a linked device is doing, as far as the hub can tell. */
    public enum DeviceState {
        /** Linked and working (a powered door, an armed sensor, a projecting field, any unpowered device). */
        OK,
        /** Not enough energy to do its job. */
        UNPOWERED,
        /** A powered laser node with no facing partner in range. */
        UNPAIRED,
        /** A force field emitter that is switched off or not projecting. */
        IDLE,
        /** Its chunk is not loaded (or the block there is no longer the device). */
        UNLOADED,
        /** A door clamped shut by lockdown. */
        SEALED,
        /** An alarm that is going off right now. */
        ACTIVE,
        /** A force field emitter that collapsed under load and is cooling down. */
        OVERLOADED;

        /** {@code "device.nerosecurity.state.<lower_name>"}. */
        public String translationKey() {
            return "device.nerosecurity.state." + name().toLowerCase(Locale.ROOT);
        }
    }

    private DeviceStates() {
    }

    /** The state of the device at {@code pos} in {@code level} (server thread). Null level = unloaded. */
    public static DeviceState of(@Nullable ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return DeviceState.UNLOADED;
        }
        BlockEntity be = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);
        if (be instanceof SecurityDoorBlockEntity door) {
            boolean sealed = state.hasProperty(AbstractSecurityDoorBlock.SEALED)
                    && state.getValue(AbstractSecurityDoorBlock.SEALED);
            return door(sealed, DoorRules.powerLow(true, NeroSecurityConfig.doorsRequirePower(),
                    door.getEnergy().getAmount(), NeroSecurityConfig.doorOpenCost()));
        }
        if (be instanceof SensorHost sensor) {
            long perTick = sensor.isLaser() ? NeroSecurityConfig.laserPowerPerTick()
                    : NeroSecurityConfig.cameraPowerPerTick();
            return sensor(sensor.isLaser(), sensor.armed(), sensor.energyAmount() >= Math.max(1L, perTick));
        }
        if (be instanceof AlarmBlockEntity) {
            return alarm(state.hasProperty(AlarmBlock.ACTIVE) && state.getValue(AlarmBlock.ACTIVE));
        }
        if (be instanceof ForceFieldEmitterBlockEntity emitter) {
            return emitter(emitter.fieldState());
        }
        return be == null ? DeviceState.UNLOADED : DeviceState.OK;
    }

    // --- pure rules -------------------------------------------------------------------------------

    /** A door: sealed wins, then power. */
    public static DeviceState door(boolean sealed, boolean powerLow) {
        if (sealed) {
            return DeviceState.SEALED;
        }
        return powerLow ? DeviceState.UNPOWERED : DeviceState.OK;
    }

    /** A sensor: armed is OK; a powered laser that is not armed has no partner; anything else lacks power. */
    public static DeviceState sensor(boolean laser, boolean armed, boolean powered) {
        if (armed) {
            return DeviceState.OK;
        }
        return laser && powered ? DeviceState.UNPAIRED : DeviceState.UNPOWERED;
    }

    /** An alarm: going off, or standing by. */
    public static DeviceState alarm(boolean active) {
        return active ? DeviceState.ACTIVE : DeviceState.OK;
    }

    /** A force field emitter, from its state machine. */
    public static DeviceState emitter(@Nullable FieldState state) {
        if (state == null) {
            return DeviceState.IDLE;
        }
        return switch (state) {
            case PROJECTING -> DeviceState.OK;
            case BROWNOUT -> DeviceState.UNPOWERED;
            case OVERLOADED -> DeviceState.OVERLOADED;
            case IDLE -> DeviceState.IDLE;
        };
    }
}

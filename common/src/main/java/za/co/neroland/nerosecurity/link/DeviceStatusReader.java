package za.co.neroland.nerosecurity.link;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerosecurity.block.alarm.AlarmBlock;
import za.co.neroland.nerosecurity.block.alarm.AlarmBlockEntity;
import za.co.neroland.nerosecurity.block.camera.SecurityCameraBlockEntity;
import za.co.neroland.nerosecurity.block.chest.SecureChestBlockEntity;
import za.co.neroland.nerosecurity.block.door.SecurityDoorBlockEntity;
import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlockEntity;
import za.co.neroland.nerosecurity.block.laser.LaserGridNodeBlockEntity;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * The production {@link DeviceStatusSource}: reads the device's block entity through its existing public
 * accessors, only where {@code level.isLoaded(pos)} (never a chunk load). Reports device facts only, never
 * who is near a device. Built per snapshot (it caches the level map). Server thread only.
 */
final class DeviceStatusReader implements DeviceStatusSource {

    /** How recently (game ticks) a laser must have tripped to be reported as tripped. */
    static final long TRIPPED_TICKS = 200L;

    private final Map<Identifier, ServerLevel> levels = new HashMap<>();

    DeviceStatusReader(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            levels.put(level.dimension().identifier(), level);
        }
    }

    @Override
    public Status status(Installation installation, DevicePos pos, Identifier blockId, long now) {
        ServerLevel level = levels.get(pos.dimension());
        if (level == null || !level.isLoaded(pos.pos())) {
            return Status.UNLOADED;
        }
        BlockEntity be = level.getBlockEntity(pos.pos());
        if (be == null || be.isRemoved()) {
            return Status.of(null, null);
        }
        boolean sealed = NeroSecurityConfig.isLockdownEnabled() && installation.lockdown().active();
        if (be instanceof SecurityDoorBlockEntity door) {
            boolean powered = door.hasPowerToOpen();
            String state;
            if (door.opening() || door.openTicks() > 0) {
                state = "open";
            } else if (sealed) {
                state = "sealed";
            } else if (!powered) {
                state = "unpowered";
            } else {
                state = "closed";
            }
            return Status.of(powered, state);
        }
        if (be instanceof SecureChestBlockEntity chest) {
            return Status.of(null, chest.isOpenForRender() ? "open" : "locked");
        }
        if (be instanceof LaserGridNodeBlockEntity laser) {
            boolean powered = laser.energyAmount() > 0;
            String state = !laser.paired() ? "unpaired" : laser.armed() ? "armed" : !powered ? "unpowered" : "disarmed";
            long trippedAt = laser.lastTripGameTime();
            boolean tripped = trippedAt != Long.MIN_VALUE && level.getGameTime() - trippedAt <= TRIPPED_TICKS;
            return new Status(true, powered, state, tripped, 0L);
        }
        if (be instanceof SecurityCameraBlockEntity camera) {
            boolean powered = camera.energyAmount() > 0;
            return Status.of(powered, camera.armed() ? "armed" : !powered ? "unpowered" : "disarmed");
        }
        if (be instanceof AlarmBlockEntity alarm) {
            BlockState state = alarm.getBlockState();
            boolean active = state.hasProperty(AlarmBlock.ACTIVE) && state.getValue(AlarmBlock.ACTIVE);
            if (active) {
                return Status.of(null, "active");
            }
            if (alarm.armed(installation, now)) {
                return Status.of(null, "armed");
            }
            long until = Math.max(alarm.silencedUntil(), installation.alarmSilencedUntil());
            return new Status(true, null, "silenced", false, until);
        }
        if (be instanceof ForceFieldEmitterBlockEntity emitter) {
            boolean powered = emitter.energyAmount() > 0;
            String state = switch (emitter.fieldState()) {
                case PROJECTING -> "projecting";
                case OVERLOADED -> "cooldown";
                case BROWNOUT -> "collapsed";
                default -> powered ? "off" : "unpowered";
            };
            return Status.of(powered, state);
        }
        return Status.of(null, null);
    }
}

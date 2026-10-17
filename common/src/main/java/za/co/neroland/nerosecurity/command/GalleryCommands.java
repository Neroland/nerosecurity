package za.co.neroland.nerosecurity.command;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.EnergyBuffer;
import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.alarm.AlarmFiring;
import za.co.neroland.nerosecurity.block.DeviceAccessSettings;
import za.co.neroland.nerosecurity.block.KeycardProgrammerBlock;
import za.co.neroland.nerosecurity.block.SecurityHubBlock;
import za.co.neroland.nerosecurity.block.alarm.AlarmBlockEntity;
import za.co.neroland.nerosecurity.block.alarm.AlarmResponse;
import za.co.neroland.nerosecurity.block.alarm.AlarmRules;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.block.camera.SecurityCameraBlock;
import za.co.neroland.nerosecurity.block.camera.SecurityCameraBlockEntity;
import za.co.neroland.nerosecurity.block.chest.SecureChestBlock;
import za.co.neroland.nerosecurity.block.chest.SecureChestBlockEntity;
import za.co.neroland.nerosecurity.block.door.AbstractSecurityDoorBlock;
import za.co.neroland.nerosecurity.block.door.BlastDoorBlock;
import za.co.neroland.nerosecurity.block.door.BlastPart;
import za.co.neroland.nerosecurity.block.door.DoorPart;
import za.co.neroland.nerosecurity.block.door.RedstoneMode;
import za.co.neroland.nerosecurity.block.door.SecurityDoorBlock;
import za.co.neroland.nerosecurity.block.door.SecurityDoorBlockEntity;
import za.co.neroland.nerosecurity.block.entity.KeycardProgrammerBlockEntity;
import za.co.neroland.nerosecurity.block.entity.SecurityHubBlockEntity;
import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlock;
import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlockEntity;
import za.co.neroland.nerosecurity.block.laser.LaserGridNodeBlock;
import za.co.neroland.nerosecurity.block.laser.LaserGridNodeBlockEntity;
import za.co.neroland.nerosecurity.block.monitor.SecurityMonitorBlock;
import za.co.neroland.nerosecurity.command.GalleryLayout.Box;
import za.co.neroland.nerosecurity.command.GalleryLayout.Offset;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.event.SecuritySeverity;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationNames;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.installation.ServiceResult;
import za.co.neroland.nerosecurity.keycard.KeycardService;
import za.co.neroland.nerosecurity.notice.InstallationAreas;
import za.co.neroland.nerosecurity.registry.AlarmRegistrations;
import za.co.neroland.nerosecurity.registry.CameraRegistrations;
import za.co.neroland.nerosecurity.registry.ChestRegistrations;
import za.co.neroland.nerosecurity.registry.DoorRegistrations;
import za.co.neroland.nerosecurity.registry.FieldRegistrations;
import za.co.neroland.nerosecurity.registry.LaserRegistrations;
import za.co.neroland.nerosecurity.registry.MonitorRegistrations;
import za.co.neroland.nerosecurity.registry.NeroSecurityBlocks;
import za.co.neroland.nerosecurity.registry.NeroSecurityItems;
import za.co.neroland.nerosecurity.registry.ProgrammerRegistrations;
import za.co.neroland.nerosecurity.sensor.SensorEntityMode;
import za.co.neroland.nerosecurity.sensor.SensorSettings;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * The creative showcase: {@code /nerosecurity gallery} builds a lit, symmetric exhibit of every NeroSecurity
 * block north of the invoking operator, and {@code /nerosecurity gallery clear} takes it down again.
 * Operator level 2, creative mode and a player are all required; the floor plan is {@link GalleryLayout}.
 *
 * <p><b>What it shows.</b> A Security Hub on a plinth owning a demo installation named
 * {@value GalleryLayout#INSTALLATION_NAME}, with every other block linked to it: the keycard programmer (an
 * encryption module fitted) and the monitor flanking the hub, the secure chest in front, an alarm on a post
 * behind it lit in its active state, a door wall with a closed security door, an OFFICER keycard-only blast
 * door and a security door held open by a lever, a camera over the plaza, a west pavilion with a force-field
 * wall and a floating force-field box, an east pavilion with three armed laser beams and a camera at the end
 * of the corridor, a keycard rack (a card of every level, an encrypted card and a blank) and an item rack
 * (the linking tool and the five modules). Powered devices stand on, or in front of, hidden Neroland Core
 * Creative Batteries and start with full buffers, so every gauge and LED reads live on arrival.
 *
 * <p><b>The access-denied bay.</b> At the north end, behind the pavilions, a second installation named
 * {@value GalleryLayout#DENIED_INSTALLATION_NAME} shows the system refusing someone. It is owned by the
 * tombstone owner (nobody), so no player holds membership in it: its MEMBER secure chest, its keycard-only
 * door and its force field refuse every player who is not an operator, with the deny sound, the device
 * flash and the action-bar reason, and its hub screen and monitor are closed to them too. Because an operator
 * (the builder included) is always let through by the resolver's override, the bay also shows denial through
 * a <em>visitor</em>: a passive, no-AI, silent, invulnerable villager penned behind the field with two laser
 * beams across it and a camera over it, both set to "every living thing" at PUBLIC, so they trip on the
 * visitor every cooldown and never on a player. Each trip fires the bay's alarm (light and a redstone lamp,
 * no siren, no chat, no lockdown) and appears on the bay's monitor as an anonymous event. A three-slot rack
 * holds the bay's own MEMBER card (opens the door), a Gallery MEMBER card (foreign here: refused) and the
 * bay's OFFICER card (foreign at the Gallery's blast door: refused there).
 *
 * <p><b>What it does not do.</b> It never enters a lockdown (a sealed door needs one, so none is shown),
 * never changes {@code laserTripDamage} (the visitor is invulnerable whatever it is set to), and sets every
 * sensor in the main exhibit to PUBLIC and players-only: the beams draw and the cameras sweep, but they trip,
 * hurt and record nobody. The bay's sensors are PUBLIC too, so they record no player either; only the visitor
 * trips them. The main alarm's only response is its light; the bay's alarm adds the redstone pulse.
 *
 * <p><b>Clearing.</b> {@code gallery clear}, run where {@code gallery} was run, unlinks every device and
 * dissolves every installation whose hub stands inside the footprint (the two demo installations, and any
 * hub an operator placed inside the gallery while trying it) through the normal service path (each audit
 * log goes with its installation, exactly as when an owner breaks a hub), then wipes the footprint and
 * removes only the entities the gallery spawned (they carry the {@value GalleryLayout#ENTITY_TAG} tag: label
 * stands, item frames, the visitor). {@code gallery} clears first, so a rebuild never stacks duplicates.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> the command acts only at the invoking operator's position and stores
 * nothing of its own. The only records written are the ones ordinary play writes (installation created,
 * devices linked, keycards written, all with the operator as actor), and every one of them lives inside one
 * of the demo installations and is dropped with it on clear. The visitor is never a player, so the rows its
 * trips produce carry no actor at all; a player refused by the bay's door or chest is logged there the way
 * any installation logs a refusal, and that log is dropped on clear too. Logs carry the subcommand name
 * only: never a name, UUID or position.
 */
public final class GalleryCommands {

    private GalleryCommands() {
    }

    /** Adds {@code gallery} and {@code gallery clear} under the shared {@code /nerosecurity} root. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nerosecurity")
                .then(Commands.literal("gallery")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> runSafely(ctx.getSource(), "gallery", () -> build(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> runSafely(ctx.getSource(), "gallery clear",
                                        () -> clear(ctx.getSource()))))));
    }

    private interface CommandBody {
        int run();
    }

    /** Catches a failing subcommand, reports it by subcommand name only, and tells the invoker. */
    private static int runSafely(CommandSourceStack source, String subcommand, CommandBody body) {
        try {
            return body.run();
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityTelemetry.captureHandledException(e, "command", "/nerosecurity " + subcommand);
            NeroSecurityCommon.LOGGER.error("[NeroSecurity] /nerosecurity {} failed ({})", subcommand,
                    e.getClass().getName());
            source.sendFailure(Component.translatable("command.nerosecurity.failed", subcommand));
            return 0;
        }
    }

    /** A creative-mode player, or null after the failure message. */
    private static @Nullable ServerPlayer requireCreativePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerosecurity.players_only"));
            return null;
        }
        if (!player.getAbilities().instabuild) {
            source.sendFailure(Component.translatable("command.nerosecurity.gallery.creative_only"));
            return null;
        }
        return player;
    }

    // --- build ----------------------------------------------------------------------------------------

    private static int build(CommandSourceStack source) {
        ServerPlayer player = requireCreativePlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();

        // 1. Never stack: a previous gallery at this origin comes down first, installation included.
        if (hasGalleryAt(level, origin)) {
            clearAt(source, player, level, origin);
        }

        // 2. The shell: floor, walls, racks, lights. Plain blocks only; nothing here is linked.
        buildFloor(level, origin);
        buildDoorWall(level, origin);
        buildPavilions(level, origin);
        buildDeniedBay(level, origin);
        buildRacks(level, origin);
        buildLighting(level, origin);

        // 3. The hub and its installation, through the same service path a placed hub uses.
        BlockPos hubPos = GalleryLayout.HUB.at(origin);
        level.setBlockAndUpdate(hubPos, NeroSecurityBlocks.SECURITY_HUB.get().defaultBlockState()
                .setValue(SecurityHubBlock.FACING, Direction.SOUTH));
        if (!(level.getBlockEntity(hubPos) instanceof SecurityHubBlockEntity hub)) {
            source.sendFailure(Component.translatable("command.nerosecurity.gallery.hub_failed",
                    Component.translatable("hub.nerosecurity.inert")));
            return 0;
        }
        InstallationService.CreateOutcome outcome = InstallationService.createForHub(player, level, hubPos);
        if (outcome.installation() == null) {
            source.sendFailure(Component.translatable("command.nerosecurity.gallery.hub_failed",
                    outcome.result().message()));
            return 0;
        }
        UUID installationId = outcome.installation().id();
        hub.bind(installationId);
        hub.refreshVisual(level);
        InstallationService.rename(player, outcome.installation(), GalleryLayout.INSTALLATION_NAME);
        label(level, GalleryLayout.HUB.plus(0, 2, 0).at(origin), "Security Hub — owns the \"Gallery\" installation");

        Linker linker = new Linker(source, player, level, installationId);

        // 4. The plaza: programmer (module fitted), monitor, secure chest, alarm on its post.
        BlockPos programmerPos = GalleryLayout.PROGRAMMER.at(origin);
        level.setBlockAndUpdate(programmerPos, ProgrammerRegistrations.BLOCK.get().defaultBlockState()
                .setValue(KeycardProgrammerBlock.FACING, Direction.SOUTH));
        linker.link(programmerPos);
        if (level.getBlockEntity(programmerPos) instanceof KeycardProgrammerBlockEntity programmer) {
            programmer.container().setItem(KeycardProgrammerBlockEntity.SLOT_MODULE,
                    new ItemStack(NeroSecurityItems.ENCRYPTION_MODULE.get()));
        }
        label(level, programmerPos.above(2), "Keycard Programmer — Encryption Module fitted");

        BlockPos monitorPos = GalleryLayout.MONITOR.at(origin);
        level.setBlockAndUpdate(monitorPos, MonitorRegistrations.BLOCK.get().defaultBlockState()
                .setValue(SecurityMonitorBlock.FACING, Direction.SOUTH));
        linker.link(monitorPos);
        label(level, monitorPos.above(2), "Security Monitor — the installation's live event feed");

        BlockPos chestPos = GalleryLayout.CHEST.at(origin);
        level.setBlockAndUpdate(chestPos, ChestRegistrations.SECURE_CHEST.get().defaultBlockState()
                .setValue(SecureChestBlock.FACING, Direction.SOUTH));
        linker.link(chestPos);
        if (level.getBlockEntity(chestPos) instanceof SecureChestBlockEntity chest) {
            chest.applySettings(new DeviceAccessSettings(AccessLevel.MEMBER, false));
        }
        label(level, chestPos.above(2), "Secure Chest — MEMBER to open; automation policy per installation");

        BlockPos alarmPos = GalleryLayout.ALARM.at(origin);
        level.setBlockAndUpdate(alarmPos, AlarmRegistrations.ALARM.get().defaultBlockState());
        linker.link(alarmPos);
        label(level, alarmPos.above(2), "Alarm — light response only here; shown in its active state");

        // 5. The door wall: closed MEMBER door, OFFICER keycard-only blast door, lever-held open door.
        BlockPos closedDoor = GalleryLayout.SECURITY_DOOR_CLOSED.at(origin);
        placeSecurityDoor(level, closedDoor, Direction.SOUTH);
        powerBelow(level, closedDoor);
        linker.link(closedDoor);
        chargeMachine(level, closedDoor);
        label(level, closedDoor.above(3), "Security Door — closed, MEMBER and above");

        BlockPos blastDoor = GalleryLayout.BLAST_DOOR.at(origin);
        placeBlastDoor(level, blastDoor, Direction.SOUTH);
        powerBelow(level, blastDoor);
        linker.link(blastDoor);
        chargeMachine(level, blastDoor);
        if (level.getBlockEntity(blastDoor) instanceof SecurityDoorBlockEntity door) {
            door.applySettings(new DeviceAccessSettings(AccessLevel.OFFICER, true), RedstoneMode.IGNORE);
        }
        label(level, blastDoor.offset(0, 3, 0), "Blast Door — locked: OFFICER keycard only");

        BlockPos openDoor = GalleryLayout.SECURITY_DOOR_OPEN.at(origin);
        placeSecurityDoor(level, openDoor, Direction.SOUTH);
        powerBelow(level, openDoor);
        linker.link(openDoor);
        chargeMachine(level, openDoor);
        if (level.getBlockEntity(openDoor) instanceof SecurityDoorBlockEntity door) {
            door.applySettings(DeviceAccessSettings.DEFAULT, RedstoneMode.OPEN_WHILE_POWERED);
        }
        level.setBlockAndUpdate(GalleryLayout.DOOR_LEVER.at(origin), Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.SOUTH)
                .setValue(LeverBlock.POWERED, true));
        label(level, openDoor.above(3), "Security Door — held open by redstone (flip the lever)");

        placeCamera(level, linker, GalleryLayout.PLAZA_CAMERA.at(origin), Direction.SOUTH,
                "Security Camera — armed; watching the plaza, recording nobody");

        // 6. West pavilion: the force-field wall and the floating force-field box.
        placeEmitter(level, linker, GalleryLayout.FIELD_WALL_EMITTER.at(origin), GalleryLayout.FIELD_WALL_FACING,
                GalleryLayout.FIELD_WALL_SETTINGS, "Force Field Emitter — 5×5 wall, two blocks out");
        placeEmitter(level, linker, GalleryLayout.FIELD_BOX_EMITTER.at(origin), GalleryLayout.FIELD_BOX_FACING,
                GalleryLayout.FIELD_BOX_SETTINGS, "Force Field Emitter — 3×3×3 box, projected upward");

        // 7. East pavilion: three laser pairs across the corridor and the camera at its end.
        int pair = 0;
        for (Offset west : GalleryLayout.LASER_WEST_NODES) {
            BlockPos a = west.at(origin);
            BlockPos b = GalleryLayout.laserPartner(west).at(origin);
            placeLaserNode(level, linker, a, Direction.EAST);
            placeLaserNode(level, linker, b, Direction.WEST);
            if (pair++ == 0) {
                label(level, new BlockPos((a.getX() + b.getX()) / 2, a.getY() + 4, a.getZ()),
                        "Laser Grid — three armed pairs; the gallery beams trip nobody");
            }
        }
        placeCamera(level, linker, GalleryLayout.CORRIDOR_CAMERA.at(origin), Direction.SOUTH,
                "Security Camera — armed; looking down the corridor");

        // 8. The racks: keycards written at the programmer, then the tool and the modules.
        Installation current = installation(level.getServer(), installationId);
        if (current != null) {
            placeKeycardRack(source, player, level, origin, current, DevicePos.of(level, programmerPos));
        }
        placeItemRack(level, origin);

        // 8b. The access-denied bay: a second installation nobody is a member of, and the visitor.
        int deniedLinked = buildDeniedExhibit(source, player, level, origin, installationId, DevicePos.of(level, programmerPos));

        // 9. Light the alarm once so the exhibit opens with the beacon turning.
        current = installation(level.getServer(), installationId);
        if (current != null && level.getBlockEntity(alarmPos) instanceof AlarmBlockEntity alarm) {
            alarm.applySettings(AlarmRules.defaultSubscribed(), EnumSet.of(AlarmResponse.HIGHLIGHT));
            alarm.fire(level, current, AlarmFiring.single(SecurityEventType.DEVICE_FORCED, SecuritySeverity.CRITICAL),
                    System.currentTimeMillis());
        }

        int linked = linker.linked + deniedLinked;
        String shortId = InstallationNames.shortId(installationId);
        source.sendSuccess(() -> Component.translatable("command.nerosecurity.gallery.built", linked, shortId), false);
        return Command.SINGLE_SUCCESS;
    }

    // --- clear ----------------------------------------------------------------------------------------

    private static int clear(CommandSourceStack source) {
        ServerPlayer player = requireCreativePlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        if (!hasGalleryAt(level, origin)) {
            source.sendFailure(Component.translatable("command.nerosecurity.gallery.nothing_here"));
            return 0;
        }
        return clearAt(source, player, level, origin);
    }

    /** Whether a gallery stands at {@code origin}: its hub, or at least its plinth, is where the plan puts it. */
    private static boolean hasGalleryAt(ServerLevel level, BlockPos origin) {
        return level.getBlockState(GalleryLayout.HUB.at(origin)).getBlock() instanceof SecurityHubBlock
                || level.getBlockState(GalleryLayout.PLINTH_CENTRE.at(origin)).is(Blocks.CHISELED_DEEPSLATE);
    }

    /**
     * Takes the gallery at {@code origin} down: every installation whose hub stands in the clear box first
     * (unlink every device, then dissolve, both through {@link InstallationService}; that is the two demo
     * installations plus any hub an operator placed inside the gallery, which would otherwise be wiped to air
     * and leave an orphaned record behind), then every block of the footprint, then the entities the gallery
     * spawned.
     */
    private static int clearAt(CommandSourceStack source, ServerPlayer player, ServerLevel level, BlockPos origin) {
        MinecraftServer server = level.getServer();
        Box box = GalleryLayout.CLEAR_BOX;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int dissolved = 0;
        for (int dy = box.minDy(); dy <= box.maxDy(); dy++) {
            for (int dx = box.minDx(); dx <= box.maxDx(); dx++) {
                for (int dz = box.minDz(); dz <= box.maxDz(); dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (level.getBlockState(cursor).getBlock() instanceof SecurityHubBlock
                            && level.getBlockEntity(cursor) instanceof SecurityHubBlockEntity hub
                            && dissolveThroughService(source, player, server, hub)) {
                        dissolved++;
                    }
                }
            }
        }

        BlockState air = Blocks.AIR.defaultBlockState();
        int cleared = 0;
        // Top down, so a door's upper part never sees its lower part vanish first and cascade.
        for (int dy = box.maxDy(); dy >= box.minDy(); dy--) {
            for (int dx = box.minDx(); dx <= box.maxDx(); dx++) {
                for (int dz = box.minDz(); dz <= box.maxDz(); dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.getBlockState(cursor).isAir()) {
                        level.setBlock(cursor, air, Block.UPDATE_CLIENTS); // notify clients, no neighbour cascade
                        cleared++;
                    }
                }
            }
        }

        AABB bounds = new AABB(origin.getX() + box.minDx(), origin.getY() + box.minDy() - 1,
                origin.getZ() + box.minDz(), origin.getX() + box.maxDx() + 1, origin.getY() + box.maxDy() + 2,
                origin.getZ() + box.maxDz() + 1);
        int removed = 0;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, bounds,
                GalleryCommands::isGalleryEntity)) {
            entity.discard();
            removed++;
        }

        int clearedBlocks = cleared;
        int removedEntities = removed;
        Component installationNote = dissolved == 0
                ? Component.translatable("command.nerosecurity.gallery.cleared.no_installation")
                : Component.translatable("command.nerosecurity.gallery.cleared.dissolved_count", dissolved);
        source.sendSuccess(() -> Component.translatable("command.nerosecurity.gallery.cleared", clearedBlocks,
                removedEntities, installationNote), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Unlinks every device of the installation {@code hub} controls and dissolves it, both through the service
     * (so the resolver judges the clearing operator, every device is told, and the audit log goes with the
     * installation). False when the hub is inert or the service refused.
     */
    private static boolean dissolveThroughService(CommandSourceStack source, ServerPlayer player, MinecraftServer server,
            SecurityHubBlockEntity hub) {
        Installation installation = hub.installation(server);
        if (installation == null) {
            return false;
        }
        for (DevicePos device : List.copyOf(installation.devices().keySet())) {
            Installation now = installation(server, installation.id());
            if (now == null) {
                break;
            }
            ServiceResult unlinked = InstallationService.unlinkDevice(player, now, device);
            if (!unlinked.ok()) {
                source.sendFailure(unlinked.message());
            }
        }
        Installation now = installation(server, installation.id());
        if (now == null) {
            return false;
        }
        hub.markDissolving();
        ServiceResult result = InstallationService.dissolveByBreak(player, now);
        if (!result.ok()) {
            source.sendFailure(Component.translatable("command.nerosecurity.gallery.not_dissolved", result.message()));
        }
        return result.ok();
    }

    /** Whether an entity carries the gallery tag. */
    private static boolean isGalleryEntity(Entity entity) {
        return entity.entityTags().contains(GalleryLayout.ENTITY_TAG);
    }

    // --- the shell ------------------------------------------------------------------------------------

    private static void buildFloor(ServerLevel level, BlockPos origin) {
        BlockState body = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState border = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState spine = Blocks.DEEPSLATE_TILES.defaultBlockState();
        // 26.2 folded the dyed blocks into ColorCollection records.
        //? if <26.2 {
        /*BlockState yellow = Blocks.YELLOW_CONCRETE.defaultBlockState();
        BlockState black = Blocks.BLACK_CONCRETE.defaultBlockState();
        *///?} else {
        BlockState yellow = Blocks.CONCRETE.yellow().defaultBlockState();
        BlockState black = Blocks.CONCRETE.black().defaultBlockState();
        //?}
        BlockState red = redConcrete();
        for (int dx = GalleryLayout.MIN_DX; dx <= GalleryLayout.MAX_DX; dx++) {
            for (int dz = GalleryLayout.MIN_DZ; dz <= GalleryLayout.MAX_DZ; dz++) {
                BlockState state = body;
                if (GalleryLayout.onBorder(dx, dz)) {
                    state = border;
                } else if (GalleryLayout.onPlinthRing(dx, dz) || GalleryLayout.onThreshold(dx, dz)
                        || onPavilionThreshold(dx, dz)) {
                    state = GalleryLayout.hazardYellow(dx, dz) ? yellow : black;
                } else if (GalleryLayout.onDeniedThreshold(dx, dz)) {
                    state = GalleryLayout.hazardYellow(dx, dz) ? red : black; // red: the bay refuses
                } else if (GalleryLayout.onSpine(dx, dz)) {
                    state = spine;
                }
                level.setBlockAndUpdate(origin.offset(dx, 0, dz), state);
            }
        }
        // The raised plinths under the two hubs.
        for (Offset centre : List.of(GalleryLayout.PLINTH_CENTRE, GalleryLayout.DENIED_PLINTH_CENTRE)) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlockAndUpdate(centre.plus(dx, 0, dz).at(origin), Blocks.CHISELED_DEEPSLATE.defaultBlockState());
                }
            }
        }
        // The alarm posts: slender wall-block columns.
        for (int dy = 0; dy < GalleryLayout.ALARM_POST_HEIGHT; dy++) {
            level.setBlockAndUpdate(GalleryLayout.ALARM_POST_BASE.plus(0, dy, 0).at(origin),
                    Blocks.DEEPSLATE_TILE_WALL.defaultBlockState());
        }
        for (int dy = 0; dy < GalleryLayout.DENIED_ALARM_POST_HEIGHT; dy++) {
            level.setBlockAndUpdate(GalleryLayout.DENIED_ALARM_POST_BASE.plus(0, dy, 0).at(origin),
                    Blocks.DEEPSLATE_TILE_WALL.defaultBlockState());
        }
    }

    /** Red concrete: the bay's colour. 26.2 folded the dyed blocks into ColorCollection records. */
    private static BlockState redConcrete() {
        //? if <26.2 {
        /*return Blocks.RED_CONCRETE.defaultBlockState();
        *///?} else {
        return Blocks.CONCRETE.red().defaultBlockState();
        //?}
    }

    /** The hazard strip across each pavilion's open front. */
    private static boolean onPavilionThreshold(int dx, int dz) {
        if (dz != GalleryLayout.PAVILION_FRONT_DZ) {
            return false;
        }
        return (dx > GalleryLayout.WEST_PAVILION_MIN_DX + 1 && dx < GalleryLayout.WEST_PAVILION_MAX_DX - 1)
                || (dx > GalleryLayout.EAST_PAVILION_MIN_DX + 1 && dx < GalleryLayout.EAST_PAVILION_MAX_DX - 1);
    }

    /**
     * The door wall: two blocks thick, from the floor to {@link GalleryLayout#WALL_TOP_DY}, capped with slabs.
     * The three doorways are left open through both layers; the doors themselves come later.
     */
    private static void buildDoorWall(ServerLevel level, BlockPos origin) {
        BlockState wall = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState lintel = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState cap = Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState();
        for (int dx = GalleryLayout.DOOR_WALL_MIN_DX; dx <= GalleryLayout.DOOR_WALL_MAX_DX; dx++) {
            boolean doorway = dx == GalleryLayout.SECURITY_DOOR_CLOSED.dx() || dx == GalleryLayout.SECURITY_DOOR_OPEN.dx()
                    || dx == GalleryLayout.BLAST_DOOR.dx() || dx == GalleryLayout.BLAST_DOOR.dx() + 1;
            for (int dz = GalleryLayout.DOOR_WALL_BACK_DZ; dz <= GalleryLayout.DOOR_WALL_FRONT_DZ; dz++) {
                for (int dy = 1; dy <= GalleryLayout.WALL_TOP_DY; dy++) {
                    if (doorway && dy <= 2) {
                        continue; // the opening, both layers deep
                    }
                    boolean overDoor = doorway && dy == 3;
                    level.setBlockAndUpdate(origin.offset(dx, dy, dz), overDoor ? lintel : wall);
                }
                level.setBlockAndUpdate(origin.offset(dx, GalleryLayout.WALL_TOP_DY + 1, dz), cap);
            }
        }
    }

    /** The two pavilions: U-shaped two-thick walls open to the south, capped with slabs. */
    private static void buildPavilions(ServerLevel level, BlockPos origin) {
        buildPavilion(level, origin, GalleryLayout.WEST_PAVILION_MIN_DX, GalleryLayout.WEST_PAVILION_MAX_DX);
        buildPavilion(level, origin, GalleryLayout.EAST_PAVILION_MIN_DX, GalleryLayout.EAST_PAVILION_MAX_DX);
    }

    private static void buildPavilion(ServerLevel level, BlockPos origin, int minDx, int maxDx) {
        BlockState wall = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState band = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState cap = Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState();
        for (int dx = minDx; dx <= maxDx; dx++) {
            for (int dz = GalleryLayout.PAVILION_BACK_DZ; dz <= GalleryLayout.PAVILION_FRONT_DZ; dz++) {
                boolean side = dx <= minDx + 1 || dx >= maxDx - 1;
                boolean back = dz <= GalleryLayout.PAVILION_BACK_DZ + 1;
                if (!side && !back) {
                    continue;
                }
                for (int dy = 1; dy <= GalleryLayout.PAVILION_TOP_DY; dy++) {
                    level.setBlockAndUpdate(origin.offset(dx, dy, dz), dy == 3 ? band : wall);
                }
                level.setBlockAndUpdate(origin.offset(dx, GalleryLayout.PAVILION_TOP_DY + 1, dz), cap);
            }
        }
    }

    /**
     * The access-denied bay: a U of two-thick walls open to the south, banded in red, with the visitor cell's
     * partition on the west and the members' room (partition and front wall with the doorway) on the east.
     */
    private static void buildDeniedBay(ServerLevel level, BlockPos origin) {
        BlockState wall = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState band = redConcrete();
        BlockState cap = Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState();
        int top = GalleryLayout.PAVILION_TOP_DY;
        for (int dx = GalleryLayout.DENIED_MIN_DX; dx <= GalleryLayout.DENIED_MAX_DX; dx++) {
            for (int dz = GalleryLayout.DENIED_BACK_DZ; dz <= GalleryLayout.DENIED_FRONT_DZ; dz++) {
                boolean side = dx <= GalleryLayout.DENIED_MIN_DX + 1 || dx >= GalleryLayout.DENIED_MAX_DX - 1;
                boolean back = dz <= GalleryLayout.DENIED_BACK_DZ + 1;
                if (!side && !back) {
                    continue;
                }
                for (int dy = 1; dy <= top; dy++) {
                    level.setBlockAndUpdate(origin.offset(dx, dy, dz), dy == 3 ? band : wall);
                }
                level.setBlockAndUpdate(origin.offset(dx, top + 1, dz), cap);
            }
        }
        // The cell partition (west) and the room partition (east), both two thick, back wall to the field line.
        for (int dz = GalleryLayout.CELL_MIN_DZ; dz <= GalleryLayout.CELL_FIELD_DZ; dz++) {
            for (int dy = 1; dy <= top; dy++) {
                BlockState state = dy == 3 ? band : wall;
                for (int dx = GalleryLayout.CELL_PARTITION_MIN_DX; dx <= GalleryLayout.CELL_PARTITION_MAX_DX; dx++) {
                    level.setBlockAndUpdate(origin.offset(dx, dy, dz), state);
                }
                for (int dx = GalleryLayout.ROOM_PARTITION_MIN_DX; dx <= GalleryLayout.ROOM_PARTITION_MAX_DX; dx++) {
                    level.setBlockAndUpdate(origin.offset(dx, dy, dz), state);
                }
            }
        }
        // The room's front wall, one thick, with the doorway left open (the door itself comes later).
        for (int dx = GalleryLayout.ROOM_MIN_DX; dx <= GalleryLayout.ROOM_MAX_DX; dx++) {
            for (int dy = 1; dy <= top; dy++) {
                if (dx == GalleryLayout.DENIED_DOOR.dx() && dy <= 2) {
                    continue;
                }
                level.setBlockAndUpdate(origin.offset(dx, dy, GalleryLayout.ROOM_FRONT_DZ), dy == 3 ? band : wall);
            }
        }
    }

    /** The two rack walls, one block thick, with a tile band at frame height. */
    private static void buildRacks(ServerLevel level, BlockPos origin) {
        BlockState wall = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState band = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState cap = Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState();
        for (int dx : new int[] {GalleryLayout.CARD_RACK_DX, GalleryLayout.ITEM_RACK_DX}) {
            for (int dz = GalleryLayout.RACK_MIN_DZ; dz <= GalleryLayout.RACK_MAX_DZ; dz++) {
                for (int dy = 1; dy <= GalleryLayout.RACK_TOP_DY; dy++) {
                    level.setBlockAndUpdate(origin.offset(dx, dy, dz), dy == GalleryLayout.RACK_FRAME_DY ? band : wall);
                }
                level.setBlockAndUpdate(origin.offset(dx, GalleryLayout.RACK_TOP_DY + 1, dz), cap);
            }
        }
    }

    /** Soul lanterns on the wall tops for the look, invisible light blocks overhead for the fill. */
    private static void buildLighting(ServerLevel level, BlockPos origin) {
        BlockState lantern = Blocks.SOUL_LANTERN.defaultBlockState();
        for (Offset seat : GalleryLayout.lanterns()) {
            BlockPos pos = seat.at(origin);
            // A lantern needs something under it: the slab cap is there, but put a full block under it first.
            level.setBlockAndUpdate(pos.below(), Blocks.DEEPSLATE_TILES.defaultBlockState());
            level.setBlockAndUpdate(pos, lantern);
        }
        BlockState light = Blocks.LIGHT.defaultBlockState();
        for (Offset at : GalleryLayout.lights()) {
            level.setBlock(at.at(origin), light, Block.UPDATE_CLIENTS);
        }
    }

    // --- devices --------------------------------------------------------------------------------------

    /** Links devices to the demo installation, one service call each, and counts what took. */
    private static final class Linker {
        private final CommandSourceStack source;
        private final ServerPlayer player;
        private final ServerLevel level;
        private final UUID installationId;
        private int linked;

        Linker(CommandSourceStack source, ServerPlayer player, ServerLevel level, UUID installationId) {
            this.source = source;
            this.player = player;
            this.level = level;
            this.installationId = installationId;
        }

        void link(BlockPos pos) {
            Installation current = installation(level.getServer(), installationId);
            if (current == null) {
                return;
            }
            ServiceResult result = InstallationService.linkDevice(player, current, level, pos);
            if (result.ok()) {
                linked++;
            } else {
                source.sendFailure(result.message());
            }
        }
    }

    private static @Nullable Installation installation(MinecraftServer server, UUID id) {
        return SecurityInstallations.get(server).byId(id).orElse(null);
    }

    /** A 1x2 Security Door, every part placed the way {@code setPlacedBy} would. */
    private static void placeSecurityDoor(ServerLevel level, BlockPos controller, Direction facing) {
        SecurityDoorBlock door = (SecurityDoorBlock) DoorRegistrations.SECURITY_DOOR.get();
        BlockState base = door.defaultBlockState()
                .setValue(AbstractSecurityDoorBlock.FACING, facing)
                .setValue(AbstractSecurityDoorBlock.OPEN, false)
                .setValue(AbstractSecurityDoorBlock.ANIMATING, false)
                .setValue(AbstractSecurityDoorBlock.SEALED, false);
        List<BlockPos> parts = door.partPositions(controller, facing);
        for (int i = 0; i < parts.size(); i++) {
            level.setBlock(parts.get(i), base.setValue(SecurityDoorBlock.PART, DoorPart.values()[i]), Block.UPDATE_ALL);
        }
    }

    /** A 2x2 Blast Door; the controller is the bottom-left part seen from the front. */
    private static void placeBlastDoor(ServerLevel level, BlockPos controller, Direction facing) {
        BlastDoorBlock door = (BlastDoorBlock) DoorRegistrations.BLAST_DOOR.get();
        BlockState base = door.defaultBlockState()
                .setValue(AbstractSecurityDoorBlock.FACING, facing)
                .setValue(AbstractSecurityDoorBlock.OPEN, false)
                .setValue(AbstractSecurityDoorBlock.ANIMATING, false)
                .setValue(AbstractSecurityDoorBlock.SEALED, false);
        List<BlockPos> parts = door.partPositions(controller, facing);
        for (int i = 0; i < parts.size(); i++) {
            level.setBlock(parts.get(i), base.setValue(BlastDoorBlock.PART, BlastPart.values()[i]), Block.UPDATE_ALL);
        }
    }

    /** A camera on a wall, its battery hidden in the wall block behind it; PUBLIC, so it records nobody. */
    private static void placeCamera(ServerLevel level, Linker linker, BlockPos pos, Direction facing, String text) {
        placeCamera(level, linker, pos, facing, GALLERY_SENSOR, pos.above(2), text);
    }

    /** A camera with explicit sensor settings and label position (null text: no label). */
    private static void placeCamera(ServerLevel level, Linker linker, BlockPos pos, Direction facing,
            SensorSettings sensor, BlockPos labelPos, @Nullable String text) {
        level.setBlockAndUpdate(pos, CameraRegistrations.SECURITY_CAMERA.get().defaultBlockState()
                .setValue(SecurityCameraBlock.FACING, facing)
                .setValue(SecurityCameraBlock.ARMED, false));
        placeCreativeBattery(level, pos.relative(facing.getOpposite()));
        linker.link(pos);
        chargeMachine(level, pos);
        if (level.getBlockEntity(pos) instanceof SecurityCameraBlockEntity camera) {
            camera.setSensorSettings(sensor);
        }
        if (text != null) {
            label(level, labelPos, text);
        }
    }

    /** A laser grid node on a wall, battery behind it; PUBLIC, so the beam draws but trips nobody. */
    private static void placeLaserNode(ServerLevel level, Linker linker, BlockPos pos, Direction facing) {
        placeLaserNode(level, linker, pos, facing, GALLERY_SENSOR);
    }

    /** A laser grid node with explicit sensor settings. */
    private static void placeLaserNode(ServerLevel level, Linker linker, BlockPos pos, Direction facing,
            SensorSettings sensor) {
        level.setBlockAndUpdate(pos, LaserRegistrations.LASER_GRID_NODE.get().defaultBlockState()
                .setValue(LaserGridNodeBlock.FACING, facing)
                .setValue(LaserGridNodeBlock.ARMED, false));
        placeCreativeBattery(level, pos.relative(facing.getOpposite()));
        linker.link(pos);
        chargeMachine(level, pos);
        if (level.getBlockEntity(pos) instanceof LaserGridNodeBlockEntity node) {
            node.setSensorSettings(sensor);
        }
    }

    /** Gallery sensors watch everyone as permitted: armed and sweeping, recording and hurting nobody. */
    private static final SensorSettings GALLERY_SENSOR =
            new SensorSettings(AccessLevel.PUBLIC, SensorEntityMode.PLAYERS_ONLY, SensorSettings.DEFAULT.fovDegrees());

    /**
     * The bay's sensors: every player is permitted (PUBLIC, so no player is ever logged by them) and every other
     * living thing trips them, which is exactly one entity in the whole gallery: the visitor.
     */
    private static final SensorSettings VISITOR_SENSOR =
            new SensorSettings(AccessLevel.PUBLIC, SensorEntityMode.ALL_LIVING, GalleryLayout.CELL_CAMERA_FOV);

    /** A force field emitter, battery behind it (or under it, for a floor emitter), with the given field. */
    private static void placeEmitter(ServerLevel level, Linker linker, BlockPos pos, Direction facing,
            za.co.neroland.nerosecurity.field.FieldSettings settings, String text) {
        placeEmitter(level, linker, pos, facing, settings, facing == Direction.UP ? pos.above(5) : pos.above(2), text);
    }

    /** A force field emitter with an explicit label position. */
    private static void placeEmitter(ServerLevel level, Linker linker, BlockPos pos, Direction facing,
            za.co.neroland.nerosecurity.field.FieldSettings settings, BlockPos labelPos, String text) {
        level.setBlockAndUpdate(pos, FieldRegistrations.EMITTER_BLOCK.get().defaultBlockState()
                .setValue(ForceFieldEmitterBlock.FACING, facing)
                .setValue(ForceFieldEmitterBlock.ACTIVE, false));
        placeCreativeBattery(level, pos.relative(facing.getOpposite()));
        linker.link(pos);
        chargeMachine(level, pos);
        if (level.getBlockEntity(pos) instanceof ForceFieldEmitterBlockEntity emitter) {
            emitter.setSettings(settings);
        }
        label(level, labelPos, text);
    }

    /** The hidden endless source: Neroland Core's Creative Battery. */
    private static void placeCreativeBattery(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos,
                za.co.neroland.nerolandcore.registry.ModBlocks.CREATIVE_BATTERY.get().defaultBlockState());
    }

    /** A battery in the floor directly under a floor-standing device. */
    private static void powerBelow(ServerLevel level, BlockPos devicePos) {
        placeCreativeBattery(level, devicePos.below());
    }

    /** Fills a device's buffer so its gauge reads full on arrival ({@code setRaw} clamps to capacity). */
    private static void chargeMachine(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AbstractMachineBlockEntity machine
                && machine.getEnergy() instanceof EnergyBuffer buffer) {
            buffer.setRaw(Integer.MAX_VALUE);
            machine.setChanged();
        }
    }

    // --- the access-denied bay ------------------------------------------------------------------------

    /**
     * Builds the bay's exhibit: the second installation and its hub, the devices that refuse (chest, keycard-only
     * door, the cell's field), the visitor with the beams and camera that catch it, the alarm with its lamp, the
     * monitor, the labels and the three-card rack. Every link, settings change and card goes through the same
     * service calls a player's actions take; the operator is the actor of each, and every row lands in the bay's
     * own audit log, which {@code gallery clear} dissolves.
     *
     * @return how many of the bay's devices linked
     */
    private static int buildDeniedExhibit(CommandSourceStack source, ServerPlayer player, ServerLevel level,
            BlockPos origin, UUID galleryId, DevicePos programmerAt) {
        BlockPos hubPos = GalleryLayout.DENIED_HUB.at(origin);
        level.setBlockAndUpdate(hubPos, NeroSecurityBlocks.SECURITY_HUB.get().defaultBlockState()
                .setValue(SecurityHubBlock.FACING, Direction.SOUTH));
        if (!(level.getBlockEntity(hubPos) instanceof SecurityHubBlockEntity hub)) {
            source.sendFailure(Component.translatable("command.nerosecurity.gallery.hub_failed",
                    Component.translatable("hub.nerosecurity.inert")));
            return 0;
        }
        Installation denied = createUnownedInstallation(level, hubPos);
        if (denied == null) {
            source.sendFailure(Component.translatable("command.nerosecurity.gallery.hub_failed",
                    Component.translatable("hub.nerosecurity.position_taken")));
            return 0;
        }
        UUID deniedId = denied.id();
        hub.bind(deniedId);
        hub.refreshVisual(level);
        Linker linker = new Linker(source, player, level, deniedId);

        label(level, GalleryLayout.DENIED_TITLE.at(origin),
                "ACCESS DENIED \u2014 nobody is a member of the installation ahead; watch it refuse");
        label(level, GalleryLayout.DENIED_NOTE.at(origin),
                "Operators are always let through \u2014 the visitor in the cell shows what everyone else gets");
        label(level, hubPos.above(2), "Security Hub \u2014 owns \"" + GalleryLayout.DENIED_INSTALLATION_NAME
                + "\": owned by nobody, so you are NOT a member here");

        // The devices that refuse players: a MEMBER chest, a keycard-only MEMBER door.
        BlockPos chestPos = GalleryLayout.DENIED_CHEST.at(origin);
        level.setBlockAndUpdate(chestPos, ChestRegistrations.SECURE_CHEST.get().defaultBlockState()
                .setValue(SecureChestBlock.FACING, Direction.SOUTH));
        linker.link(chestPos);
        if (level.getBlockEntity(chestPos) instanceof SecureChestBlockEntity chest) {
            chest.applySettings(new DeviceAccessSettings(AccessLevel.MEMBER, false));
        }
        label(level, chestPos.above(2), "Secure Chest \u2014 MEMBER only: refuses anyone who is not a member (deny sound, flash, reason)");

        BlockPos doorPos = GalleryLayout.DENIED_DOOR.at(origin);
        placeSecurityDoor(level, doorPos, Direction.SOUTH);
        powerBelow(level, doorPos);
        linker.link(doorPos);
        chargeMachine(level, doorPos);
        if (level.getBlockEntity(doorPos) instanceof SecurityDoorBlockEntity door) {
            door.applySettings(new DeviceAccessSettings(AccessLevel.MEMBER, true), RedstoneMode.IGNORE);
        }
        label(level, doorPos.above(3), "Security Door \u2014 keycard only: the VISITOR card opens it, the foreign cards are refused");
        label(level, GalleryLayout.ROOM_LABEL.at(origin), "Members' room \u2014 reached only with this installation's card");

        // The alarm (light and redstone pulse only: no siren, no chat, never lockdown) with a lamp on top, and the monitor.
        BlockPos alarmPos = GalleryLayout.DENIED_ALARM.at(origin);
        level.setBlockAndUpdate(alarmPos, AlarmRegistrations.ALARM.get().defaultBlockState());
        linker.link(alarmPos);
        if (level.getBlockEntity(alarmPos) instanceof AlarmBlockEntity alarm) {
            alarm.applySettings(AlarmRules.defaultSubscribed(), EnumSet.of(AlarmResponse.HIGHLIGHT, AlarmResponse.REDSTONE));
        }
        level.setBlockAndUpdate(GalleryLayout.DENIED_LAMP.at(origin), Blocks.REDSTONE_LAMP.defaultBlockState());
        label(level, alarmPos.above(3), "Alarm \u2014 fires on every trip and refusal in this bay; the lamp blinks with its pulse");

        BlockPos monitorPos = GalleryLayout.DENIED_MONITOR.at(origin);
        level.setBlockAndUpdate(monitorPos, MonitorRegistrations.BLOCK.get().defaultBlockState()
                .setValue(SecurityMonitorBlock.FACING, Direction.SOUTH));
        linker.link(monitorPos);
        label(level, monitorPos.above(2), "Security Monitor \u2014 every trip and refusal in this bay shows up here");

        // The visitor cell: the field that pens it, the two beams across it, the camera over it, the visitor.
        placeEmitter(level, linker, GalleryLayout.CELL_FIELD_EMITTER.at(origin), GalleryLayout.CELL_FIELD_FACING,
                GalleryLayout.CELL_FIELD_SETTINGS, origin.offset(GalleryLayout.VISITOR.dx(), 4, GalleryLayout.CELL_FIELD_DZ + 1),
                "Force Field \u2014 solid to the visitor and to non-members; members and operators walk through");
        placeCamera(level, linker, GalleryLayout.CELL_CAMERA.at(origin), Direction.SOUTH, VISITOR_SENSOR,
                origin.offset(GalleryLayout.VISITOR.dx(), 5, GalleryLayout.CELL_MAX_DZ),
                "Visitor cell \u2014 beams and camera set to every living thing: the visitor trips them, players never do");
        for (Offset west : GalleryLayout.CELL_LASER_WEST_NODES) {
            placeLaserNode(level, linker, west.at(origin), Direction.EAST, VISITOR_SENSOR);
            placeLaserNode(level, linker, GalleryLayout.cellLaserPartner(west).at(origin), Direction.WEST, VISITOR_SENSOR);
        }
        spawnVisitor(level, GalleryLayout.VISITOR.at(origin));

        // The rack: this bay's MEMBER card, a foreign (Gallery) MEMBER card, this bay's OFFICER card.
        Installation gallery = installation(level.getServer(), galleryId);
        List<Offset> slots = GalleryLayout.deniedRackSlots();
        List<ItemStack> cards = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        cards.add(writeCard(source, player, level, denied, DevicePos.of(level, hubPos), AccessLevel.MEMBER, false));
        texts.add("Visitor card \u2014 MEMBER of \"" + GalleryLayout.DENIED_INSTALLATION_NAME + "\": opens the door");
        cards.add(gallery == null ? new ItemStack(NeroSecurityItems.KEYCARD.get())
                : writeCard(source, player, level, gallery, programmerAt, AccessLevel.MEMBER, false));
        texts.add("Foreign card \u2014 MEMBER of \"" + GalleryLayout.INSTALLATION_NAME + "\": refused here (wrong installation)");
        cards.add(writeCard(source, player, level, denied, DevicePos.of(level, hubPos), AccessLevel.OFFICER, false));
        texts.add("Foreign card \u2014 OFFICER of \"" + GalleryLayout.DENIED_INSTALLATION_NAME
                + "\": take it to the Gallery's blast door, refused there");
        for (int i = 0; i < slots.size() && i < cards.size(); i++) {
            BlockPos at = slots.get(i).at(origin);
            hang(level, at, Direction.WEST, cards.get(i));
            label(level, at.above(2), texts.get(i));
        }
        return linker.linked;
    }

    /**
     * Registers the bay's installation through the store, owned by the tombstone owner: nobody holds a membership
     * row in it, so the resolver grants it to no player (the operator override aside), and no owner is ever
     * notified about it. The per-player cap is not applied: the owner is nobody. The hub's record is written the
     * way {@link InstallationService#createForHub} writes one, minus the creation event, which would name the
     * operator as the creator of an installation that is not theirs.
     */
    private static @Nullable Installation createUnownedInstallation(ServerLevel level, BlockPos hubPos) {
        MinecraftServer server = level.getServer();
        SecurityInstallations store = SecurityInstallations.get(server);
        SecurityInstallations.CreateResult created = store.create(Installation.TOMBSTONE_OWNER,
                GalleryLayout.DENIED_INSTALLATION_NAME, DevicePos.of(level, hubPos), System.currentTimeMillis(),
                Integer.MAX_VALUE);
        if (created.status() != SecurityInstallations.CreateStatus.OK || created.installation() == null) {
            return null;
        }
        Installation installation = created.installation();
        AccessLevel automation = NeroSecurityConfig.chestAutomationDefault();
        if (automation != null) {
            store.setAutomationLevel(installation.id(), automation);
        }
        InstallationAreas.invalidate(installation.id());
        return installation;
    }

    /**
     * The visitor: a villager (passive, never a player, not owned by one) standing still in the cell. No AI, so it
     * never wanders into the beams' nodes or out of the camera's view; silent; persistent, so it never despawns;
     * invulnerable, so a configured {@code laserTripDamage} never hurts it; tagged, so {@code gallery clear} takes
     * it away. Its name tag says what it is.
     */
    private static void spawnVisitor(ServerLevel level, BlockPos pos) {
        // 26.2 moved the vanilla entity type constants from EntityType to EntityTypes.
        //? if <26.2 {
        /*Villager visitor = new Villager(net.minecraft.world.entity.EntityType.VILLAGER, level);
        *///?} else {
        Villager visitor = new Villager(net.minecraft.world.entity.EntityTypes.VILLAGER, level);
        //?}
        visitor.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F); // yaw 0: facing south, at the viewer
        visitor.setYBodyRot(0.0F);
        visitor.setYHeadRot(0.0F);
        visitor.setNoAi(true);
        visitor.setSilent(true);
        visitor.setPersistenceRequired();
        //? if >=26.3 {
        /*visitor.setPermanentlyInvulnerable(true);
        *///?} else {
        visitor.setInvulnerable(true);
        //?}
        visitor.setCustomName(Component.literal(GalleryLayout.VISITOR_NAME));
        visitor.setCustomNameVisible(true);
        visitor.addTag(GalleryLayout.ENTITY_TAG);
        level.addFreshEntity(visitor);
    }

    // --- the racks ------------------------------------------------------------------------------------

    /**
     * Writes one card per level, an encrypted MEMBER card and a blank at the programmer (every write goes through
     * {@link KeycardService#write}, so each card is signed, in the ledger and audited like any other), and hangs them
     * in glow item frames on the keycard rack.
     */
    private static void placeKeycardRack(CommandSourceStack source, ServerPlayer player, ServerLevel level, BlockPos origin,
            Installation installation, DevicePos programmerAt) {
        List<Offset> slots = GalleryLayout.rackSlots(GalleryLayout.CARD_RACK_DX, Direction.WEST);
        List<ItemStack> cards = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (AccessLevel levelOnCard : GalleryLayout.CARD_LEVELS) {
            // OWNER cards must be encrypted (keycardBasicMaxLevel); the others are plain, copyable cards.
            boolean encrypted = levelOnCard == AccessLevel.OWNER;
            cards.add(writeCard(source, player, level, installation, programmerAt, levelOnCard, encrypted));
            texts.add("Keycard — " + levelOnCard.name() + (encrypted ? " (encrypted)" : ""));
        }
        cards.add(writeCard(source, player, level, installation, programmerAt, AccessLevel.MEMBER, true));
        texts.add("Keycard — MEMBER (encrypted: level and serial hidden)");
        cards.add(new ItemStack(NeroSecurityItems.KEYCARD.get()));
        texts.add("Keycard — blank");
        for (int i = 0; i < slots.size() && i < cards.size(); i++) {
            BlockPos at = slots.get(i).at(origin);
            hang(level, at, Direction.WEST, cards.get(i));
            label(level, at.above(2), texts.get(i));
        }
    }

    private static ItemStack writeCard(CommandSourceStack source, ServerPlayer player, ServerLevel level,
            Installation installation, DevicePos at, AccessLevel cardLevel, boolean encrypted) {
        ItemStack card = new ItemStack(NeroSecurityItems.KEYCARD.get());
        Installation current = installation(level.getServer(), installation.id());
        if (current == null) {
            return card;
        }
        ServiceResult result = KeycardService.write(player, current, at, card, cardLevel, encrypted, false, true);
        if (!result.ok()) {
            source.sendFailure(result.message());
        }
        return card;
    }

    /** The linking tool and the five modules, one per frame, on the item rack. */
    private static void placeItemRack(ServerLevel level, BlockPos origin) {
        List<Offset> slots = GalleryLayout.rackSlots(GalleryLayout.ITEM_RACK_DX, Direction.EAST);
        List<ItemStack> items = List.of(
                new ItemStack(NeroSecurityItems.LINKING_TOOL.get()),
                new ItemStack(NeroSecurityItems.ENCRYPTION_MODULE.get()),
                new ItemStack(NeroSecurityItems.HARDNESS_MODULE.get()),
                new ItemStack(NeroSecurityItems.OPEN_SPEED_MODULE.get()),
                new ItemStack(NeroSecurityItems.RANGE_MODULE.get()),
                new ItemStack(NeroSecurityItems.STRENGTH_MODULE.get()));
        List<String> texts = List.of(
                "Linking Tool — sneak-use a hub, then use a device",
                "Encryption Module — programmer: encrypted cards",
                "Hardness Module — doors: slower to break, blast-resistant",
                "Open Speed Module — doors: faster opening",
                "Range Module — lasers, cameras, emitters: +4 blocks",
                "Strength Module — emitters: +1 field strength");
        for (int i = 0; i < slots.size() && i < items.size(); i++) {
            BlockPos at = slots.get(i).at(origin);
            hang(level, at, Direction.EAST, items.get(i));
            label(level, at.above(2), texts.get(i));
        }
    }

    /** A glow item frame on the wall behind {@code pos}, showing {@code stack}, tagged as a gallery entity. */
    private static void hang(ServerLevel level, BlockPos pos, Direction facing, ItemStack stack) {
        GlowItemFrame frame = new GlowItemFrame(level, pos, facing);
        frame.setItem(stack, false);
        frame.addTag(GalleryLayout.ENTITY_TAG);
        level.addFreshEntity(frame);
    }

    // --- labels ---------------------------------------------------------------------------------------

    /** A floating label: an invisible marker armour stand with a visible name (the sibling mods' recipe). */
    private static void label(ServerLevel level, BlockPos pos, String text) {
        ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        // Marker = zero-size bounding box, so the stand never hijacks pick-block or a click on the exhibit.
        // ArmorStand#setMarker is private in 26.x, so the flag goes in the only public way: Entity#load.
        CompoundTag markerTag = new CompoundTag();
        markerTag.putBoolean("Marker", true);
        stand.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), markerTag));
        stand.refreshDimensions();
        stand.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5); // load() zeroed the position
        stand.setCustomName(Component.literal(text));
        stand.setCustomNameVisible(true);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        //? if >=26.3 {
        /*stand.setPermanentlyInvulnerable(true);
        *///?} else {
        stand.setInvulnerable(true);
        //?}
        stand.addTag(GalleryLayout.ENTITY_TAG);
        level.addFreshEntity(stand);
    }
}

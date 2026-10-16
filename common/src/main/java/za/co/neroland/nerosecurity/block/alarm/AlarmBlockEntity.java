package za.co.neroland.nerosecurity.block.alarm;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.alarm.AlarmFiring;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.registry.AlarmRegistrations;
import za.co.neroland.nerosecurity.sound.AlarmTones;
import za.co.neroland.nerosecurity.sound.SecuritySounds;

/**
 * The alarm's block entity: which event types it listens to, what it does when one fires, and how long
 * it is silenced. Unpowered on purpose: the alarm is the safety device and must work on a server with no
 * generator at all.
 *
 * <p>The dispatcher hands it a coalesced {@link AlarmFiring}; it never listens to the bus itself. It
 * holds no installation pointer (every check re-reads the store by position) and no player data: the
 * only persisted state is the two sets and the silence deadline. The {@code ACTIVE} and {@code POWERED}
 * block-state properties carry the light/beacon and the redstone pulse to clients and to redstone; their
 * timers are transient and a reloaded alarm simply goes quiet.
 */
public class AlarmBlockEntity extends BlockEntity {

    private static final String KEY_SUBSCRIBED = "Subscribed";
    private static final String KEY_RESPONSES = "Responses";
    private static final String KEY_SILENCED_UNTIL = "SilencedUntil";
    private static final Codec<List<String>> NAMES = Codec.STRING.listOf();

    private EnumSet<SecurityEventType> subscribed = AlarmRules.defaultSubscribed();
    private EnumSet<AlarmResponse> responses = AlarmRules.defaultResponses();
    /** Epoch ms; 0 = not silenced. */
    private long silencedUntil;

    // --- transient ------------------------------------------------------------------------------
    private int activeTicks;
    private int pulseTicks;
    /** Ticks the two-tone siren keeps sounding, and how long it has sounded (see {@link AlarmTones}). */
    private int soundTicks;
    private int soundElapsed;
    /** Set once removal starts, so a firing published during our own removal never touches this block. */
    private boolean removing;
    private long playerBreakAt = Long.MIN_VALUE;

    public AlarmBlockEntity(BlockPos pos, BlockState state) {
        super(AlarmRegistrations.ALARM_BLOCK_ENTITY.get(), pos, state);
    }

    // --- settings -------------------------------------------------------------------------------

    public Set<SecurityEventType> subscribed() {
        return EnumSet.copyOf(subscribed);
    }

    public Set<AlarmResponse> responses() {
        return EnumSet.copyOf(responses);
    }

    public long silencedUntil() {
        return silencedUntil;
    }

    /** Replaces both sets. Callers have already authorised the change (including the lockdown flag). */
    public void applySettings(Set<SecurityEventType> wantedTypes, Set<AlarmResponse> wantedResponses) {
        this.subscribed = AlarmRules.sanitiseSubscribed(wantedTypes);
        this.responses = AlarmRules.copyResponses(wantedResponses);
        setChanged();
    }

    /** Linking resets everything, so nothing set while it stood unlinked carries over. */
    public void resetSettings() {
        this.subscribed = AlarmRules.defaultSubscribed();
        this.responses = AlarmRules.defaultResponses();
        this.silencedUntil = 0L;
        setChanged();
    }

    /** Silences until {@code until} (epoch ms). Callers have authorised and clamped it. */
    public void silenceUntil(long until) {
        this.silencedUntil = Math.max(0L, until);
        setChanged();
    }

    /** A lockdown began: clear any silence. */
    public void rearm() {
        if (silencedUntil != 0L) {
            silencedUntil = 0L;
            setChanged();
        }
    }

    // --- installation ---------------------------------------------------------------------------

    /** The installation this alarm is linked to right now, or null. */
    public @Nullable Installation installation() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return SecurityInstallations.get(serverLevel.getServer())
                .atDevice(DevicePos.of(serverLevel, worldPosition)).orElse(null);
    }

    /** Whether this alarm reacts right now for {@code installation}. */
    public boolean armed(@Nullable Installation installation, long now) {
        // The installation-wide silence (set from the companion app, reaching unloaded alarms too) counts as well.
        long silence = installation == null ? silencedUntil : Math.max(silencedUntil, installation.alarmSilencedUntil());
        return AlarmRules.armed(installation != null, now, silence, NeroSecurityConfig.isLockdownEnabled(),
                installation != null && installation.lockdown().active());
    }

    /** Whether this alarm would react to {@code firing}: armed, subscribed, and not being removed. */
    public boolean wouldFire(@Nullable Installation installation, AlarmFiring firing, long now) {
        return !removing && !isRemoved() && armed(installation, now) && firing.matches(subscribed);
    }

    /**
     * Carries out this alarm's responses for one firing (the dispatcher checked {@link #wouldFire}).
     * Chat is not sent here: the return value says whether this alarm wants it, and the dispatcher sends
     * one message per firing.
     *
     * <p>The responses act on this alarm's own view of the firing ({@link AlarmRules#effective}): severity
     * and type over the window's types that this alarm subscribes to, so an unsubscribed CRITICAL in the
     * same window can never push a subscribed WARN over the lockdown gate.
     */
    public boolean fire(ServerLevel serverLevel, Installation installation, AlarmFiring firing, long now) {
        if (removing || isRemoved()) {
            return false;
        }
        AlarmFiring mine = AlarmRules.effective(firing, subscribed);
        if (mine == null) {
            return false;
        }
        BlockState state = serverLevel.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof AlarmBlock)) {
            return false;
        }
        BlockState next = state;
        if (responses.contains(AlarmResponse.REDSTONE)) {
            pulseTicks = Math.max(1, NeroSecurityConfig.alarmRedstonePulseTicks());
            next = next.setValue(AlarmBlock.POWERED, Boolean.TRUE);
        }
        if (responses.contains(AlarmResponse.HIGHLIGHT)) {
            activeTicks = Math.max(1, NeroSecurityConfig.alarmActiveTicks());
            next = next.setValue(AlarmBlock.ACTIVE, Boolean.TRUE);
        }
        if (next != state) {
            serverLevel.setBlock(worldPosition, next, Block.UPDATE_ALL);
        }
        if (responses.contains(AlarmResponse.SOUND)) {
            // The two-tone siren: tone A now, then alternating every AlarmTones.PERIOD_TICKS while it sounds.
            soundTicks = Math.max(1, NeroSecurityConfig.alarmActiveTicks());
            soundElapsed = 0;
            SecuritySounds tone = AlarmTones.toneAt(0);
            if (tone != null) {
                tone.play(serverLevel, worldPosition);
            }
        }
        if (AlarmRules.attemptsLockdown(responses)) {
            // Every other guard (server switches, severity, cooldown, never extending, this alarm being a
            // linked alarm of the installation) is applied by the service, which also reports suppression.
            InstallationService.enterLockdownByAlarm(serverLevel.getServer(), installation,
                    DevicePos.of(serverLevel, worldPosition), mine.severity(), now);
        }
        return responses.contains(AlarmResponse.CHAT);
    }

    /** Counts the two timers down and clears the properties (also clears a stale property after reload). */
    public static void serverTick(Level level, BlockPos pos, BlockState state, AlarmBlockEntity alarm) {
        if (!(level instanceof ServerLevel) || !(state.getBlock() instanceof AlarmBlock)) {
            return;
        }
        BlockState next = state;
        if (alarm.pulseTicks > 0) {
            alarm.pulseTicks--;
        }
        if (alarm.pulseTicks == 0 && state.getValue(AlarmBlock.POWERED)) {
            next = next.setValue(AlarmBlock.POWERED, Boolean.FALSE);
        }
        if (alarm.activeTicks > 0) {
            alarm.activeTicks--;
        }
        if (alarm.soundTicks > 0) {
            alarm.soundTicks--;
            alarm.soundElapsed++;
            SecuritySounds tone = alarm.soundTicks > 0 ? AlarmTones.toneAt(alarm.soundElapsed) : null;
            if (tone != null) {
                tone.play(level, pos);
            }
        }
        if (alarm.activeTicks == 0 && state.getValue(AlarmBlock.ACTIVE)) {
            next = next.setValue(AlarmBlock.ACTIVE, Boolean.FALSE);
        }
        if (next != state) {
            level.setBlock(pos, next, Block.UPDATE_ALL);
        }
    }

    // --- breaking -------------------------------------------------------------------------------

    /**
     * Called from the block's {@code playerWillDestroy}. Anyone may break an alarm; a breaker the resolver
     * would not let configure it raises {@code DEVICE_FORCED}. Never cancels.
     */
    public void onPlayerBreak(ServerPlayer breaker) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        this.playerBreakAt = serverLevel.getGameTime();
        Installation installation = installation();
        if (installation == null) {
            return;
        }
        boolean couldConfigure;
        try {
            couldConfigure = AccessResolver.DEFAULT.resolve(breaker, installation, AccessAction.CONFIGURE_DEVICE,
                    DevicePos.of(serverLevel, worldPosition)).allowed();
        } catch (RuntimeException | LinkageError e) {
            couldConfigure = false;
        }
        if (!couldConfigure) {
            publish(serverLevel, SecurityEventType.DEVICE_FORCED, installation, breaker.getUUID());
        }
    }

    /**
     * The block is being removed (not unloaded). If it was linked: a non-player removal raises
     * {@code DEVICE_FORCED} with no actor, and the link is dropped so no ghost device remains.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        this.removing = true;
        super.preRemoveSideEffects(pos, state);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        try {
            MinecraftServer server = serverLevel.getServer();
            DevicePos at = DevicePos.of(serverLevel, pos);
            Installation installation = SecurityInstallations.get(server).atDevice(at).orElse(null);
            if (installation == null) {
                return;
            }
            if (playerBreakAt != serverLevel.getGameTime()) {
                publish(serverLevel, SecurityEventType.DEVICE_FORCED, installation, null);
            }
            InstallationService.unlinkByLoss(server, installation, at);
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] alarm removal bookkeeping failed ({})",
                    e.getClass().getName());
        }
    }

    /** Chunk unload or removal: transient state only, no world access. */
    @Override
    public void setRemoved() {
        this.activeTicks = 0;
        this.pulseTicks = 0;
        super.setRemoved();
    }

    private void publish(ServerLevel serverLevel, SecurityEventType type, Installation installation,
            @Nullable UUID actor) {
        SecurityEventBus.publish(SecurityEvent.of(type, installation.id(), DevicePos.of(serverLevel, worldPosition),
                actor, System.currentTimeMillis()));
    }

    // --- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        List<String> types = new ArrayList<>();
        for (SecurityEventType type : subscribed) {
            types.add(AlarmRules.typeKey(type));
        }
        List<String> answers = new ArrayList<>();
        for (AlarmResponse response : responses) {
            answers.add(response.key());
        }
        output.store(KEY_SUBSCRIBED, NAMES, types);
        output.store(KEY_RESPONSES, NAMES, answers);
        output.putLong(KEY_SILENCED_UNTIL, silencedUntil);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.subscribed = input.read(KEY_SUBSCRIBED, NAMES).map(AlarmRules::typesFromNames)
                .orElseGet(AlarmRules::defaultSubscribed);
        this.responses = input.read(KEY_RESPONSES, NAMES).map(AlarmRules::responsesFromNames)
                .orElseGet(AlarmRules::defaultResponses);
        this.silencedUntil = Math.max(0L, input.getLongOr(KEY_SILENCED_UNTIL, 0L));
    }
}

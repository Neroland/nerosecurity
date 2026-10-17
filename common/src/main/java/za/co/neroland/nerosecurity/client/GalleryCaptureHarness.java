package za.co.neroland.nerosecurity.client;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerosecurity.command.GalleryLayout;

/**
 * CLIENT, creative-debug: a reproducible screenshot pass over the {@code /nerosecurity gallery} scene.
 * The server command builds the exhibit; this harness pins time and weather, drives the camera through a
 * hand-framed shot list with every overlay hidden, and writes one PNG per shot to
 * {@code screenshots/nerosecurity/<shot>.png}, so the gallery can be re-shot identically after any texture
 * or model change instead of being framed by hand.
 *
 * <p><b>Commands</b> (client side; they never reach the server dispatcher). The root is {@code /nsecgallery},
 * NOT {@code /nerosecurity}: a client-dispatcher root that shares its name with a server command shadows the
 * server subtree (the client parser matches the root, fails on the server-only child and never forwards), the
 * same reason {@code /nsber} has its own root.
 * <ul>
 *   <li>{@code /nsecgallery capture [time]}: freezes time and weather, rebuilds the gallery at the spot you
 *       stand on ({@code gallery clear} then {@code gallery}, so a rerun never stacks blocks or keeps stale
 *       device state), strips the floating labels, then shoots the list. {@code time} is
 *       {@code day}/{@code noon}/{@code night}/{@code midnight} or a tick count; the default is {@code night},
 *       which shows the beams, LEDs and fields best against the exhibit's own lighting.</li>
 *   <li>{@code /nsecgallery capture shot <name>}: captures the current view once, overlays hidden, to
 *       {@code nerosecurity/<name>.png}.</li>
 * </ul>
 *
 * <p>Every pose is relative to the origin the run started at (the player's block position), so the shots
 * match the {@link GalleryLayout} plan wherever the gallery is built. The HUD, clouds and view-bob are
 * suppressed for the run and restored afterwards. Loader-agnostic: each loader wires
 * {@link #registerClientCommands(CommandDispatcher)} from its client-command hook (next to {@code /nsber})
 * and {@link #tick()} from its client-tick hook. Nothing here records a player, a name or a position.
 */
public final class GalleryCaptureHarness {

    /** Ticks for a fresh frame to render at a pinned pose before grabbing. */
    private static final int SETTLE_TICKS = 12;
    /** Ticks after the teleport and environment pins for chunks to load before building. */
    private static final int BUILD_WARMUP_TICKS = 60;
    /** Ticks after the build for devices to arm, fields to form, the lever door to open and the label kill to settle. */
    private static final int SCENE_TICKS = 70;

    private static final String SUBFOLDER = "nerosecurity";

    /**
     * One framed capture. {@code setup} runs first (teleport, pins), {@code build} after {@link #BUILD_WARMUP_TICKS},
     * {@code cleanup} after a further {@link #SCENE_TICKS}; {@code camera}/{@code target} give the pose, or are
     * null to keep the current view.
     */
    private record Shot(String name, List<String> setup, List<String> build, List<String> cleanup, int warmup,
            Vec3 camera, Vec3 target) {
    }

    private enum Phase { MOVE, WARMUP, SCENE, SETTLE, SHOOT }

    private static final Deque<Shot> QUEUE = new ArrayDeque<>();
    private static boolean running;
    private static boolean hudWasHidden;
    private static boolean bobViewWas;
    private static boolean flyingWas;
    private static CloudStatus cloudsWere;
    private static Phase phase = Phase.MOVE;
    private static int warmup;
    private static int sceneWait;
    private static int settle;
    private static Shot current;

    private GalleryCaptureHarness() {
    }

    // --- registration (generic in the brigadier source type; the bodies never read the source) --------------

    /**
     * Registers the client-side {@code /nsecgallery capture ...} tree. Generic in {@code S} so one call serves
     * NeoForge and Forge ({@code CommandSourceStack}) and Fabric ({@code FabricClientCommandSource}).
     */
    public static <S> void registerClientCommands(CommandDispatcher<S> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<S>literal("nsecgallery")
                        .then(LiteralArgumentBuilder.<S>literal("capture")
                                .executes(ctx -> startCapture("night"))
                                .then(LiteralArgumentBuilder.<S>literal("shot")
                                        .then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                                                .executes(ctx -> shotHere(StringArgumentType.getString(ctx, "name")))))
                                .then(RequiredArgumentBuilder.<S, String>argument("time", StringArgumentType.word())
                                        .executes(ctx -> startCapture(StringArgumentType.getString(ctx, "time"))))));
    }

    // --- run starters ------------------------------------------------------------------------------------

    private static int startCapture(String time) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        if (running) {
            mc.player.sendSystemMessage(Component.literal("NeroSecurity gallery capture already running."));
            return 0;
        }
        BlockPos origin = mc.player.blockPosition();
        QUEUE.clear();
        QUEUE.addAll(galleryQueue(origin, time));
        begin(mc);
        mc.player.sendSystemMessage(Component.literal("NeroSecurity gallery capture: rebuilding the exhibit here, time="
                + time + ", " + QUEUE.size() + " shots -> screenshots/" + SUBFOLDER + "/ (overlays hidden)."));
        return Command.SINGLE_SUCCESS;
    }

    private static int shotHere(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || running) {
            return 0;
        }
        QUEUE.clear();
        QUEUE.add(new Shot(name, List.of(), List.of(), List.of(), 0, null, null));
        begin(mc);
        return Command.SINGLE_SUCCESS;
    }

    /** The shot list, with the one-time rebuild and environment pins attached to the first shot. */
    private static List<Shot> galleryQueue(BlockPos origin, String time) {
        List<Shot> shots = new ArrayList<>(buildShots(origin));
        List<String> setup = List.of(
                "tp @s " + (origin.getX() + 0.5) + " " + origin.getY() + " " + (origin.getZ() + 0.5),
                "gamerule advance_time false",    // 26.1 renamed doDaylightCycle -> advance_time
                "gamerule advance_weather false", // ...and doWeatherCycle -> advance_weather
                "time set " + time,
                "weather clear");
        // Built after the warmup, once chunks are loaded. The server command clears any earlier gallery at
        // this origin itself, so the rebuild never stacks; the explicit clear first keeps a rerun honest even
        // if a previous run was interrupted half-way.
        List<String> build = List.of(
                "nerosecurity gallery clear",
                "nerosecurity gallery");
        // After the scene settles: strip the floating labels (gallery-tagged marker stands only) and sweep any
        // litter, so the frames are clean.
        List<String> cleanup = List.of(
                "kill @e[type=armor_stand,tag=" + GalleryLayout.ENTITY_TAG + ",distance=..64]",
                "kill @e[type=item,distance=..64]",
                "kill @e[type=experience_orb,distance=..64]");
        Shot first = shots.get(0);
        shots.set(0, new Shot(first.name(), setup, build, cleanup, BUILD_WARMUP_TICKS, first.camera(), first.target()));
        return shots;
    }

    private static void begin(Minecraft mc) {
        new File(mc.gameDirectory, "screenshots/" + SUBFOLDER).mkdirs();
        hudWasHidden = isHudHidden(mc);
        setHudHidden(mc, true);
        cloudsWere = mc.options.cloudStatus().get();
        mc.options.cloudStatus().set(CloudStatus.OFF);
        bobViewWas = mc.options.bobView().get();
        mc.options.bobView().set(false);
        flyingWas = mc.player != null && mc.player.getAbilities().flying;
        setFlying(mc, true);
        running = true;
        phase = Phase.MOVE;
        current = null;
    }

    private static void setFlying(Minecraft mc, boolean fly) {
        if (mc.player == null) {
            return;
        }
        mc.player.getAbilities().flying = fly && mc.player.getAbilities().mayfly;
        mc.player.onUpdateAbilities();
    }

    // --- per-client-tick state machine -----------------------------------------------------------------------

    /** Called once per client tick from each loader's client-tick hook. No-op unless a run is live. */
    public static void tick() {
        if (!running) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            finish(mc);
            return;
        }
        switch (phase) {
            case MOVE -> {
                Shot shot = QUEUE.poll();
                current = shot;
                if (shot == null) {
                    finish(mc);
                    return;
                }
                for (String cmd : shot.setup()) {
                    player.connection.sendCommand(cmd);
                }
                warmup = shot.warmup();
                phase = Phase.WARMUP;
            }
            case WARMUP -> {
                Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                holdStill(mc, player);
                if (--warmup <= 0) {
                    for (String cmd : shot.build()) {
                        player.connection.sendCommand(cmd);
                    }
                    sceneWait = shot.build().isEmpty() ? 0 : SCENE_TICKS;
                    phase = Phase.SCENE;
                }
            }
            case SCENE -> {
                Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                holdStill(mc, player);
                if (--sceneWait <= 0) {
                    for (String cmd : shot.cleanup()) {
                        player.connection.sendCommand(cmd);
                    }
                    applyPose(player, shot);
                    settle = SETTLE_TICKS;
                    phase = Phase.SETTLE;
                }
            }
            case SETTLE -> {
                Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                applyPose(player, shot); // re-pinned every tick so nothing drifts the camera
                if (--settle <= 0) {
                    phase = Phase.SHOOT;
                }
            }
            case SHOOT -> {
                Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                grab(mc, shot.name());
                phase = Phase.MOVE;
            }
        }
    }

    private static void holdStill(Minecraft mc, LocalPlayer player) {
        if (!player.getAbilities().flying && player.getAbilities().mayfly) {
            setFlying(mc, true);
        }
        player.setDeltaMovement(Vec3.ZERO);
    }

    /** Snaps the player (the render camera) to the shot's pose. */
    private static void applyPose(LocalPlayer player, Shot shot) {
        Vec3 cam = shot.camera();
        Vec3 tgt = shot.target();
        if (cam == null || tgt == null) {
            return;
        }
        double dx = tgt.x - cam.x;
        double dy = tgt.y - cam.y;
        double dz = tgt.z - cam.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        player.snapTo(cam.x, cam.y, cam.z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, SUBFOLDER + "/" + name + ".png", mainRenderTarget(mc), 1, msg -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage(msg);
            }
        });
    }

    private static void finish(Minecraft mc) {
        setHudHidden(mc, hudWasHidden);
        if (cloudsWere != null) {
            mc.options.cloudStatus().set(cloudsWere);
        }
        mc.options.bobView().set(bobViewWas);
        setFlying(mc, flyingWas);
        running = false;
        current = null;
        QUEUE.clear();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("NeroSecurity gallery capture done: see screenshots/"
                    + SUBFOLDER + "/."));
        }
    }

    // --- HUD flag and main framebuffer: the API moved in 26.2 --------------------------------------------------

    private static boolean isHudHidden(Minecraft mc) {
        //? if <26.2 {
        /*return mc.options.hideGui;
        *///?} else {
        return mc.gui.hud.isHidden();
        //?}
    }

    private static void setHudHidden(Minecraft mc, boolean hidden) {
        //? if <26.2 {
        /*mc.options.hideGui = hidden;
        *///?} else {
        if (mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
        //?}
    }

    private static RenderTarget mainRenderTarget(Minecraft mc) {
        //? if <26.2 {
        /*return mc.getMainRenderTarget();
        *///?} else {
        return mc.gameRenderer.mainRenderTarget();
        //?}
    }

    // --- the shot list (poses relative to the origin; mirrors GalleryLayout, tune the two together) ------------

    private static List<Shot> buildShots(BlockPos o) {
        List<Shot> shots = new ArrayList<>();
        // The alarm's active light lasts ten seconds after the build, so the shots that show it come first.
        shots.add(shot(o, "alarm", 3, 5.2, -11, 0, 4.4, -17));                 // the beacon on its post
        shots.add(shot(o, "hero", 13, 8.5, 2, -1, 2, -19));                    // the whole exhibit, 3/4 from the south-east
        shots.add(shot(o, "hub", 3.5, 3.6, -7, 0, 2.6, -13));                  // hub on the plinth, programmer and monitor
        shots.add(shot(o, "doors", 0, 3.2, -11, 0, 2, -22));                  // closed, blast (locked) and lever-held open
        shots.add(shot(o, "door_open", 3, 2.6, -17, 6.5, 2, -22));            // the open door and its lever
        shots.add(shot(o, "laser_corridor", 9, 2.6, -24, 9, 2, -33));          // down the corridor to the camera
        shots.add(shot(o, "laser_grid", 7, 2, -25, 9.5, 2, -29));             // low 3/4 so all three beams read
        shots.add(shot(o, "field_wall", -9, 2.8, -22.5, -9, 3, -32));          // box in front of the wall
        shots.add(shot(o, "field_box", -5.5, 2.4, -24, -9, 3, -28));           // the floating shell, close
        shots.add(shot(o, "keycard_rack", 4, 2.3, -10, 8.5, 2, -10));          // six frames, levels left to right
        shots.add(shot(o, "item_rack", -4, 2.3, -10, -8.5, 2, -10));           // tool and modules
        shots.add(shot(o, "chest", 2, 2.4, -5, 0, 1.6, -8));                   // secure chest, lid and LEDs
        shots.add(shot(o, "plaza_camera", 7, 2.4, -16, 10, 3.2, -21));         // the camera over the plaza
        shots.add(shot(o, "denied", 0, 3.6, -34, 0, 2.2, -46));                // the access-denied bay down the axis
        shots.add(shot(o, "denied_cell", -3.5, 2.8, -41, -8, 2, -46.5));       // the visitor behind the field, beams on it
        return shots;
    }

    /** A camera-only shot whose pose is an offset from the origin. */
    private static Shot shot(BlockPos o, String name, double cx, double cy, double cz, double tx, double ty, double tz) {
        return new Shot(name, List.of(), List.of(), List.of(), 0,
                new Vec3(o.getX() + 0.5 + cx, o.getY() + cy, o.getZ() + 0.5 + cz),
                new Vec3(o.getX() + 0.5 + tx, o.getY() + ty, o.getZ() + 0.5 + tz));
    }
}

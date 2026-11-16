package za.co.neroland.nerosecurity.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerolandcore.map.MapArea;
import za.co.neroland.nerolandcore.map.MapAreaStyle;
import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLabel;
import za.co.neroland.nerolandcore.map.MapLayerInfo;
import za.co.neroland.nerolandcore.map.MapLayerProvider;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;
import za.co.neroland.nerolandcore.map.MapLayerSink;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapScope;
import za.co.neroland.nerolandcore.map.MapViewer;

/**
 * NeroSecurity's map layers, offered through Neroland Core's map registry; NeroSecurity depends on no map mod.
 *
 * <p>Three {@link MapScope#MEMBERS} layers, all scoped by {@link SecurityMapAccess} (the link module's rule: owner,
 * officers and members, no proximity path at all):
 * <ul>
 *   <li>{@code installations}: the hub, labelled with the installation's name. Minimap and world map.</li>
 *   <li>{@code installation_devices}: every other linked device. World map only by default.</li>
 *   <li>{@code fields}: the footprint of each force field whose emitter is loaded, enabled and validly set.</li>
 * </ul>
 * No id, label or detail carries a player's name or id; ids come from installation ids and block positions.
 */
public final class SecurityMapLayers implements MapLayerProvider {

    private static final Logger LOG = LoggerFactory.getLogger("NeroSecurity/Map");

    public static final Identifier INSTALLATIONS = id("installations");
    public static final Identifier DEVICES = id("installation_devices");
    public static final Identifier FIELDS = id("fields");

    static final Identifier ICON_INSTALLATION = id("installation");
    static final Identifier ICON_FIELD = id("field");

    private static final Identifier EMITTER = id("force_field_emitter");
    private static final String KEY_PATTERN = "[a-z0-9_.-]{1,128}";
    private static final int SKY = 0xFF56B4E9;
    private static final int SKY_FILL = 0x3356B4E9;

    private static final List<MapLayerInfo> LAYERS = List.of(
            MapLayerInfo.of(INSTALLATIONS, "map.layer.nerosecurity.installations", ICON_INSTALLATION, MapScope.MEMBERS),
            MapLayerInfo.of(DEVICES, "map.layer.nerosecurity.installation_devices", ICON_INSTALLATION,
                    MapScope.MEMBERS).withDefaults(true, false),
            MapLayerInfo.of(FIELDS, "map.layer.nerosecurity.fields", ICON_FIELD, MapScope.MEMBERS));

    private final Function<MinecraftServer, SecurityMapSource> sources;

    private SecurityMapLayers(Function<MinecraftServer, SecurityMapSource> sources) {
        this.sources = sources;
    }

    /** A provider over {@code sources}; the live one is {@link SecurityMapSource#of}. */
    public static SecurityMapLayers create(Function<MinecraftServer, SecurityMapSource> sources) {
        return new SecurityMapLayers(sources);
    }

    /** Registers the provider with Core's map registry. Never throws. */
    public static void register() {
        try {
            MapLayerRegistry.register(create(SecurityMapSource::of));
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("NeroSecurity map layers not registered ({})", e.getClass().getSimpleName());
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, path);
    }

    @Override
    public String modId() {
        return NeroSecurityCommon.MOD_ID;
    }

    @Override
    public List<MapLayerInfo> layers() {
        return LAYERS;
    }

    @Override
    public void query(MapViewer viewer, ResourceKey<Level> dimension, MapBounds bounds, MapLayerSink sink) {
        if (viewer == null || dimension == null || bounds == null || sink == null) {
            return;
        }
        SecurityMapSource source = sources.apply(viewer.server());
        if (source == null) {
            return;
        }
        List<Installation> visible = SecurityMapAccess.visible(source.server(), source.resolver(),
                source.installations(), viewer.id());
        if (visible.isEmpty()) {
            return;
        }
        Identifier dim = dimension.identifier();
        BlockPos from = viewer.position().orElse(new BlockPos(bounds.centreX(), 64, bounds.centreZ()));
        List<Device> devices = new ArrayList<>();
        for (Installation installation : visible) {
            DevicePos hub = installation.hubPos();
            if (hub != null && dim.equals(hub.dimension()) && !sink.isFull(INSTALLATIONS)) {
                sink.marker(MapMarker.builder("hub/" + installation.id(), INSTALLATIONS, dimension, hub.pos())
                        .label(MapLabel.literal(installation.name())).build());
            }
            for (Map.Entry<DevicePos, Identifier> device : installation.devices().entrySet()) {
                DevicePos at = device.getKey();
                if (dim.equals(at.dimension()) && !at.equals(hub)) {
                    devices.add(new Device(at, device.getValue()));
                }
            }
        }
        devices.sort(Comparator.comparingDouble(d -> d.at().pos().distSqr(from)));
        for (Device device : devices) {
            BlockPos p = device.at().pos();
            String key = p.getX() + "_" + p.getY() + "_" + p.getZ();
            if (!sink.isFull(DEVICES)) {
                sink.marker(MapMarker.builder("device/" + key, DEVICES, dimension, p)
                        .label(MapLabel.translatable(deviceKey(device.kind()))).build());
            }
            if (EMITTER.equals(device.kind()) && !sink.isFull(FIELDS)) {
                Optional<SecurityMapSource.Footprint> field = source.field(dimension, device.at());
                field.ifPresent(f -> sink.area(new MapArea.Rect("field/" + key, FIELDS, dimension,
                        MapAreaStyle.of(SKY_FILL, SKY), f.minX(), f.minZ(), f.maxX(), f.maxZ())));
            }
            if (sink.isFull(DEVICES) && sink.isFull(FIELDS)) {
                return;
            }
        }
    }

    private record Device(DevicePos at, Identifier kind) {
    }

    /** The device block's own lang key, or a generic one. */
    static String deviceKey(Identifier kind) {
        if (kind == null) {
            return "map.nerosecurity.device";
        }
        String key = "block." + kind.getNamespace() + "." + kind.getPath().replace('/', '.');
        return key.matches(KEY_PATTERN) ? key : "map.nerosecurity.device";
    }
}

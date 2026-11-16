package za.co.neroland.nerosecurity.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLayerConformance;
import za.co.neroland.nerolandcore.map.MapLayerInfo;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;
import za.co.neroland.nerolandcore.map.MapQueryResult;
import za.co.neroland.nerolandcore.map.MapScope;
import za.co.neroland.nerolandcore.map.MapViewer;
import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessSource;
import za.co.neroland.nerosecurity.access.AccessVote;
import za.co.neroland.nerosecurity.access.DenyReason;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.field.FieldSettings;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.map.SecurityMapAccess;
import za.co.neroland.nerosecurity.map.SecurityMapLayers;
import za.co.neroland.nerosecurity.map.SecurityMapSource;

/**
 * NeroSecurity's map layers against Core's {@link MapLayerConformance}, and G6 parity: the map's re-implemented
 * rule ({@link SecurityMapAccess}) and the link module's own {@link SecurityLinkAccess#visible} return the same
 * installations for owner, officer, member, stranger, erased-owner and denying-source fixtures, over the same store
 * and resolver. Lives in the link test package so it can call the package-private link rule directly.
 */
class SecurityMapLayersTest {

    static final ResourceKey<Level> HOME = ResourceKey.create(Registries.DIMENSION, FakeLinkContext.OVERWORLD);
    static final ResourceKey<Level> MOON = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath("nerosecurity_test", "moon_test"));

    final FakeLinkContext ctx = new FakeLinkContext();
    final UUID owner = UUID.fromString("00000000-0000-4000-8000-000000000001");
    final UUID stranger = UUID.fromString("00000000-0000-4000-8000-000000000002");
    final UUID third = UUID.fromString("00000000-0000-4000-8000-000000000003");
    final UUID officer = UUID.fromString("00000000-0000-4000-8000-000000000004");
    final UUID member = UUID.fromString("00000000-0000-4000-8000-000000000005");
    Installation lab;
    Installation ridge;
    DevicePos emitter;

    SecurityMapLayersTest() {
        lab = ctx.install(owner, "Orbital Lab", 0);
        ctx.store.setMember(lab.id(), officer, AccessLevel.OFFICER, 64);
        ctx.store.setMember(lab.id(), member, AccessLevel.MEMBER, 64);
        emitter = ctx.link(lab, "force_field_emitter", 4, 64, 0);
        ctx.link(lab, "security_camera", -3, 66, 2);
        ridge = ctx.install(third, "Ridge", 30);
        ctx.link(ridge, "force_field_emitter", 33, 64, 3);
    }

    SecurityMapSource source() {
        return new SecurityMapSource() {
            @Override
            public MinecraftServer server() {
                return null;
            }

            @Override
            public SecurityInstallations installations() {
                return ctx.store;
            }

            @Override
            public AccessResolver resolver() {
                return ctx.resolver();
            }

            @Override
            public Optional<Footprint> field(ResourceKey<Level> dimension, DevicePos at) {
                BlockPos p = at.pos();
                return Optional.of(SecurityMapSource.footprint(p.getX(), p.getZ(), 0, 0, -1, FieldSettings.DEFAULT));
            }
        };
    }

    SecurityMapLayers provider() {
        SecurityMapSource source = source();
        return SecurityMapLayers.create(server -> source);
    }

    static MapViewer at(UUID id, int x, int z) {
        return MapViewer.forTest(id, null, HOME, new BlockPos(x, 64, z));
    }

    @Test
    void conformance() {
        MapLayerConformance.create(provider())
                .viewers(at(owner, 0, 0), at(stranger, 5, 0))
                .dimensions(HOME, MOON)
                .bounds(MapBounds.around(0, 0, 512))
                .farBounds(MapBounds.around(20_000, 20_000, 256))
                .forbiddenText("Trudy", "Sam")
                .forbiddenIds(third, officer, member)
                .expectEntitledSees(SecurityMapLayers.INSTALLATIONS, 1)
                .expectEntitledSees(SecurityMapLayers.DEVICES, 2)
                .expectEntitledSees(SecurityMapLayers.FIELDS, 1)
                .verify();
    }

    @Test
    void conformanceForAMemberViewer() {
        MapLayerConformance.create(provider())
                .viewers(at(member, 0, 0), at(stranger, 5, 0))
                .dimensions(HOME, MOON)
                .bounds(MapBounds.around(0, 0, 512))
                .farBounds(MapBounds.around(20_000, 20_000, 256))
                .forbiddenIds(owner, third, officer)
                .expectEntitledSees(SecurityMapLayers.INSTALLATIONS, 1)
                .expectEntitledSees(SecurityMapLayers.DEVICES, 2)
                .expectEntitledSees(SecurityMapLayers.FIELDS, 1)
                .verify();
    }

    @Test
    void everyLayerIsMembersScopedAndDevicesStayOffTheMinimap() {
        for (MapLayerInfo info : provider().layers()) {
            assertEquals(MapScope.MEMBERS, info.scope(), info.id().toString());
        }
        assertFalse(provider().layers().get(1).defaultOnMinimap());
        assertTrue(provider().layers().get(0).defaultOnMinimap());
    }

    private Set<UUID> link(UUID viewer) {
        return SecurityLinkAccess.visible(ctx, ctx.store, viewer).stream().map(Installation::id)
                .collect(Collectors.toSet());
    }

    private Set<UUID> map(UUID viewer) {
        return SecurityMapAccess.visible(null, ctx.resolver(), ctx.store, viewer).stream().map(Installation::id)
                .collect(Collectors.toSet());
    }

    private Set<String> hubs(UUID viewer) {
        MapLayerRegistry.register(provider());
        MapQueryResult r = MapLayerRegistry.query(NeroSecurityCommon.MOD_ID, MapViewer.forTest(viewer, null, null,
                null), HOME, MapBounds.around(0, 0, 1024));
        assertFalse(r.failed());
        Set<String> out = new HashSet<>();
        r.markers().stream().filter(m -> m.id().startsWith("hub/")).forEach(m -> out.add(m.id()));
        return out;
    }

    private void assertParity(List<UUID> viewers, String when) {
        for (UUID viewer : viewers) {
            Set<UUID> fromLink = link(viewer);
            assertEquals(fromLink, map(viewer), when + ": viewer " + viewer);
            assertEquals(fromLink.stream().map(id -> "hub/" + id).collect(Collectors.toSet()), hubs(viewer),
                    when + ": provider, viewer " + viewer);
        }
    }

    @Test
    void parityWithTheLinkRule() {
        List<UUID> viewers = List.of(owner, officer, member, stranger, third, Installation.TOMBSTONE_OWNER);
        assertParity(viewers, "seeded");
        assertEquals(Set.of(lab.id()), map(member));
        assertTrue(map(stranger).isEmpty());

        ctx.store.erase(owner);
        assertParity(viewers, "owner erased");
        assertTrue(map(owner).isEmpty(), "an erased owner sees nothing");
        assertEquals(Set.of(lab.id()), map(officer), "members keep the orphaned installation, as the link does");

        ctx.admin = (server, actor) -> true;
        assertParity(viewers, "operator");
        ctx.admin = za.co.neroland.nerosecurity.access.AdminCheck.NONE;

        ctx.sources.add(new AccessSource() {
            @Override
            public Identifier id() {
                return Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "test_deny");
            }

            @Override
            public AccessVote vote(za.co.neroland.nerosecurity.access.AccessQuery query) {
                return new AccessVote.Deny(DenyReason.SOURCE_DENY, "access.nerosecurity.deny.source");
            }
        });
        assertParity(viewers, "denying source");
        assertTrue(map(third).isEmpty());
    }

    @Test
    void fieldFootprintFollowsTheFacing() {
        // Default plane: offset 2, 5 wide, 5 high. Facing north from (4, 0): centre (4, -2), five blocks across x.
        SecurityMapSource.Footprint north = SecurityMapSource.footprint(4, 0, 0, 0, -1, FieldSettings.DEFAULT);
        assertEquals(new SecurityMapSource.Footprint(2, -2, 6, -2), north);
        SecurityMapSource.Footprint east = SecurityMapSource.footprint(4, 0, 1, 0, 0, FieldSettings.DEFAULT);
        assertEquals(new SecurityMapSource.Footprint(6, -2, 6, 2), east);
        SecurityMapSource.Footprint up = SecurityMapSource.footprint(4, 0, 0, 1, 0, FieldSettings.DEFAULT);
        assertEquals(new SecurityMapSource.Footprint(2, -2, 6, 2), up);
    }
}

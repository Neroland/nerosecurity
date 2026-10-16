package za.co.neroland.nerosecurity.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client-only {@code /nsber} command: prints the last {@link BerStats#WINDOW}-frame block-entity-renderer average
 * ({@code /nsber}) or clears it ({@code /nsber reset}). Used to fill in {@code docs/BER-BUDGET.md}.
 *
 * <p>The root is deliberately NOT {@code /nerosecurity}: a client-dispatcher root that shares its name with a
 * server command shadows the server subtree. The bodies never read the command source, so the registration is
 * generic in the brigadier source type and one call serves all three loaders. The output holds only counts and
 * timings, never a player, position or name.
 */
public final class BerCommand {

    private BerCommand() {
    }

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("nsber")
                .executes(ctx -> print())
                .then(LiteralArgumentBuilder.<S>literal("reset").executes(ctx -> {
                    BerStats.reset();
                    say("NeroSecurity BER stats reset.");
                    return 1;
                })));
    }

    private static int print() {
        say(BerStats.f3Line());
        say(BerStats.record());
        return 1;
    }

    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        }
    }
}

package za.co.neroland.nerosecurity.mixin;

//? if <1.20.3 {
/*import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import za.co.neroland.nerosecurity.block.field.ForceFieldBlock;

@Mixin(Explosion.class)
abstract class LegacyFieldExplosionMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private float radius;

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void nerosecurity$protectFields(boolean spawnParticles, CallbackInfo callback) {
        Explosion explosion = (Explosion) (Object) this;
        if (!(level instanceof ServerLevel server) || !explosion.interactsWithBlocks()) {
            return;
        }
        explosion.getToBlow().removeIf(pos -> {
            if (!(server.getBlockState(pos).getBlock() instanceof ForceFieldBlock)) {
                return false;
            }
            ForceFieldBlock.absorbExplosion(server, pos, explosion, radius);
            return true;
        });
    }
}
*///?}

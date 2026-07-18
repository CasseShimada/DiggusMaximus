package net.kyrptonaught.diggusmaximus.mixin;

import net.kyrptonaught.diggusmaximus.DiggingPlayerEntity;
import net.kyrptonaught.diggusmaximus.DiggusMaximusMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Block.class)
public class MixinBlock {
    @Redirect(
            method = "playerDestroy",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V")
    )
    private void diggus$adjustExcavationExhaustion(Player player, float exhaustion) {
        if (!((DiggingPlayerEntity) player).diggus$isExcavating()) {
            player.causeFoodExhaustion(exhaustion);
        } else if (DiggusMaximusMod.getOptions().playerExhaustion) {
            player.causeFoodExhaustion(exhaustion * DiggusMaximusMod.getOptions().exhaustionMultiplier);
        }
    }
}

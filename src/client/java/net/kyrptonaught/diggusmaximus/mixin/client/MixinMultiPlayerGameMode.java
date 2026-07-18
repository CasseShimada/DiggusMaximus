package net.kyrptonaught.diggusmaximus.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kyrptonaught.diggusmaximus.DiggusMaximusMod;
import net.kyrptonaught.diggusmaximus.StartExcavatePacket;
import net.kyrptonaught.diggusmaximus.client.DiggusKeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void diggus$requestExcavation(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (minecraft.player == null || minecraft.level == null || !ClientPlayNetworking.canSend(StartExcavatePacket.TYPE)) {
            return;
        }

        int shapeSelection = -1;
        Direction facing = null;
        if (DiggusMaximusMod.getOptions().enabled && DiggusKeyMappings.activationPressed()) {
            // Ordinary excavation takes precedence, matching the old client mixin.
        } else if (DiggusMaximusMod.getExcavatingShapes().enableShapes && DiggusKeyMappings.shapePressed()) {
            var hit = minecraft.player.pick(minecraft.player.blockInteractionRange() + 1.0, 1.0F, false);
            if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(pos)) {
                return;
            }
            facing = blockHit.getDirection();
            shapeSelection = DiggusMaximusMod.getExcavatingShapes().selectedShape.ordinal();
        } else {
            return;
        }

        var blockID = BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(pos).getBlock());
        ClientPlayNetworking.send(new StartExcavatePacket(pos, blockID, facing == null ? -1 : facing.get3DDataValue(), shapeSelection));
    }
}

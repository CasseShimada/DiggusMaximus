package net.kyrptonaught.diggusmaximus.mixin;

import net.kyrptonaught.diggusmaximus.DiggingPlayerEntity;
import net.kyrptonaught.diggusmaximus.PendingExcavation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Player.class)
public class MixinPlayerEntity implements DiggingPlayerEntity {
    @Unique
    private boolean diggus$excavating;
    @Unique
    private PendingExcavation diggus$pendingExcavation;
    @Unique
    private long diggus$requestTokenTick = Long.MIN_VALUE;
    @Unique
    private int diggus$requestTokens = 8;

    @Override
    public boolean diggus$isExcavating() {
        return diggus$excavating;
    }

    @Override
    public void diggus$setExcavating(boolean excavating) {
        this.diggus$excavating = excavating;
    }

    @Override
    public PendingExcavation diggus$getPendingExcavation() {
        return diggus$pendingExcavation;
    }

    @Override
    public void diggus$setPendingExcavation(PendingExcavation pending) {
        this.diggus$pendingExcavation = pending;
    }

    @Override
    public boolean diggus$tryConsumeRequestToken(long gameTime) {
        if (diggus$requestTokenTick == Long.MIN_VALUE) {
            diggus$requestTokenTick = gameTime;
        } else if (gameTime > diggus$requestTokenTick) {
            long elapsed = gameTime - diggus$requestTokenTick;
            diggus$requestTokens = (int) Math.min(8L, diggus$requestTokens + elapsed);
            diggus$requestTokenTick = gameTime;
        }
        if (diggus$requestTokens <= 0) {
            return false;
        }
        diggus$requestTokens--;
        return true;
    }
}

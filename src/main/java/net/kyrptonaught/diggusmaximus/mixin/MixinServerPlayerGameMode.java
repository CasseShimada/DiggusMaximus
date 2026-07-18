package net.kyrptonaught.diggusmaximus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.kyrptonaught.diggusmaximus.DiggingPlayerEntity;
import net.kyrptonaught.diggusmaximus.DiggusMaximusMod;
import net.kyrptonaught.diggusmaximus.Excavate;
import net.kyrptonaught.diggusmaximus.PendingExcavation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Set;
import java.util.UUID;

@Mixin(ServerPlayerGameMode.class)
public class MixinServerPlayerGameMode {
    @Shadow
    @Final
    protected ServerPlayer player;
    @Shadow
    protected ServerLevel level;
    @Shadow
    private boolean isDestroyingBlock;
    @Shadow
    private BlockPos destroyPos;
    @Shadow
    private boolean hasDelayedDestroy;
    @Shadow
    private BlockPos delayedDestroyPos;

    @Unique
    private int diggus$breakDepth;

    /**
     * Arms the request before vanilla may call destroyBlock, then retains it only when vanilla's
     * own mining state machine accepted responsibility for this exact position. This preserves
     * arbitrarily slow mining without turning a rejected action into an unbounded authorization.
     */
    @WrapMethod(method = "handleBlockBreakAction")
    private void diggus$wrapVanillaBreakAction(
            BlockPos pos,
            ServerboundPlayerActionPacket.Action action,
            net.minecraft.core.Direction direction,
            int worldHeight,
            int sequence,
            Operation<Void> original
    ) {
        boolean wasDestroyingSamePosition = isDestroyingBlock && destroyPos.equals(pos);
        diggus$bindRequestToVanillaAction(pos, action);
        boolean completed = false;
        try {
            original.call(pos, action, direction, worldHeight, sequence);
            completed = true;
        } finally {
            DiggingPlayerEntity diggingPlayer = (DiggingPlayerEntity) player;
            PendingExcavation pending = diggingPlayer.diggus$getPendingExcavation();
            if (pending != null && pending.boundToVanillaAction()) {
                boolean vanillaOwnsRequest = completed && switch (action) {
                    case START_DESTROY_BLOCK -> !wasDestroyingSamePosition
                            && isDestroyingBlock
                            && destroyPos.equals(pending.pos());
                    case STOP_DESTROY_BLOCK -> hasDelayedDestroy
                            && delayedDestroyPos.equals(pending.pos());
                    default -> false;
                };
                if (!vanillaOwnsRequest) {
                    diggingPlayer.diggus$setPendingExcavation(null);
                }
            }
        }
    }

    @Unique
    private void diggus$bindRequestToVanillaAction(BlockPos pos, ServerboundPlayerActionPacket.Action action) {
        DiggingPlayerEntity diggingPlayer = (DiggingPlayerEntity) player;
        PendingExcavation pending = diggingPlayer.diggus$getPendingExcavation();
        if (pending == null) {
            return;
        }
        if (pending.isExpired(level.getGameTime())) {
            diggingPlayer.diggus$setPendingExcavation(null);
            return;
        }

        if (action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
            diggingPlayer.diggus$setPendingExcavation(null);
            return;
        }
        if (action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                && action != ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
            return;
        }

        // This injection runs before vanilla's action validation. Reject an unrelated position
        // using only the already-validated pending coordinates so a hostile action packet cannot
        // make getBlockState synchronously touch an arbitrary or unloaded chunk.
        if (!pending.dimension().equals(level.dimension())
                || !pending.pos().equals(pos)
                || !level.isInsideBuildHeight(pos)
                || !level.hasChunkAt(pos)) {
            diggingPlayer.diggus$setPendingExcavation(null);
            return;
        }

        BlockState state = level.getBlockState(pos);
        Identifier blockID = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        boolean sameTarget = pending.sameTarget(level.dimension(), pos, blockID, state);
        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            // A custom packet for a new attempt is still unbound when its START arrives. Seeing
            // an already-bound request here means a previous attempt was abandoned without ABORT.
            diggingPlayer.diggus$setPendingExcavation(
                    sameTarget && !pending.boundToVanillaAction() ? pending.bindToVanillaAction() : null
            );
        } else {
            diggingPlayer.diggus$setPendingExcavation(sameTarget ? pending.bindToVanillaAction() : null);
        }
    }

    @WrapMethod(method = "tick")
    private void diggus$wrapVanillaBreakTick(Operation<Void> original) {
        try {
            original.call();
        } finally {
            DiggingPlayerEntity diggingPlayer = (DiggingPlayerEntity) player;
            PendingExcavation pending = diggingPlayer.diggus$getPendingExcavation();
            if (pending != null
                    && pending.boundToVanillaAction()
                    && !diggus$isVanillaTracking(pending.pos())) {
                diggingPlayer.diggus$setPendingExcavation(null);
            }
        }
    }

    @Unique
    private boolean diggus$isVanillaTracking(BlockPos pos) {
        return (isDestroyingBlock && destroyPos.equals(pos))
                || (hasDelayedDestroy && delayedDestroyPos.equals(pos));
    }

    /**
     * Wraps the complete, already-mixed vanilla method so cancellable protection/event injections
     * cannot bypass cleanup. The Java call stack owns each invocation's snapshot; the depth counter
     * only suppresses nested or excavation-initiated breaks from consuming the outer seed request.
     */
    @WrapMethod(method = "destroyBlock")
    private boolean diggus$wrapVanillaBreak(BlockPos pos, Operation<Boolean> original) {
        ServerLevel breakLevel = level;
        BlockState originalState = breakLevel.getBlockState(pos);
        Item originalTool = player.getMainHandItem().getItem();
        Set<UUID> seedDropIds = Set.of();
        PendingExcavation pending = ((DiggingPlayerEntity) player).diggus$getPendingExcavation();
        Identifier blockID = BuiltInRegistries.BLOCK.getKey(originalState.getBlock());
        if (pending != null
                && DiggusMaximusMod.getOptions().autoPickup
                && pending.matches(breakLevel.dimension(), pos, blockID, originalState, breakLevel.getGameTime())) {
            seedDropIds = net.kyrptonaught.diggusmaximus.ExcavateHelper.itemEntityIds(breakLevel, pos);
        }

        int previousDepth = diggus$breakDepth++;
        try {
            boolean broken = original.call(pos);
            DiggingPlayerEntity diggingPlayer = (DiggingPlayerEntity) player;
            if (previousDepth == 0 && !diggingPlayer.diggus$isExcavating()) {
                diggus$afterVanillaBreak(
                        pos,
                        broken,
                        breakLevel,
                        originalState,
                        originalTool,
                        seedDropIds
                );
            }
            return broken;
        } finally {
            diggus$breakDepth--;
        }
    }

    @Unique
    private void diggus$afterVanillaBreak(
            BlockPos pos,
            boolean broken,
            ServerLevel breakLevel,
            BlockState originalState,
            Item originalTool,
            Set<UUID> seedDropIds
    ) {
        DiggingPlayerEntity diggingPlayer = (DiggingPlayerEntity) player;
        Identifier breakingBlockID = BuiltInRegistries.BLOCK.getKey(originalState.getBlock());
        PendingExcavation pending = diggingPlayer.diggus$getPendingExcavation();
        PendingExcavation matchedPending = null;
        if (pending != null) {
            if (pending.isExpired(breakLevel.getGameTime())) {
                diggingPlayer.diggus$setPendingExcavation(null);
            } else if (pending.sameTarget(
                    breakLevel.dimension(),
                    pos,
                    breakingBlockID,
                    originalState
            )) {
                // A request belongs to exactly one vanilla attempt. Consume it even when an
                // event cancels that attempt or destroyBlock reports success without changing state.
                diggingPlayer.diggus$setPendingExcavation(null);
                matchedPending = pending;
            }
        }

        if (!broken
                || originalState.isAir()
                || level != breakLevel
                || player.level() != breakLevel
                || breakLevel.getBlockState(pos).equals(originalState)) {
            return;
        }

        boolean sneakTriggered = DiggusMaximusMod.getOptions().sneakToExcavate
                && player.isShiftKeyDown()
                && player.isWithinBlockInteractionRange(pos, 1.0);
        if (matchedPending != null
                && matchedPending.boundToVanillaAction()
                && DiggusMaximusMod.getOptions().enabled
                && (matchedPending.shapeSelection() < 0 || DiggusMaximusMod.getExcavatingShapes().enableShapes)
                && player.isWithinBlockInteractionRange(pos, 1.0)) {
            new Excavate(
                    pos,
                    breakingBlockID,
                    originalTool,
                    player,
                    matchedPending.facing(),
                    matchedPending.shapeSelection(),
                    seedDropIds
            ).startExcavate(sneakTriggered);
            return;
        }

        // Preserve the legacy server-only semantics: sneakToExcavate is independent of the
        // client-packet enabled flag, but still require a vanilla-reachable break origin.
        if (sneakTriggered) {
            new Excavate(pos, breakingBlockID, originalTool, player, null, -1, null).startExcavate();
        }
    }
}

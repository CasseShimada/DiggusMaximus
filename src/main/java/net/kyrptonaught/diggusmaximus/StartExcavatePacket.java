package net.kyrptonaught.diggusmaximus;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Typed 26.2 form of the original four-field start_excavate_packet payload. */
public record StartExcavatePacket(BlockPos blockPos, Identifier blockID, int facingID, int shapeSelection)
        implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(DiggusMaximusMod.MOD_ID, "start_excavate_packet");
    public static final Type<StartExcavatePacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, StartExcavatePacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StartExcavatePacket::blockPos,
            Identifier.STREAM_CODEC, StartExcavatePacket::blockID,
            ByteBufCodecs.INT, StartExcavatePacket::facingID,
            ByteBufCodecs.INT, StartExcavatePacket::shapeSelection,
            StartExcavatePacket::new
    );

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
                context.server().execute(() -> payload.validateAndQueue(context.player()))
        );
    }

    private void validateAndQueue(ServerPlayer player) {
        DiggingPlayerEntity diggingPlayer = (DiggingPlayerEntity) player;
        long gameTime = player.level().getGameTime();
        // Rate-limit every decoded request before even the cheap reject paths so malformed packet
        // floods cannot avoid the same per-player budget as valid attempts.
        if (!diggingPlayer.diggus$tryConsumeRequestToken(gameTime)) {
            return;
        }

        var options = DiggusMaximusMod.getOptions();
        var shapes = DiggusMaximusMod.getExcavatingShapes();
        if (!options.enabled
                || !player.level().hasChunkAt(blockPos)
                || !player.isWithinBlockInteractionRange(blockPos, 1.0)
                || !player.level().mayInteract(player, blockPos)
                || !player.level().isInsideBuildHeight(blockPos)
                || facingID < -1 || facingID > 5
                || shapeSelection < -1 || shapeSelection >= ExcavateTypes.shape.values().length
                || (shapeSelection >= 0 && !shapes.enableShapes)) {
            return;
        }

        PendingExcavation existing = diggingPlayer.diggus$getPendingExcavation();
        if (existing != null && existing.isExpired(gameTime)) {
            diggingPlayer.diggus$setPendingExcavation(null);
            existing = null;
        }
        // A vanilla client emits at most one completion request per server tick. Coalesce a
        // same-tick packet flood before the raycast and state work below.
        if (existing != null && existing.gameTime() == gameTime) {
            return;
        }

        var state = player.level().getBlockState(blockPos);
        if (state.isAir()) {
            return;
        }
        Identifier actualBlockID = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!actualBlockID.equals(blockID)) {
            return;
        }

        Direction serverFacing = null;
        if (shapeSelection >= 0) {
            HitResult hit = player.pick(player.blockInteractionRange() + 1.0, 1.0F, false);
            if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(blockPos)) {
                return;
            }
            serverFacing = blockHit.getDirection(); // Client-facing field is retained on the wire but never trusted.
        }

        diggingPlayer.diggus$setPendingExcavation(
                new PendingExcavation(
                        player.level().dimension(),
                        blockPos,
                        actualBlockID,
                        state,
                        serverFacing,
                        shapeSelection,
                        gameTime
                )
        );
    }

    @Override
    public Type<StartExcavatePacket> type() {
        return TYPE;
    }
}

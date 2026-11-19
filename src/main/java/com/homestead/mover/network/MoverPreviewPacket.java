package com.homestead.mover.network;

import com.homestead.mover.client.MoverPreviewClient;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: open the ghost preview and hand the client the building it should draw. */
public final class MoverPreviewPacket {
    private final String blueprintId;
    private final BlockPos origin;
    private final int yaw;
    private final CompoundTag blob;

    public MoverPreviewPacket(String blueprintId, BlockPos origin, int yaw, CompoundTag blob) {
        this.blueprintId = blueprintId == null ? "" : blueprintId;
        this.origin = origin;
        this.yaw = yaw;
        this.blob = blob == null ? new CompoundTag() : blob;
    }

    public static void encode(MoverPreviewPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.blueprintId);
        buf.writeBlockPos(packet.origin);
        buf.writeVarInt(packet.yaw);
        buf.writeNbt(packet.blob);
    }

    public static MoverPreviewPacket decode(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        BlockPos origin = buf.readBlockPos();
        int yaw = buf.readVarInt();
        CompoundTag blob = buf.readNbt();
        return new MoverPreviewPacket(id, origin, yaw, blob);
    }

    public static void handle(MoverPreviewPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> MoverPreviewClient.begin(packet.blueprintId, packet.blob, packet.origin, packet.yaw)));
        ctx.get().setPacketHandled(true);
    }
}

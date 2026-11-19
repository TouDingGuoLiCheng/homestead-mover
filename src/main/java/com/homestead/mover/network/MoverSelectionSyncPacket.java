package com.homestead.mover.network;

import com.homestead.mover.capture.Bounds;
import com.homestead.mover.client.MoverSelectionClient;
import com.homestead.mover.item.MoverCaseItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: the measured box plus the corners that made it, so the player can see both the area and the
 * two blocks they actually clicked. One corner is already worth drawing even before the box exists.
 */
public final class MoverSelectionSyncPacket {
    private final int corners;
    private final BlockPos first;
    private final BlockPos second;
    /** The closed box, or null while fewer than two corners are standing. */
    private final Bounds bounds;

    public MoverSelectionSyncPacket(int corners, BlockPos first, BlockPos second, Bounds bounds) {
        this.corners = corners;
        this.first = first;
        this.second = second;
        this.bounds = bounds;
    }

    /** Reads the case itself: corners picked, the blocks they sit on, and the box they close. */
    public static MoverSelectionSyncPacket of(ItemStack stack) {
        return new MoverSelectionSyncPacket(MoverCaseItem.cornerCount(stack),
                MoverCaseItem.corner(stack, 0), MoverCaseItem.corner(stack, 1),
                MoverCaseItem.selection(stack).orElse(null));
    }

    public static void encode(MoverSelectionSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.corners);
        if (packet.corners >= 1) {
            buf.writeBlockPos(packet.first);
        }
        if (packet.corners >= 2) {
            buf.writeBlockPos(packet.second);
        }
        buf.writeBoolean(packet.bounds != null);
        if (packet.bounds == null) {
            return;
        }
        buf.writeVarInt(packet.bounds.minX());
        buf.writeVarInt(packet.bounds.minY());
        buf.writeVarInt(packet.bounds.minZ());
        buf.writeVarInt(packet.bounds.sizeX());
        buf.writeVarInt(packet.bounds.sizeY());
        buf.writeVarInt(packet.bounds.sizeZ());
    }

    public static MoverSelectionSyncPacket decode(FriendlyByteBuf buf) {
        int corners = buf.readByte();
        BlockPos first = corners >= 1 ? buf.readBlockPos() : null;
        BlockPos second = corners >= 2 ? buf.readBlockPos() : null;
        Bounds bounds = buf.readBoolean()
                ? new Bounds(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
                : null;
        return new MoverSelectionSyncPacket(corners, first, second, bounds);
    }

    public static void handle(MoverSelectionSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> MoverSelectionClient.apply(packet.corners, packet.first, packet.second, packet.bounds)));
        ctx.get().setPacketHandled(true);
    }
}

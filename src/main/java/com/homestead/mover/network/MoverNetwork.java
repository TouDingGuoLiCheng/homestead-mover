package com.homestead.mover.network;

import com.homestead.mover.BuildMoverMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** One channel, four messages: enter preview, update it, clear it, and client actions. */
public final class MoverNetwork {
    private static final String PROTOCOL = "1";

    /** Not the deprecated two-arg constructor: that one is flagged for removal. */
    private static final ResourceLocation CHANNEL_NAME =
            ResourceLocation.tryParse(BuildMoverMod.MOD_ID + ":main");

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_NAME,
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int id;

    private MoverNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(MoverPreviewPacket.class, id++)
                .decoder(MoverPreviewPacket::decode)
                .encoder(MoverPreviewPacket::encode)
                .consumerMainThread(MoverPreviewPacket::handle)
                .add();
        CHANNEL.messageBuilder(MoverPreviewUpdatePacket.class, id++)
                .decoder(MoverPreviewUpdatePacket::decode)
                .encoder(MoverPreviewUpdatePacket::encode)
                .consumerMainThread(MoverPreviewUpdatePacket::handle)
                .add();
        CHANNEL.messageBuilder(MoverPreviewClearPacket.class, id++)
                .decoder(MoverPreviewClearPacket::decode)
                .encoder(MoverPreviewClearPacket::encode)
                .consumerMainThread(MoverPreviewClearPacket::handle)
                .add();
        CHANNEL.messageBuilder(MoverSelectionSyncPacket.class, id++)
                .decoder(MoverSelectionSyncPacket::decode)
                .encoder(MoverSelectionSyncPacket::encode)
                .consumerMainThread(MoverSelectionSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(MoverPreviewConfirmPacket.class, id++)
                .decoder(MoverPreviewConfirmPacket::decode)
                .encoder(MoverPreviewConfirmPacket::encode)
                .consumerMainThread(MoverPreviewConfirmPacket::handle)
                .add();
        CHANNEL.messageBuilder(MoverPreviewCancelPacket.class, id++)
                .decoder(MoverPreviewCancelPacket::decode)
                .encoder(MoverPreviewCancelPacket::encode)
                .consumerMainThread(MoverPreviewCancelPacket::handle)
                .add();
        CHANNEL.messageBuilder(MoverCaptureSummaryPacket.class, id++)
                .decoder(MoverCaptureSummaryPacket::decode)
                .encoder(MoverCaptureSummaryPacket::encode)
                .consumerMainThread(MoverCaptureSummaryPacket::handle)
                .add();
        CHANNEL.messageBuilder(MoverCaptureResponsePacket.class, id++)
                .decoder(MoverCaptureResponsePacket::decode)
                .encoder(MoverCaptureResponsePacket::encode)
                .consumerMainThread(MoverCaptureResponsePacket::handle)
                .add();
        CHANNEL.messageBuilder(MoverSelectionClickPacket.class, id++)
                .decoder(MoverSelectionClickPacket::decode)
                .encoder(MoverSelectionClickPacket::encode)
                .consumerMainThread(MoverSelectionClickPacket::handle)
                .add();
        CHANNEL.messageBuilder(MoverPreviewShiftPacket.class, id++)
                .decoder(MoverPreviewShiftPacket::decode)
                .encoder(MoverPreviewShiftPacket::encode)
                .consumerMainThread(MoverPreviewShiftPacket::handle)
                .add();
        CHANNEL.messageBuilder(MoverSelectionShiftPacket.class, id++)
                .decoder(MoverSelectionShiftPacket::decode)
                .encoder(MoverSelectionShiftPacket::encode)
                .consumerMainThread(MoverSelectionShiftPacket::handle)
                .add();
        CHANNEL.messageBuilder(MoverImportRequestPacket.class, id++)
                .decoder(MoverImportRequestPacket::decode)
                .encoder(MoverImportRequestPacket::encode)
                .consumerMainThread(MoverImportRequestPacket::handle)
                .add();
        CHANNEL.messageBuilder(MoverImportListPacket.class, id++)
                .decoder(MoverImportListPacket::decode)
                .encoder(MoverImportListPacket::encode)
                .consumerMainThread(MoverImportListPacket::handle)
                .add();
        CHANNEL.messageBuilder(MoverImportPickPacket.class, id++)
                .decoder(MoverImportPickPacket::decode)
                .encoder(MoverImportPickPacket::encode)
                .consumerMainThread(MoverImportPickPacket::handle)
                .add();
        CHANNEL.messageBuilder(MoverPreviewActionPacket.class, id++)
                .decoder(MoverPreviewActionPacket::decode)
                .encoder(MoverPreviewActionPacket::encode)
                .consumerMainThread(MoverPreviewActionPacket::handle)
                .add();
    }

    public static void toPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void toServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}

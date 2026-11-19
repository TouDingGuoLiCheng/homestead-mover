package com.homestead.mover.client;

import com.homestead.mover.network.MoverImportListPacket;
import com.homestead.mover.network.MoverImportPickPacket;
import com.homestead.mover.network.MoverNetwork;
import com.homestead.mover.store.BlueprintStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The import page: every {@code .hsm} file in this save, one button per building. Picking a row
 * binds that file to the empty case in hand and closes the page — the file stays where it is, so
 * the same building can be imported again (or carried to another machine by copying the folder).
 */
public final class MoverImportScreen extends Screen {
    private static final int PAGE_SIZE = 6;

    private final List<MoverImportListPacket.Entry> entries;
    private int page;

    private MoverImportScreen(List<MoverImportListPacket.Entry> entries) {
        super(Component.translatable("homestead_mover.import.title"));
        this.entries = entries;
    }

    public static void show(MoverImportListPacket packet) {
        Minecraft.getInstance().setScreen(new MoverImportScreen(packet.entries()));
    }

    @Override
    protected void init() {
        int centre = width / 2;
        int y = height / 2 - 62;
        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            MoverImportListPacket.Entry entry = entries.get(i);
            addRenderableWidget(Button.builder(row(entry), button -> pick(entry))
                    .bounds(centre - 170, y, 340, 20).build());
            y += 24;
        }
        int bottom = height - 40;
        if (page > 0) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> turn(-1))
                    .bounds(centre - 170, bottom, 30, 20).build());
        }
        if (to < entries.size()) {
            addRenderableWidget(Button.builder(Component.literal(">"), button -> turn(1))
                    .bounds(centre + 140, bottom, 30, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(centre - 30, bottom, 60, 20).build());
    }

    private void turn(int delta) {
        page = Math.max(0, page + delta);
        rebuildWidgets();
    }

    private static Component row(MoverImportListPacket.Entry entry) {
        String name = entry.name() == null || entry.name().isEmpty() ? entry.id() : entry.name();
        return Component.translatable("homestead_mover.import.entry", name,
                entry.sizeX(), entry.sizeY(), entry.sizeZ(), kilobytes(entry.bytes()));
    }

    private static String kilobytes(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.0f", bytes / 1024.0D);
    }

    private void pick(MoverImportListPacket.Entry entry) {
        MoverNetwork.toServer(new MoverImportPickPacket(entry.id()));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int centre = width / 2;
        graphics.drawCenteredString(font, title, centre, height / 2 - 96, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("homestead_mover.import.hint",
                BlueprintStore.FOLDER), centre, height / 2 - 82, 0xA0A0A0);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.drawCenteredString(font, Component.translatable("homestead_mover.import.page",
                page + 1, pages), centre, height / 2 - 68, 0xE0E0E0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}

package com.homestead.mover.client;

import com.homestead.mover.network.MoverCaptureResponsePacket;
import com.homestead.mover.network.MoverCaptureSummaryPacket;
import com.homestead.mover.network.MoverNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The one screen in this mod: what the capture counted, a box to name the build, confirm and cancel.
 * The name is the only thing the player must think about — everything else is already decided by the
 * two corners and the height keys. Not a pause screen: the server still ticks to finish emptying.
 */
public final class MoverCaptureScreen extends Screen {
    private static final int LABEL_Y = 32;
    private static final int BOX_Y = 46;
    private static final int HINT_Y = 68;
    private static final int FIRST_LINE_Y = 84;

    private final MoverCaptureSummaryPacket summary;
    private boolean resolved;
    private EditBox nameBox;

    private MoverCaptureScreen(MoverCaptureSummaryPacket summary) {
        super(Component.translatable("homestead_mover.capture.title"));
        this.summary = summary;
    }

    public static void show(MoverCaptureSummaryPacket summary) {
        Minecraft.getInstance().setScreen(new MoverCaptureScreen(summary));
    }

    @Override
    protected void init() {
        int centre = width / 2;
        nameBox = new EditBox(font, centre - 130, BOX_Y, 260, 18,
                Component.translatable("homestead_mover.capture.name"));
        nameBox.setMaxLength(MoverCaptureResponsePacket.MAX_NAME_LENGTH);
        nameBox.setValue(summary.name());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        int y = height - 40;
        addRenderableWidget(Button.builder(Component.translatable("homestead_mover.capture.confirm"),
                        button -> resolve(true))
                .bounds(centre - 160, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("homestead_mover.capture.cancel"),
                        button -> resolve(false))
                .bounds(centre + 10, y, 150, 20).build());
    }

    /** Whatever stands in the box; clearing it keeps the auto name so the case stays findable. */
    private String chosenName() {
        return nameBox == null ? summary.name() : nameBox.getValue();
    }

    private void resolve(boolean confirmed) {
        if (resolved) {
            return;
        }
        resolved = true;
        MoverNetwork.toServer(new MoverCaptureResponsePacket(confirmed, chosenName()));
        Minecraft.getInstance().setScreen(null);
    }

    /** Enter confirms, so naming and accepting are one motion. Esc still counts as cancel. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER && nameBox != null && nameBox.isFocused()) {
            resolve(true);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (!resolved) {
            resolved = true;
            MoverNetwork.toServer(new MoverCaptureResponsePacket(false));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int centre = width / 2;
        graphics.drawCenteredString(font, title, centre, 18, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("homestead_mover.capture.name"),
                centre, LABEL_Y, 0xE0E0E0);
        graphics.drawCenteredString(font, Component.translatable("homestead_mover.capture.name_hint"),
                centre, HINT_Y, 0xA0A0A0);
        int y = FIRST_LINE_Y;
        y = line(graphics, Component.translatable("homestead_mover.capture.size", summary.sizeX(),
                summary.sizeY(), summary.sizeZ(), summary.volume()), centre, y);
        y = line(graphics, Component.translatable("homestead_mover.capture.blocks", summary.nonAir()), centre, y);
        y = line(graphics, Component.translatable("homestead_mover.capture.tiles", summary.tiles()), centre, y);
        y = line(graphics, Component.translatable("homestead_mover.capture.entities", summary.entities()), centre, y);
        y += 6;
        y = line(graphics, Component.translatable("homestead_mover.capture.keep"), centre, y);
        if (summary.skipped().isEmpty()) {
            line(graphics, Component.translatable("homestead_mover.capture.keep_none"), centre, y);
        } else {
            for (MoverCaptureSummaryPacket.SkipEntry entry : summary.skipped()) {
                y = line(graphics, Component.translatable("homestead_mover.capture.keep_line",
                        reasonText(entry), entry.count()), centre, y);
            }
        }
        graphics.drawCenteredString(font, Component.translatable("homestead_mover.capture.hint"),
                centre, height - 58, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int line(GuiGraphics graphics, Component text, int centre, int y) {
        graphics.drawCenteredString(font, text, centre, y, 0xE0E0E0);
        return y + 12;
    }

    /** Mod entities are grouped by namespace, so the wording carries the name. */
    private Component reasonText(MoverCaptureSummaryPacket.SkipEntry entry) {
        return switch (entry.reason()) {
            case MoverCaptureSummaryPacket.REASON_PLAYER -> Component.translatable("homestead_mover.skip.player");
            case MoverCaptureSummaryPacket.REASON_NOT_LIVING ->
                    Component.translatable("homestead_mover.skip.not_living");
            case MoverCaptureSummaryPacket.REASON_TRANSIENT ->
                    Component.translatable("homestead_mover.skip.transient");
            default -> Component.translatable("homestead_mover.skip.mod_entity", entry.key());
        };
    }
}

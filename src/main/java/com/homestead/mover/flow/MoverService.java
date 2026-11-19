package com.homestead.mover.flow;

import com.homestead.mover.capture.Bounds;
import com.homestead.mover.capture.CaptureSession;
import com.homestead.mover.capture.ClearSession;
import com.homestead.mover.capture.RestoreSession;
import com.homestead.mover.item.MoverCaseItem;
import com.homestead.mover.registry.ModItems;
import com.homestead.mover.network.MoverCaptureResponsePacket;
import com.homestead.mover.network.MoverImportListPacket;
import com.homestead.mover.network.MoverCaptureSummaryPacket;
import com.homestead.mover.network.MoverNetwork;
import com.homestead.mover.network.MoverPreviewActionPacket;
import com.homestead.mover.network.MoverPreviewClearPacket;
import com.homestead.mover.network.MoverPreviewPacket;
import com.homestead.mover.network.MoverPreviewUpdatePacket;
import com.homestead.mover.network.MoverSelectionSyncPacket;
import com.homestead.mover.placement.ConflictScanner;
import com.homestead.mover.placement.PlacementRules;
import com.homestead.mover.snapshot.CapturedBuilding;
import com.homestead.mover.snapshot.MoveSnapshot;
import com.homestead.mover.snapshot.SnapshotNbt;
import com.homestead.mover.store.BlueprintStore;
import com.homestead.mover.task.FrameScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side flow: measure -> capture -> store -> clear -> bind, and restore.
 * Everything long-running goes through {@link FrameScheduler}, so a tick never pays
 * for a whole building.
 */
public final class MoverService {
    private static final Map<ResourceKey<Level>, FrameScheduler> SCHEDULERS = new HashMap<>();
    private static final Map<UUID, PlacementSession> PLACEMENTS = new HashMap<>();
    private static final Map<UUID, PendingCapture> PENDING = new HashMap<>();
    /** Set while a landing job is in flight: the case stays bound until the callback runs. */
    private static final Set<UUID> BUSY = new HashSet<>();
    /** A corner click further away than this cannot have come from the player's own crosshair. */
    private static final double MAX_CORNER_CLICK_DISTANCE_SQR = 100.0D;

    private MoverService() {
    }

    public static FrameScheduler scheduler(ServerLevel level) {
        return SCHEDULERS.computeIfAbsent(level.dimension(), key -> FrameScheduler.server(System::nanoTime));
    }

    public static void tick(ServerLevel level) {
        scheduler(level).tick();
    }

    /** One horizontal corner per left click; the client gets the box so it can outline it. */
    public static String selectCorner(ServerPlayer player, ItemStack stack, BlockPos pos) {
        MoverCaseItem.CornerChange change = MoverCaseItem.selectCorner(stack, pos);
        syncSelection(player, stack);
        int corners = MoverCaseItem.cornerCount(stack);
        String summary = MoverCaseItem.selectionSummary(stack);
        return switch (change) {
            case FIRST -> "已选第 1 个角（1/2）：再点一格定范围";
            case SECOND -> "选区 " + summary + "　左键同一个角可取消";
            case REPLACED_SECOND -> "第二个角换到这里：" + summary;
            case DROPPED -> corners == 0
                    ? "已取消选区（0/2）"
                    : "已取消这个角（剩 1/2）：再点一格定范围";
        };
    }

    /**
     * A corner click from the client. The case has to be the one in hand — a selection belongs to
     * the case, so nothing may be picked up by a player who has already put it away.
     */
    public static void cornerClicked(ServerPlayer player, BlockPos pos) {
        if (PLACEMENTS.containsKey(player.getUUID()) || BUSY.contains(player.getUUID())) {
            return;
        }
        ItemStack stack = MoverCaseItem.heldCase(player);
        if (stack.isEmpty()) {
            return;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_CORNER_CLICK_DISTANCE_SQR) {
            return;
        }
        player.displayClientMessage(Component.literal(selectCorner(player, stack, pos)), true);
    }

    /** Arrow keys raise or lower the roof: the floor stays where it was picked. */
    public static String adjustSelectionHeight(ServerPlayer player, ItemStack stack, int delta) {
        if (MoverCaseItem.selection(stack).isEmpty()) {
            return "先左键点两个水平角";
        }
        MoverCaseItem.adjustHeight(stack, delta);
        syncSelection(player, stack);
        return "选区 " + MoverCaseItem.selectionSummary(stack);
    }

    /** Shift + arrow keys slide both corners across the ground; the height is left alone. */
    public static String shiftSelection(ServerPlayer player, ItemStack stack, int dx, int dz) {
        if (!MoverCaseItem.shiftCorners(stack, dx, dz)) {
            return "";
        }
        syncSelection(player, stack);
        String summary = MoverCaseItem.selectionSummary(stack);
        return summary.isEmpty() ? "已平移，再点一格定范围" : "选区 " + summary + "（Shift+方向键 平移）";
    }

    /** The client's shift-click: only the case in hand may move a selection. */
    public static void selectionShifted(ServerPlayer player, int dx, int dz) {
        if (PLACEMENTS.containsKey(player.getUUID())) {
            return;
        }
        ItemStack stack = MoverCaseItem.heldCase(player);
        if (stack.isEmpty()) {
            return;
        }
        String text = shiftSelection(player, stack, dx, dz);
        if (!text.isEmpty()) {
            player.displayClientMessage(Component.literal(text), true);
        }
    }

    // ---- import: a case in hand reads a .hsm file straight off the world folder ----

    /**
     * "I" with a case in hand: the file list of this save's blueprint folder. A case that still
     * points at a missing blueprint counts too — importing overwrites that binding, which is
     * exactly how a case left over from an older save is rescued.
     */
    public static void requestImport(ServerPlayer player) {
        ItemStack stack = MoverCaseItem.heldAnyCase(player);
        if (stack.isEmpty()) {
            message(player, "先把收纳箱拿在手上（主手或副手都行）");
            return;
        }
        // importing changes what the case carries, so an open preview has to go first
        cancelPlacementSilently(player);
        List<MoverImportListPacket.Entry> entries = new ArrayList<>();
        for (BlueprintStore.Entry entry : BlueprintStore.get(player.server).entries()) {
            entries.add(new MoverImportListPacket.Entry(entry.id(), entry.name(), entry.dimension(),
                    entry.sizeX(), entry.sizeY(), entry.sizeZ(), entry.bytes()));
        }
        if (entries.isEmpty()) {
            message(player, "还没有可导入的建筑：把 .hsm 放进 world/" + BlueprintStore.FOLDER + " 再按 I");
            return;
        }
        MoverNetwork.toPlayer(player, new MoverImportListPacket(entries));
    }

    /** Drops an open preview without the "cancelled" line; true when one was open. */
    private static boolean cancelPlacementSilently(ServerPlayer player) {
        if (PLACEMENTS.remove(player.getUUID()) == null) {
            return false;
        }
        MoverNetwork.toPlayer(player, new MoverPreviewClearPacket());
        return true;
    }

    /** Binds the chosen file to the case in hand; the file itself stays where it is. */
    public static void importBuilding(ServerPlayer player, String id) {
        if (PLACEMENTS.containsKey(player.getUUID()) || BUSY.contains(player.getUUID())) {
            return;
        }
        ItemStack stack = MoverCaseItem.heldAnyCase(player);
        if (stack.isEmpty()) {
            message(player, "先把收纳箱拿在手上（主手或副手都行）");
            return;
        }
        BlueprintStore store = BlueprintStore.get(player.server);
        if (!store.exists(id)) {
            message(player, "找不到这个文件：" + id);
            return;
        }
        CapturedBuilding building;
        try {
            building = SnapshotNbt.decode(store.load(id));
        } catch (IllegalArgumentException broken) {
            message(player, "文件读不出来，已忽略：" + id);
            return;
        }
        MoverCaseItem.bind(stack, id, building.snapshot());
        MoverCaseItem.bindCounts(stack, building.tileEntityCount(), building.entityCount());
        syncSelection(player, stack);
        message(player, "已导入 " + building.snapshot().name() + "，右键开预览");
    }

    private static void syncSelection(ServerPlayer player, ItemStack stack) {
        MoverNetwork.toPlayer(player, MoverSelectionSyncPacket.of(stack));
    }

    /**
     * The client's ghost is the authority on where it is (it follows the crosshair locally);
     * this overwrites the server's copy and then runs the same conflict check as any confirm.
     */
    public static void confirmPlacementAt(ServerPlayer player, BlockPos origin, int yaw, int yOffset) {
        PlacementSession session = PLACEMENTS.get(player.getUUID());
        if (session == null) {
            return;
        }
        session.setBaseOrigin(origin);
        session.setYaw(yaw);
        session.setYOffset(yOffset);
        confirmPlacement(player);
    }

    /** Step one: read the building, then wait for the player to look at the summary. */
    public static void requestCapture(ServerPlayer player, ItemStack stack) {
        PendingCapture waiting = PENDING.get(player.getUUID());
        if (waiting != null) {
            sendSummary(player, waiting);
            return;
        }
        if (BUSY.contains(player.getUUID())) {
            message(player, "上一件还没忙完，稍等一下再收录");
            return;
        }
        Bounds bounds = MoverCaseItem.selection(stack).orElse(null);
        if (bounds == null) {
            message(player, "先选两个角");
            return;
        }
        ServerLevel level = player.serverLevel();
        int slot = player.getInventory().selected;
        CaptureSession session = new CaptureSession(level, bounds, defaultName(level, bounds));
        message(player, "正在清点这栋建筑…");
        BUSY.add(player.getUUID());
        scheduler(level).submit("capture_scan", session::step, () -> {
            BUSY.remove(player.getUUID());
            CapturedBuilding building;
            try {
                building = session.result();
            } catch (IllegalStateException notFinished) {
                message(player, "清点未完成");
                return;
            }
            PendingCapture pending = new PendingCapture(bounds, building, slot);
            PENDING.put(player.getUUID(), pending);
            sendSummary(player, pending);
        });
    }

    private static void sendSummary(ServerPlayer player, PendingCapture pending) {
        MoverNetwork.toPlayer(player, MoverCaptureSummaryPacket.of(pending.building()));
    }

    /** The screen's answer: confirmed means write the blueprint, empty the spot, fill the case. */
    public static void resolveCapture(ServerPlayer player, boolean confirmed) {
        resolveCapture(player, confirmed, "");
    }

    public static void resolveCapture(ServerPlayer player, boolean confirmed, String requestedName) {
        if (confirmed) {
            confirmCapture(player, requestedName);
        } else {
            cancelCapture(player);
        }
    }

    public static void confirmCapture(ServerPlayer player) {
        confirmCapture(player, "");
    }

    public static void confirmCapture(ServerPlayer player, String requestedName) {
        PendingCapture pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            message(player, "没有待确认的收录");
            return;
        }
        ItemStack stack = player.getInventory().getItem(pending.slot());
        if (!stack.is(ModItems.MOVER_CASE.get())) {
            message(player, "手里已经不是收纳箱，收录取消");
            return;
        }
        ServerLevel level = player.serverLevel();
        CapturedBuilding building = pending.building();
        applyName(building, requestedName);
        String id = UUID.randomUUID().toString();
        // one file per building: the spot may not be emptied before the file is really on disk
        if (!BlueprintStore.get(player.server).save(id, SnapshotNbt.encode(building))) {
            message(player, "写不进 world/" + BlueprintStore.FOLDER + "，这次收录取消，原地没动");
            return;
        }
        message(player, "已收录 " + building.snapshot().name() + "，正在清空原地");
        BUSY.add(player.getUUID());

        ClearSession clear = new ClearSession(level, pending.bounds(), building.entities());
        scheduler(level).submit("clear", clear::step, () -> {
            BUSY.remove(player.getUUID());
            MoverCaseItem.bind(stack, id, building.snapshot());
            MoverCaseItem.bindCounts(stack, building.tileEntityCount(), building.entityCount());
            // binding clears the corners, so the client's box outline has to go too
            syncSelection(player, stack);
            message(player, "收纳箱已就绪：" + building.snapshot().region().nonAirCount() + " 格");
        });
    }

    /** "\u00a7cname\u00a7r" must not turn into "cnamer" on the item. */
    private static String stripFormatting(String value) {
        StringBuilder cleaned = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\u00a7') {
                i++;
                continue;
            }
            cleaned.append(value.charAt(i));
        }
        return cleaned.toString();
    }

    /** Finds the case that carries this blueprint id wherever it currently is, hotbar or off-hand. */
    private static ItemStack caseHolding(ServerPlayer player, String blueprintId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof MoverCaseItem && blueprintId.equals(MoverCaseItem.blueprintId(stack))) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** The player's own name wins; blank or absent keeps the auto name so the case stays findable. */
    private static void applyName(CapturedBuilding building, String requestedName) {
        if (requestedName == null) {
            return;
        }
        String cleaned = stripFormatting(requestedName).trim();
        if (cleaned.isEmpty()) {
            return;
        }
        int limit = MoverCaptureResponsePacket.MAX_NAME_LENGTH;
        building.snapshot().rename(cleaned.length() > limit ? cleaned.substring(0, limit) : cleaned);
    }

    public static void cancelCapture(ServerPlayer player) {
        if (PENDING.remove(player.getUUID()) != null) {
            message(player, "已取消，建筑原封不动");
        }
    }

    /** Test hook: the building waiting on the summary screen, or null. */
    public static CapturedBuilding pendingBuilding(ServerPlayer player) {
        PendingCapture pending = PENDING.get(player.getUUID());
        return pending == null ? null : pending.building();
    }

    /** A capture that finished but has not been confirmed yet; the world is still untouched. */
    private record PendingCapture(Bounds bounds, CapturedBuilding building, int slot) {
    }

    /** Only allowed before the building has been placed: the item still carries the id. */
    public static void restore(ServerPlayer player, ItemStack stack) {
        String id = MoverCaseItem.blueprintId(stack);
        if (id.isEmpty()) {
            return;
        }
        if (BUSY.contains(player.getUUID())) {
            message(player, "上一栋还在搬运，等它落地再复原");
            return;
        }
        BlueprintStore store = BlueprintStore.get(player.server);
        if (!store.exists(id)) {
            message(player, "数据缺失：这栋建筑不在本存档里");
            return;
        }
        CapturedBuilding building;
        try {
            building = SnapshotNbt.decode(store.load(id));
        } catch (IllegalArgumentException broken) {
            message(player, "数据损坏，已忽略");
            return;
        }
        MoveSnapshot snapshot = building.snapshot();
        ServerLevel target = levelOf(player.server, snapshot.dimensionId());
        if (target == null) {
            message(player, "原维度不见了，无法复原");
            return;
        }
        BlockPos origin = new BlockPos(snapshot.originX(), snapshot.originY(), snapshot.originZ());
        RestoreSession session = new RestoreSession(target, origin, building);
        message(player, "正在复原到原位");
        BUSY.add(player.getUUID());
        scheduler(target).submit("restore", session::step, () -> {
            BUSY.remove(player.getUUID());
            MoverCaseItem.unbind(stack);
            syncSelection(player, stack);
            message(player, "已复原到原位");
        });
    }

    // ---- M4: preview and placement ----

    /** Not-sneaking right click with a filled case: open a preview, or confirm the open one. */
    public static void usePlacement(ServerPlayer player, ItemStack stack, BlockPos clicked) {
        if (PLACEMENTS.containsKey(player.getUUID())) {
            confirmPlacement(player);
            return;
        }
        if (BUSY.contains(player.getUUID())) {
            message(player, "上一栋还在搬运，等它落地");
            return;
        }
        String id = MoverCaseItem.blueprintId(stack);
        if (id.isEmpty()) {
            message(player, "空箱不能落地：先收录一栋建筑");
            return;
        }
        BlueprintStore store = BlueprintStore.get(player.server);
        if (!store.exists(id)) {
            message(player, "数据缺失：这栋建筑不在本存档里");
            return;
        }
        CapturedBuilding building = SnapshotNbt.decode(store.load(id));
        PlacementSession session = new PlacementSession(id, building,
                PlacementRules.originOnTopOf(clicked), player.getInventory().selected);
        PLACEMENTS.put(player.getUUID(), session);
        // the client needs the blocks once; every later change is just origin + yaw
        MoverNetwork.toPlayer(player, new MoverPreviewPacket(id, session.origin(), session.yaw(),
                SnapshotNbt.encode(building)));
        reportPlacement(player);
    }

    public static PlacementSession placementOf(ServerPlayer player) {
        return PLACEMENTS.get(player.getUUID());
    }

    /** A preview must not survive a relogin: the same UUID would confirm it on the next click. */
    public static void forgetPlayer(ServerPlayer player) {
        PLACEMENTS.remove(player.getUUID());
        PENDING.remove(player.getUUID());
        BUSY.remove(player.getUUID());
    }

    public static void rotatePlacement(ServerPlayer player, boolean clockwise) {
        PlacementSession session = PLACEMENTS.get(player.getUUID());
        if (session == null) {
            message(player, "先在落点右键进预览，再旋转");
            return;
        }
        session.rotate(clockwise);
        reportPlacement(player);
    }

    /**
     * Shift + arrows with a preview open: slide the whole building one block across the ground.
     * The client's ghost is the authority on where the preview stands (it follows the crosshair
     * locally), so its origin is copied in before sliding — otherwise the server slides a stale
     * origin and the reply teleports the ghost back to where the preview was first opened.
     */
    public static void shiftPlacement(ServerPlayer player, BlockPos origin, int yOffset, int dx, int dz) {
        PlacementSession session = PLACEMENTS.get(player.getUUID());
        if (session == null) {
            message(player, "先在落点右键进预览，再平移");
            return;
        }
        session.setBaseOrigin(origin);
        session.setYOffset(yOffset);
        session.shift(dx, dz);
        reportPlacement(player);
    }

    public static void adjustPlacement(ServerPlayer player, int delta) {
        PlacementSession session = PLACEMENTS.get(player.getUUID());
        if (session == null) {
            message(player, "先在落点右键进预览，再上下微调");
            return;
        }
        session.adjustY(delta);
        reportPlacement(player);
    }

    /** Conflict count, footprint, facing and chunk readiness, as the HUD will show it. */
    public static void reportPlacement(ServerPlayer player) {
        PlacementSession session = PLACEMENTS.get(player.getUUID());
        if (session == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        MoverNetwork.toPlayer(player, new MoverPreviewUpdatePacket(session.origin(), session.yaw()));
        ConflictScanner.Result conflicts = MoverPlacer.conflicts(level, session.building(), session.origin(), session.yaw());
        boolean loaded = MoverPlacer.areaLoaded(level, session.building(), session.origin(), session.yaw());
        StringBuilder line = new StringBuilder("预览 ")
                .append(session.placedSizeX()).append("x").append(session.sizeY()).append("x").append(session.placedSizeZ())
                .append("｜").append(session.nonAirCount()).append(" 格")
                .append("｜朝向 ").append(session.yaw() * 90).append("°")
                .append("｜Y ").append(session.yOffset() >= 0 ? "+" : "").append(session.yOffset());
        if (!loaded) {
            line.append("｜区块未加载");
        }
        if (conflicts.hasConflicts()) {
            line.append("｜冲突 ").append(conflicts.conflictCount()).append(" 格(").append(conflicts.blockingSummary()).append(')');
        } else {
            line.append("｜无冲突");
        }
        message(player, line.toString());
    }

    public static void confirmPlacement(ServerPlayer player) {
        PlacementSession session = PLACEMENTS.get(player.getUUID());
        if (session == null) {
            message(player, "没有正在进行的落地");
            return;
        }
        ServerLevel level = player.serverLevel();
        ConflictScanner.Result conflicts = MoverPlacer.conflicts(level, session.building(), session.origin(), session.yaw());
        if (conflicts.hasConflicts()) {
            String where = conflicts.sampleCoords();
            message(player, "挡路 " + conflicts.conflictCount() + " 格：" + conflicts.blockingSummary()
                    + (where.isEmpty() ? "" : "　首处 " + where) + "　清掉后再落地");
            return;
        }
        if (!MoverPlacer.areaLoaded(level, session.building(), session.origin(), session.yaw())) {
            message(player, "目标区块还没加载，走近一点再确认");
            return;
        }
        PLACEMENTS.remove(player.getUUID());
        // until the callback runs the building is in the air: nothing may start a second move
        BUSY.add(player.getUUID());
        MoverNetwork.toPlayer(player, new MoverPreviewClearPacket());
        BlockPos origin = session.origin();
        int yaw = session.yaw();
        RestoreSession restore = new RestoreSession(level, origin, session.building(), yaw);
        message(player, "正在落地…");
        scheduler(level).submit("place", restore::step, () -> {
            BUSY.remove(player.getUUID());
            // the case may have been scrolled or swapped out of the slot the preview opened from
            ItemStack carried = caseHolding(player, session.blueprintId());
            if (!carried.isEmpty()) {
                MoverCaseItem.unbind(carried);
                syncSelection(player, carried);
            }
            message(player, "已落地：" + session.nonAirCount() + " 格，道具回到空箱");
        });
    }

    public static void cancelPlacement(ServerPlayer player) {
        if (PLACEMENTS.remove(player.getUUID()) != null) {
            MoverNetwork.toPlayer(player, new MoverPreviewClearPacket());
            message(player, "已取消预览，建筑还在箱子里");
        }
    }

    /** Client keys: turn the preview, or nudge it up and down. */
    public static void applyPreviewAction(ServerPlayer player, MoverPreviewActionPacket.Action action) {
        switch (action) {
            case ROTATE_RIGHT -> rotatePlacement(player, true);
            case ROTATE_LEFT -> rotatePlacement(player, false);
            case NUDGE_UP -> adjustPlacement(player, 1);
            case NUDGE_DOWN -> adjustPlacement(player, -1);
            case SELECTION_TALLER -> adjustSelectionHeight(player, player.getMainHandItem(), 1);
            case SELECTION_SHORTER -> adjustSelectionHeight(player, player.getMainHandItem(), -1);
        }
    }

    public static ServerLevel levelOf(MinecraftServer server, String dimensionId) {
        ResourceLocation key = ResourceLocation.tryParse(dimensionId);
        if (key == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, key));
    }

    private static String defaultName(ServerLevel level, Bounds bounds) {
        String dimension = level.dimension().location().getPath();
        return dimension + " @" + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ();
    }

    private static void message(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), false);
    }
}

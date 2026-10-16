package org.kylepitcock.splitscreen.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.kylepitcock.splitscreen.Splitscreen;
import org.kylepitcock.splitscreen.SplitscreenConfig;
import org.kylepitcock.splitscreen.mixin.client.KeyMappingAccessor;
import org.kylepitcock.splitscreen.mixin.client.MinecraftAccessor;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the list of local players and the "which player is currently loaded into Minecraft" state.
 * <p>
 * The main player (index 0) lives in Minecraft's own fields whenever nobody else is scoped, so a
 * game without secondary players behaves exactly like vanilla and nothing here is on the hot path.
 */
public final class SplitscreenManager {
    private static final List<PlayerContext> CONTEXTS = new ArrayList<>();
    private static final Map<Connection, PlayerContext> BY_CONNECTION = new ConcurrentHashMap<>();
    private static final List<PlayerContext> PENDING_REMOVAL = new ArrayList<>();

    private static PlayerContext primary;
    /** Whose state is currently in Minecraft's fields. */
    private static PlayerContext loaded;
    /** Non-null while a player's view is being ticked/rendered/receiving packets. */
    private static PlayerContext scoped;
    private static boolean layoutDirty;
    private static int layoutW;
    private static int layoutH;
    private static long lastFrameNanos;
    private static float frameSeconds;

    private SplitscreenManager() {}

    // ------------------------------------------------------------------ queries

    public static boolean isActive() {
        return CONTEXTS.size() > 1;
    }

    public static int playerCount() {
        return Math.max(1, CONTEXTS.size());
    }

    public static boolean isLoaded(PlayerContext ctx) {
        return loaded == ctx;
    }

    public static boolean inSecondaryScope() {
        return scoped != null && !scoped.isPrimary();
    }

    public static Viewport scopedViewport() {
        return scoped == null ? null : scoped.viewport;
    }

    public static RenderTarget scopedTarget() {
        return scoped == null ? null : scoped.target;
    }

    public static boolean virtualShift() {
        return scoped != null && scoped.virtualShift;
    }

    public static double virtualCursorX() {
        return scoped == null ? 0 : scoped.cursorX;
    }

    public static double virtualCursorY() {
        return scoped == null ? 0 : scoped.cursorY;
    }

    public static PlayerContext scopedContext() {
        return scoped;
    }

    public static PlayerContext contextForConnection(Connection connection) {
        return BY_CONNECTION.get(connection);
    }

    static void register(Connection connection, PlayerContext ctx) {
        BY_CONNECTION.put(connection, ctx);
    }

    static void unregister(Connection connection) {
        BY_CONNECTION.remove(connection);
    }

    public static List<PlayerContext> players() {
        return List.copyOf(CONTEXTS);
    }

    // ------------------------------------------------------------------ scoping

    private static void switchTo(Minecraft mc, PlayerContext ctx) {
        if (loaded == ctx) return;
        if (loaded != null) loaded.store(mc);
        ctx.load(mc);
        loaded = ctx;
    }

    /** Run something as the given player: their state in Minecraft's fields and their viewport as the window. */
    public static void run(PlayerContext ctx, Runnable action) {
        Minecraft mc = Minecraft.getInstance();
        PlayerContext prevLoaded = loaded;
        PlayerContext prevScoped = scoped;
        Map<KeyMapping, int[]> keys = ctx.isPrimary() ? null : snapshotKeys();
        switchTo(mc, ctx);
        scoped = ctx;
        try {
            action.run();
        } finally {
            scoped = prevScoped;
            if (prevLoaded != null) switchTo(mc, prevLoaded);
            if (keys != null) restoreKeys(keys);
        }
    }

    public static void runMain(Runnable action) {
        if (primary == null) {
            action.run();
        } else {
            run(primary, action);
        }
    }

    /** True for GLFW input callbacks that should be interpreted in the main player's viewport. */
    public static boolean shouldScopeMainInput() {
        return isActive() && scoped == null && loaded == primary;
    }

    private static Map<KeyMapping, int[]> snapshotKeys() {
        Map<KeyMapping, int[]> out = new IdentityHashMap<>();
        for (KeyMapping k : KeyMappingAccessor.splitscreen$getAll().values()) {
            KeyMappingAccessor a = (KeyMappingAccessor) k;
            out.put(k, new int[]{a.splitscreen$getIsDown() ? 1 : 0, a.splitscreen$getClickCount()});
        }
        return out;
    }

    private static void restoreKeys(Map<KeyMapping, int[]> snapshot) {
        snapshot.forEach((k, v) -> {
            KeyMappingAccessor a = (KeyMappingAccessor) k;
            a.splitscreen$setIsDown(v[0] == 1);
            a.splitscreen$setClickCount(v[1]);
        });
    }

    private static void blankKeys() {
        for (KeyMapping k : KeyMappingAccessor.splitscreen$getAll().values()) {
            KeyMappingAccessor a = (KeyMappingAccessor) k;
            a.splitscreen$setIsDown(false);
            a.splitscreen$setClickCount(0);
        }
    }

    // ------------------------------------------------------------------ joining / leaving

    /** Adds a local player. Pass a pad with jid -1 for a dummy that never produces input. */
    public static String join(Minecraft mc, Gamepad pad) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) return "splitscreen.no_world";
        int max = SplitscreenConfig.MAX_PLAYERS.get();
        if (CONTEXTS.size() >= max) return "splitscreen.full";

        if (primary == null) {
            primary = PlayerContext.createPrimary();
            primary.store(mc);
            loaded = primary;
        }
        if (CONTEXTS.isEmpty()) {
            CONTEXTS.add(primary);
        }
        int slot = 1;
        while (slotTaken(slot)) slot++;
        PlayerContext ctx = PlayerContext.createSecondary(mc, slot, pad);
        CONTEXTS.add(ctx);
        CONTEXTS.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
        relayout(mc, true);
        Gui mainGui = primary.gui;
        run(ctx, () -> ctx.initRenderers(mc, mainGui));
        ctx.connect(mc, server);
        Splitscreen.LOGGER.info("{} joining splitscreen on {}", ctx.name(), pad.name());
        return null;
    }

    private static boolean slotTaken(int slot) {
        for (PlayerContext c : CONTEXTS) if (c.slot() == slot) return true;
        return false;
    }

    public static void requestLeave(PlayerContext ctx) {
        if (!ctx.isPrimary() && !PENDING_REMOVAL.contains(ctx)) PENDING_REMOVAL.add(ctx);
    }

    public static void connectionLost(PlayerContext ctx, Component reason) {
        Splitscreen.LOGGER.info("{} lost its connection: {}", ctx.name(), reason.getString());
        ctx.dead = true;
        Minecraft.getInstance().execute(() -> requestLeave(ctx));
    }

    private static void removeNow(Minecraft mc, PlayerContext ctx) {
        if (!CONTEXTS.contains(ctx)) return;
        CONTEXTS.remove(ctx);
        // Run cleanup as the player so renderers close with their own state loaded.
        ctx.close(mc, true);
        if (CONTEXTS.size() <= 1) {
            CONTEXTS.clear();
            restoreSingle(mc);
        } else {
            relayout(mc, true);
        }
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable("splitscreen.left", ctx.name()));
        }
    }

    /** Back to plain vanilla rendering for the main player. */
    private static void restoreSingle(Minecraft mc) {
        if (primary != null) {
            if (primary.target != null) {
                primary.target.destroyBuffers();
                primary.target = null;
            }
            primary.viewport = null;
        }
        primary = null;
        loaded = null;
        scoped = null;
        mc.resizeGui();
    }

    /** Main player left the world: tear everything down without touching the (closing) server. */
    public static void shutdown(Minecraft mc) {
        if (CONTEXTS.isEmpty() && primary == null) return;
        if (loaded != primary && primary != null) switchTo(mc, primary);
        List<PlayerContext> copy = new ArrayList<>(CONTEXTS);
        CONTEXTS.clear();
        PENDING_REMOVAL.clear();
        for (PlayerContext c : copy) {
            if (!c.isPrimary()) c.close(mc, false);
        }
        BY_CONNECTION.clear();
        restoreSingle(mc);
    }

    // ------------------------------------------------------------------ layout

    public static void onWindowResized() {
        layoutDirty = true;
    }

    public static void markLayoutDirty() {
        layoutDirty = true;
    }

    private static void relayout(Minecraft mc, boolean force) {
        var window = mc.getWindow();
        int w = window.getWidth();
        int h = window.getHeight();
        if (!force && !layoutDirty && w == layoutW && h == layoutH) return;
        layoutDirty = false;
        layoutW = w;
        layoutH = h;
        Viewport[] vps = ViewportLayout.compute(CONTEXTS.size(), w, h, SplitscreenConfig.VERTICAL_SPLIT.get(), mc);
        for (int i = 0; i < CONTEXTS.size(); i++) {
            PlayerContext ctx = CONTEXTS.get(i);
            Viewport vp = vps[i];
            ctx.viewport = vp;
            run(ctx, () -> {
                ctx.applyViewport(mc, vp);
                ctx.resizeContents(mc);
            });
        }
    }

    // ------------------------------------------------------------------ per frame / per tick

    public static boolean shouldSplitRender() {
        return isActive() && scoped == null && loaded == primary;
    }

    /** True if this player's view has something to draw yet. A still-connecting player has no level or player. */
    private static boolean hasView(PlayerContext ctx) {
        return ctx.isPrimary() || (ctx.level != null && ctx.player != null);
    }

    /** Phase 1 of a frame: camera and level renderer updates, once per player. */
    public static void updateAll(Minecraft mc, DeltaTracker tracker, boolean advance) {
        relayout(mc, false);
        long now = Util.getNanos();
        frameSeconds = lastFrameNanos == 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = now;
        for (PlayerContext ctx : List.copyOf(CONTEXTS)) {
            run(ctx, () -> {
                if (!ctx.isPrimary()) ctx.frameUpdate(mc, frameSeconds);
                if (!hasView(ctx)) return;
                if (!ctx.isPrimary() && advance && mc.level != null) mc.level.update();
                mc.gameRenderer.update(tracker, advance);
            });
        }
    }

    /** Phase 2: what each player is looking at. */
    public static void pickAll(Minecraft mc, float partial) {
        for (PlayerContext ctx : List.copyOf(CONTEXTS)) {
            if (!hasView(ctx)) continue;
            run(ctx, () -> ((MinecraftAccessor) mc).splitscreen$pick(partial));
        }
    }

    /** Phase 3: turn each player's world and GUI into render state. */
    public static void extractAll(Minecraft mc, DeltaTracker tracker, boolean advance) {
        for (PlayerContext ctx : List.copyOf(CONTEXTS)) {
            if (!hasView(ctx)) continue;
            run(ctx, () -> mc.gameRenderer.extract(tracker, advance));
        }
    }

    /** Phase 4: draw every player into their own target, then compose the targets into the window. */
    public static void renderAll(Minecraft mc, DeltaTracker tracker, boolean advance) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        RenderTarget main = mc.getMainRenderTarget();
        encoder.clearColorTexture(main.getColorTexture(), 0xFF000000);
        for (PlayerContext ctx : List.copyOf(CONTEXTS)) {
            run(ctx, () -> {
                if (hasView(ctx)) {
                    mc.gameRenderer.render(tracker, advance);
                } else {
                    // Still connecting: nothing to draw yet (the GUI code assumes a level).
                    encoder.clearColorTexture(ctx.target.getColorTexture(), 0xFF14141A);
                }
            });
            composeInto(encoder, main, ctx);
        }
    }

    /** Draw one player's finished target into their rectangle of the window target. */
    private static void composeInto(com.mojang.blaze3d.systems.CommandEncoder encoder, RenderTarget main, PlayerContext ctx) {
        Viewport v = ctx.viewport;
        try (com.mojang.blaze3d.systems.RenderPass pass = encoder.createRenderPass(() -> "splitscreen compose " + ctx.name(),
                main.getColorTextureView(), java.util.OptionalInt.empty())) {
            pass.setPipeline(net.minecraft.client.renderer.RenderPipelines.TRACY_BLIT);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", ctx.target.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST));
            // Viewports are described from the top of the window; the GPU wants them from the bottom.
            pass.setViewport(v.x(), main.height - v.y() - v.height(), v.width(), v.height());
            pass.draw(0, 3);
        }
    }

    public static void tickSecondaries(Minecraft mc) {
        if (!isActive() || scoped != null || loaded != primary) return;

        if (!PENDING_REMOVAL.isEmpty()) {
            List<PlayerContext> gone = new ArrayList<>(PENDING_REMOVAL);
            PENDING_REMOVAL.clear();
            for (PlayerContext c : gone) removeNow(mc, c);
            if (!isActive()) return;
        }
        if (mc.isPaused()) return;

        for (PlayerContext ctx : List.copyOf(CONTEXTS)) {
            if (ctx.isPrimary() || ctx.isDead()) continue;
            run(ctx, () -> {
                blankKeys();
                ctx.tick(mc);
            });
        }
    }

    /** Called when resource packs reload so the extra renderers pick up new shaders/textures. */
    public static void reloadRenderers(Minecraft mc) {
        Gui mainGui = primary == null ? null : primary.gui;
        for (PlayerContext ctx : List.copyOf(CONTEXTS)) {
            if (!ctx.isPrimary()) run(ctx, () -> ctx.reloadRenderers(mc, mainGui));
        }
    }
}

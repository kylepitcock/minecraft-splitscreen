package org.kylepitcock.splitscreen.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TrackingEmitter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;
import org.kylepitcock.splitscreen.Splitscreen;
import org.kylepitcock.splitscreen.SplitscreenConfig;
import org.kylepitcock.splitscreen.mixin.client.KeyMappingAccessor;
import org.kylepitcock.splitscreen.mixin.client.MinecraftAccessor;
import org.kylepitcock.splitscreen.mixin.client.ParticleEngineAccessor;
import org.lwjgl.glfw.GLFW;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

/**
 * Everything that belongs to one local player: the connection to the integrated server, the client
 * level, renderers, HUD, screen and controller. {@link SplitscreenManager} swaps one context at a time
 * into the {@link Minecraft} singleton while that player is being ticked, rendered or sent packets.
 */
public final class PlayerContext {
    private final boolean primary;
    private final int slot;
    private final String name;
    private final Gamepad pad;

    Viewport viewport;
    RenderTarget target;
    Connection connection;
    volatile boolean dead;

    // --- state swapped in and out of Minecraft ---
    LocalPlayer player;
    ClientLevel level;
    MultiPlayerGameMode gameMode;
    Entity cameraEntity;
    Entity crosshairPickEntity;
    HitResult hitResult;
    Screen screen;
    Gui gui;
    RenderBuffers renderBuffers;
    LevelRenderer levelRenderer;
    GameRenderer gameRenderer;
    int missTime;
    int rightClickDelay;
    private ClientLevel particleLevel;
    private Map<ParticleRenderType, Queue<Particle>> particles;
    private Queue<TrackingEmitter> trackingEmitters;
    private Queue<Particle> particlesToAdd;
    private Object2IntOpenHashMap<ParticleGroup> trackedParticleCounts;

    // --- controller state ---
    double cursorX;
    double cursorY;
    boolean virtualShift;
    private boolean sprintLatched;
    private Screen cursorScreen;
    private int dragButton = -1;
    private double scrollAccumulator;
    private int backHeldTicks;
    private boolean closed;

    private PlayerContext(boolean primary, int slot, String name, Gamepad pad) {
        this.primary = primary;
        this.slot = slot;
        this.name = name;
        this.pad = pad;
    }

    static PlayerContext createPrimary() {
        return new PlayerContext(true, 0, "Player1", null);
    }

    static PlayerContext createSecondary(Minecraft mc, int slot, Gamepad pad) {
        PlayerContext ctx = new PlayerContext(false, slot, "Player" + (slot + 1), pad);
        ctx.gui = new Gui(mc);
        // Each view needs its own buffers: chunk compilers from different LevelRenderers would otherwise
        // fight over one shared buffer pool (and crash with a null acquire / corrupted mesh data).
        ctx.renderBuffers = new RenderBuffers(2);
        ctx.levelRenderer = new LevelRenderer(mc, mc.getEntityRenderDispatcher(), mc.getBlockEntityRenderDispatcher(), ctx.renderBuffers);
        ctx.gameRenderer = new GameRenderer(mc, new ItemInHandRenderer(mc, mc.getEntityRenderDispatcher(), mc.getItemRenderer()),
                mc.getResourceManager(), ctx.renderBuffers);
        ParticleEngineAccessor engine = (ParticleEngineAccessor) mc.particleEngine;
        TreeMap<ParticleRenderType, Queue<Particle>> shared = (TreeMap<ParticleRenderType, Queue<Particle>>) engine.splitscreen$getParticles();
        ctx.particles = new TreeMap<>(shared.comparator());
        ctx.trackingEmitters = new ArrayDeque<>();
        ctx.particlesToAdd = new ArrayDeque<>();
        ctx.trackedParticleCounts = new Object2IntOpenHashMap<>();
        return ctx;
    }

    public boolean isPrimary() {
        return primary;
    }

    public int slot() {
        return slot;
    }

    public String name() {
        return name;
    }

    public Gamepad pad() {
        return pad;
    }

    /** One-line state description for diagnostics. */
    public String statusSummary() {
        return "[" + (player == null ? "no-player" : String.format("pos=%.1f,%.1f,%.1f", player.getX(), player.getY(), player.getZ()))
                + (level == null ? " no-level" : "") + (screen == null ? "" : " screen=" + screen.getClass().getSimpleName()) + "]";
    }

    public boolean isDead() {
        return dead;
    }

    public RenderTarget target() {
        return target;
    }

    public Viewport viewport() {
        return viewport;
    }

    // ------------------------------------------------------------------ swapping

    /** Copy Minecraft's current per-player fields into this context. */
    void store(Minecraft mc) {
        player = mc.player;
        level = mc.level;
        gameMode = mc.gameMode;
        cameraEntity = mc.cameraEntity;
        crosshairPickEntity = mc.crosshairPickEntity;
        hitResult = mc.hitResult;
        screen = mc.screen;
        gui = mc.gui;
        levelRenderer = mc.levelRenderer;
        gameRenderer = mc.gameRenderer;
        MinecraftAccessor acc = (MinecraftAccessor) mc;
        missTime = acc.splitscreen$getMissTime();
        rightClickDelay = acc.splitscreen$getRightClickDelay();
        ParticleEngineAccessor engine = (ParticleEngineAccessor) mc.particleEngine;
        particleLevel = engine.splitscreen$getLevel();
        particles = engine.splitscreen$getParticles();
        trackingEmitters = engine.splitscreen$getTrackingEmitters();
        particlesToAdd = engine.splitscreen$getParticlesToAdd();
        trackedParticleCounts = engine.splitscreen$getTrackedParticleCounts();
    }

    /** Make this context the one Minecraft sees. */
    void load(Minecraft mc) {
        mc.player = player;
        mc.level = level;
        mc.gameMode = gameMode;
        mc.cameraEntity = cameraEntity;
        mc.crosshairPickEntity = crosshairPickEntity;
        mc.hitResult = hitResult;
        mc.screen = screen;
        MinecraftAccessor acc = (MinecraftAccessor) mc;
        acc.splitscreen$setGui(gui);
        acc.splitscreen$setLevelRenderer(levelRenderer);
        acc.splitscreen$setGameRenderer(gameRenderer);
        acc.splitscreen$setMissTime(missTime);
        acc.splitscreen$setRightClickDelay(rightClickDelay);
        ParticleEngineAccessor engine = (ParticleEngineAccessor) mc.particleEngine;
        engine.splitscreen$setLevel(particleLevel);
        engine.splitscreen$setParticles(particles);
        engine.splitscreen$setTrackingEmitters(trackingEmitters);
        engine.splitscreen$setParticlesToAdd(particlesToAdd);
        engine.splitscreen$setTrackedParticleCounts(trackedParticleCounts);
    }

    // ------------------------------------------------------------------ viewport / resources

    /** Must run inside this context's scope. */
    void applyViewport(Minecraft mc, Viewport vp) {
        Viewport old = this.viewport;
        this.viewport = vp;
        if (target == null) {
            target = new TextureTarget(vp.width(), vp.height(), true, Minecraft.ON_OSX);
            target.setClearColor(0f, 0f, 0f, 0f);
        } else if (target.width != vp.width() || target.height != vp.height()) {
            target.resize(vp.width(), vp.height(), Minecraft.ON_OSX);
        }
    }

    /** Called inside scope, after the viewport is set, to size the renderers and screen. */
    void resizeContents(Minecraft mc) {
        Viewport vp = viewport;
        mc.gameRenderer.resize(vp.width(), vp.height());
        if (mc.screen != null) {
            mc.screen.resize(mc, vp.guiWidth(), vp.guiHeight());
        }
    }

    /** Secondary only; inside scope. */
    void initRenderers(Minecraft mc, Gui mainGui) {
        FactoryUiSync.sync(mainGui, gui);
        levelRenderer.onResourceManagerReload(mc.getResourceManager());
        levelRenderer.resize(viewport.width(), viewport.height());
    }

    /** Inside scope. */
    void reloadRenderers(Minecraft mc, Gui mainGui) {
        if (!primary) {
            FactoryUiSync.sync(mainGui, gui);
            levelRenderer.onResourceManagerReload(mc.getResourceManager());
            levelRenderer.resize(viewport.width(), viewport.height());
        }
    }

    // ------------------------------------------------------------------ connection

    void connect(Minecraft mc, IntegratedServer server) {
        SocketAddress address = server.getConnection().startMemoryChannel();
        Connection conn = Connection.connectToLocalServer(address);
        this.connection = conn;
        SplitscreenManager.register(conn, this);
        conn.initiateServerboundPlayConnection(address.toString(), 0,
                new ClientHandshakePacketListenerImpl(conn, mc, null, null, false, null, s -> {}, null));
        conn.send(new ServerboundHelloPacket(name, UUIDUtil.createOfflinePlayerUUID(name)));
    }

    /** Called with the context NOT scoped. Releases everything this context owns. */
    void close(Minecraft mc, boolean notifyServer) {
        if (closed) return;
        closed = true;
        dead = true;
        if (connection != null) {
            SplitscreenManager.unregister(connection);
            try {
                if (player != null && player.connection != null) player.connection.close();
                if (notifyServer && connection.isConnected()) {
                    connection.disconnect(Component.translatable("disconnect.quitting"));
                    connection.handleDisconnection();
                }
            } catch (RuntimeException e) {
                Splitscreen.LOGGER.warn("Error closing splitscreen connection for {}", name, e);
            }
        }
        if (gameRenderer != null) {
            // Shaders are shared with the main renderer; detach them so close() doesn't free them.
            gameRenderer.blitShader = null;
            ((org.kylepitcock.splitscreen.mixin.client.GameRendererAccessor) gameRenderer).splitscreen$getShaders().clear();
            gameRenderer.close();
        }
        if (levelRenderer != null) {
            // Releases chunk buffers and stops the section compiler threads; close() alone does neither.
            levelRenderer.setLevel(null);
            levelRenderer.close();
        }
        if (renderBuffers != null) renderBuffers.fixedBufferPack().close();
        if (target != null) target.destroyBuffers();
        if (pad != null) pad.close();
        player = null;
        level = null;
        gameMode = null;
        screen = null;
        cameraEntity = null;
    }

    // ------------------------------------------------------------------ per-frame input (scoped)

    /** Camera look and on-screen cursor. Runs every rendered frame, inside this context's scope. */
    void frameUpdate(Minecraft mc, float seconds) {
        if (pad == null) return;
        pad.pollFrame();
        if (player == null || level == null) return;
        if (mc.screen == null) {
            cursorScreen = null;
            float dz = SplitscreenConfig.DEADZONE.get().floatValue();
            float rx = pad.axis(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X, dz);
            float ry = pad.axis(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y, dz);
            if (rx != 0f || ry != 0f) {
                double speed = SplitscreenConfig.LOOK_SPEED.get() * seconds / 0.15;
                double sign = SplitscreenConfig.INVERT_Y.get() ? -1 : 1;
                player.turn(rx * Math.abs(rx) * speed, ry * Math.abs(ry) * speed * sign);
            }
        } else {
            screenInput(mc, seconds);
        }
    }

    private void screenInput(Minecraft mc, float seconds) {
        Screen s = mc.screen;
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        if (s != cursorScreen) {
            cursorScreen = s;
            cursorX = gw / 2.0;
            cursorY = gh / 2.0;
            dragButton = -1;
        }
        float dz = SplitscreenConfig.DEADZONE.get().floatValue();
        float lx = pad.axis(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X, dz);
        float ly = pad.axis(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y, dz);
        double oldX = cursorX, oldY = cursorY;
        double speed = 260.0 * seconds;
        cursorX = Math.max(0, Math.min(gw - 1, cursorX + lx * Math.abs(lx) * speed));
        cursorY = Math.max(0, Math.min(gh - 1, cursorY + ly * Math.abs(ly) * speed));
        if (cursorX != oldX || cursorY != oldY) {
            s.mouseMoved(cursorX, cursorY);
            if (dragButton >= 0) s.mouseDragged(cursorX, cursorY, dragButton, cursorX - oldX, cursorY - oldY);
        }

        // A = left click, X = right click, Y = shift + left click, B = back.
        click(mc, s, GLFW.GLFW_GAMEPAD_BUTTON_A, 0);
        if (mc.screen == s) click(mc, s, GLFW.GLFW_GAMEPAD_BUTTON_X, 1);
        if (mc.screen == s && pad.pressedThisFrame(GLFW.GLFW_GAMEPAD_BUTTON_Y)) {
            virtualShift = true;
            s.mouseClicked(cursorX, cursorY, 0);
            s.mouseReleased(cursorX, cursorY, 0);
            virtualShift = false;
        }
        if (mc.screen == s && pad.pressedThisFrame(GLFW.GLFW_GAMEPAD_BUTTON_B)) {
            s.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        }
        if (mc.screen == s) {
            scrollAccumulator += pad.axis(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y, dz) * seconds * 12.0;
            while (Math.abs(scrollAccumulator) >= 1.0 && mc.screen == s) {
                double dir = Math.signum(scrollAccumulator);
                s.mouseScrolled(cursorX, cursorY, 0, -dir);
                scrollAccumulator -= dir;
            }
        }
    }

    private void click(Minecraft mc, Screen s, int button, int mouseButton) {
        if (pad.pressedThisFrame(button)) {
            s.mouseClicked(cursorX, cursorY, mouseButton);
            dragButton = mouseButton;
        } else if (pad.releasedThisFrame(button)) {
            s.mouseReleased(cursorX, cursorY, mouseButton);
            if (dragButton == mouseButton) dragButton = -1;
        }
    }

    // ------------------------------------------------------------------ tick (scoped)

    /** Secondary only. Runs inside scope with key mappings already blanked by the manager. */
    void tick(Minecraft mc) {
        if (gameMode == null || level == null || player == null) {
            // Still logging in: the pending connection is not driven by a game mode yet.
            if (connection != null) {
                if (connection.isConnected()) connection.tick();
                else connection.handleDisconnection();
            }
            return;
        }
        pad.pollTick();
        applyVirtualKeys(mc);

        if (rightClickDelay > 0) rightClickDelay--;
        mc.gui.tick(false);
        mc.gameRenderer.pick(1.0F);
        gameMode.tick();

        if (mc.screen == null) {
            if (player.isDeadOrDying()) {
                mc.setScreen(null);
            } else if (player.isSleeping()) {
                mc.setScreen(new InBedChatScreen());
            }
        }
        if (mc.screen != null) {
            if (mc.screen instanceof InBedChatScreen bed && !player.isSleeping()) bed.onPlayerWokeUp();
            ((MinecraftAccessor) mc).splitscreen$setMissTime(10000);
            mc.screen.tick();
        }
        if (mc.screen == null) {
            ((MinecraftAccessor) mc).splitscreen$handleKeybinds();
            int miss = ((MinecraftAccessor) mc).splitscreen$getMissTime();
            if (miss > 0) ((MinecraftAccessor) mc).splitscreen$setMissTime(miss - 1);
        }

        ClientLevel lvl = mc.level;
        if (lvl != null) {
            mc.gameRenderer.tick();
            mc.levelRenderer.tick();
            lvl.tickEntities();
            EventHooks.fireLevelTickPre(lvl, () -> true);
            lvl.tick(() -> true);
            EventHooks.fireLevelTickPost(lvl, () -> true);
            if (mc.player != null) {
                lvl.animateTick(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
            }
            mc.particleEngine.tick();
        }

        // Hold Back to leave splitscreen.
        if (pad.tickHeld(GLFW.GLFW_GAMEPAD_BUTTON_BACK)) {
            if (++backHeldTicks >= 40) SplitscreenManager.requestLeave(this);
        } else {
            backHeldTicks = 0;
        }
        if (!pad.isConnected()) SplitscreenManager.requestLeave(this);
        // Start opens this player's own pause menu (skin, options, ...) without pausing anyone else.
        if (pad.tickPressed(GLFW.GLFW_GAMEPAD_BUTTON_START) && mc.screen == null) {
            mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
        }
    }

    private void applyVirtualKeys(Minecraft mc) {
        var o = mc.options;
        if (!(player.input instanceof GamepadInput)) {
            player.input = new GamepadInput(pad);
        }
        GamepadInput input = (GamepadInput) player.input;
        if (mc.screen != null) {
            // Gameplay buttons are inert while a screen is open.
            sprintLatched = false;
            input.sneakToggled = false;
            return;
        }
        if (pad.tickPressed(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB)) input.sneakToggled = !input.sneakToggled;
        if (pad.tickPressed(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB)) sprintLatched = true;
        if (player.input.forwardImpulse <= 0.01f) sprintLatched = false;
        key(o.keySprint, sprintLatched, 0);

        key(o.keyAttack, pad.trigger(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) > 0.5f, 0);
        key(o.keyUse, pad.trigger(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) > 0.5f, 0);
        edge(o.keyAttack, rtPressed());
        edge(o.keyUse, ltPressed());
        if (pad.tickPressed(GLFW.GLFW_GAMEPAD_BUTTON_Y)) click(o.keyInventory);
        if (pad.tickPressed(GLFW.GLFW_GAMEPAD_BUTTON_B)) click(o.keyDrop);
        if (pad.tickPressed(GLFW.GLFW_GAMEPAD_BUTTON_X)) click(o.keySwapOffhand);
        if (pad.tickPressed(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP)) click(o.keyTogglePerspective);

        var inv = player.getInventory();
        if (pad.tickPressed(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER)) inv.selected = (inv.selected + 1) % 9;
        if (pad.tickPressed(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER)) inv.selected = (inv.selected + 8) % 9;
    }

    // Trigger edge tracking so a quick pull still registers one click.
    private boolean rtPrev;
    private boolean ltPrev;

    private boolean rtPressed() {
        boolean now = pad.trigger(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) > 0.5f;
        boolean edge = now && !rtPrev;
        rtPrev = now;
        return edge;
    }

    private boolean ltPressed() {
        boolean now = pad.trigger(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) > 0.5f;
        boolean edge = now && !ltPrev;
        ltPrev = now;
        return edge;
    }

    private static void key(KeyMapping key, boolean down, int unused) {
        ((KeyMappingAccessor) key).splitscreen$setIsDown(down);
    }

    private static void edge(KeyMapping key, boolean pressed) {
        if (pressed) click(key);
    }

    private static void click(KeyMapping key) {
        KeyMappingAccessor acc = (KeyMappingAccessor) key;
        acc.splitscreen$setClickCount(acc.splitscreen$getClickCount() + 1);
    }
}

package de.ancientreminder;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import de.ancientreminder.gui.HudEditorScreen;
import de.ancientreminder.gui.MenuScreen;
import de.ancientreminder.render.Overlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

/**
 * Ancient Reminder 2.1 – Einstiegspunkt.
 * Menü: Rechte Umschalt-Taste oder /ancient
 */
public class AncientReminderClient implements ClientModInitializer {
    public static final String MOD_ID = "ancientreminder";

    private static KeyMapping menuKey;
    private static KeyMapping chunkKey;
    private static Supplier<Screen> pendingScreen;
    private static int pendingDelay;

    /** Öffnet einen Bildschirm ein paar Ticks später (nötig nach Chat-Befehlen). */
    public static void openLater(Supplier<Screen> screen) {
        pendingScreen = screen;
        pendingDelay = 2;
    }

    @Override
    public void onInitializeClient() {
        Config.load();
        Data.load();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ancientreminder.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, category));
        chunkKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ancientreminder.chunks", InputConstants.Type.KEYSYM, InputConstants.KEY_F9, category));

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "hud"), Hud::render);
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(Overlay::render);

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientSendMessageEvents.COMMAND.register(NetherSession::onCommand);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Stats.resetSession();
            NetherSession.endSession();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            NetherSession.endSession();
            Data.save();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            NetherSession.endSession();
            Data.save();
            Config.save();
            Overlay.close();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> registerCommands(dispatcher));
    }

    private void onTick(Minecraft mc) {
        while (menuKey.consumeClick()) {
            mc.setScreen(new MenuScreen(null));
        }
        while (chunkKey.consumeClick()) {
            Config.I.chunkEnabled = !Config.I.chunkEnabled;
            Config.save();
            Ui.bar("Chunkgrenzen: " + (Config.I.chunkEnabled ? "§aAN" : "§cAUS"));
        }
        if (pendingScreen != null && --pendingDelay <= 0) {
            Screen s = pendingScreen.get();
            pendingScreen = null;
            mc.setScreen(s);
        }
        if (mc.player == null || mc.level == null) return;
        Stats.tick(mc);
        OreScanner.tick(mc);
        NetherSession.tick(mc);
        Warnings.tick(mc);
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("ancient")
                .executes(ctx -> {
                    openLater(() -> new MenuScreen(null));
                    return 1;
                })
                .then(ClientCommandManager.literal("menu").executes(ctx -> {
                    openLater(() -> new MenuScreen(null));
                    return 1;
                }))
                .then(ClientCommandManager.literal("hud").executes(ctx -> {
                    openLater(() -> new HudEditorScreen(null));
                    return 1;
                }))
                .then(ClientCommandManager.literal("stats").executes(ctx -> {
                    openLater(() -> new MenuScreen(null, MenuScreen.PAGE_STATS));
                    return 1;
                }))
                .then(ClientCommandManager.literal("rescan").executes(ctx -> {
                    NetherSession.forceRescan();
                    ctx.getSource().sendFeedback(Component.literal("Neuer Scan wird gestartet (wenn du auf Ancient-Höhe bist)."));
                    return 1;
                }))
                .then(ClientCommandManager.literal("calibrate").then(ClientCommandManager.literal("reset").executes(ctx -> {
                    Calibration.reset();
                    ctx.getSource().sendFeedback(Component.literal("Kalibrierung zurückgesetzt."));
                    return 1;
                })))
                .then(ClientCommandManager.literal("tunnel").then(ClientCommandManager.literal("clear").executes(ctx -> {
                    NetherSession.clearTunnel();
                    ctx.getSource().sendFeedback(Component.literal("Gang-Markierung gelöscht."));
                    return 1;
                })))
                .then(ClientCommandManager.literal("chunks").executes(ctx -> {
                    Config.I.chunkEnabled = !Config.I.chunkEnabled;
                    Config.save();
                    ctx.getSource().sendFeedback(Component.literal("Chunkgrenzen: " + (Config.I.chunkEnabled ? "AN" : "AUS")));
                    return 1;
                })));
    }
}

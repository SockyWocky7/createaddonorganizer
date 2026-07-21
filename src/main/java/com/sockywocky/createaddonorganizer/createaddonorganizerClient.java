package com.sockywocky.createaddonorganizer;

import com.sockywocky.createaddonorganizer.client.DevMode;
import com.sockywocky.createaddonorganizer.client.Notice;
import com.sockywocky.createaddonorganizer.client.RemoteBannerPools;
import com.sockywocky.createaddonorganizer.client.RemoteBanners;
import com.sockywocky.createaddonorganizer.client.RemoteBoxTextures;
import com.sockywocky.createaddonorganizer.client.SectionColorsScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = createaddonorganizer.MODID, dist = Dist.CLIENT)
public class createaddonorganizerClient {

    private static final int REQUIRED_STABLE_TICKS = 5;

    private static final int MAX_ORGANIZE_ATTEMPTS = 10;

    private static boolean done = false;
    private static boolean remoteSyncStarted = false;
    private static ClientLevel lastSeenLevel;
    private static int stableTicks = 0;
    private static int organizeAttempts = 0;

    public createaddonorganizerClient(ModContainer container) {

        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new SectionColorsScreen(parent, modContainer));

        NeoForge.EVENT_BUS.addListener(createaddonorganizerClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(createaddonorganizerClient::onScreenRender);
    }

    private static void onScreenRender(ScreenEvent.Render.Post event) {
        Notice.render(event.getGuiGraphics(), Minecraft.getInstance());
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        DevMode.tick(Minecraft.getInstance());
        if (!remoteSyncStarted) {
            remoteSyncStarted = true;
            RemoteBanners.loadCacheFromDisk();
            RemoteBanners.syncAsync();
            RemoteBannerPools.loadCacheFromDisk();
            RemoteBannerPools.syncAsync();
            RemoteBoxTextures.loadCacheFromDisk();
            RemoteBoxTextures.syncAsync();
        }
        if (done) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            lastSeenLevel = null;
            stableTicks = 0;
            return;
        }
        if (mc.level == lastSeenLevel) {
            stableTicks++;
        } else {
            lastSeenLevel = mc.level;
            stableTicks = 1;
        }
        if (stableTicks < REQUIRED_STABLE_TICKS) {
            return;
        }

        organizeAttempts++;
        boolean succeeded = createaddonorganizer.organize(currentDisplayParams(mc));
        if (succeeded || organizeAttempts >= MAX_ORGANIZE_ATTEMPTS) {
            done = true;
        } else {
            stableTicks = 0;
        }
    }

    public static CreativeModeTab.ItemDisplayParameters currentDisplayParams(Minecraft mc) {
        boolean hasPermissions = mc.player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get();
        return new CreativeModeTab.ItemDisplayParameters(
                mc.level.enabledFeatures(), hasPermissions, mc.level.registryAccess());
    }
}

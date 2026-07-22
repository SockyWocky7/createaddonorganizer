package com.sockywocky.createaddonorganizer.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.mcexpanded.fancytabsections.Section.Section;
import net.mcexpanded.fancytabsections.creativetab.IndexPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

@Mixin(value = IndexPanel.class, remap = false)
public class IndexPanelMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void createaddonorganizer$suppressNativePanel(CreativeModeInventoryScreen screen, GuiGraphics g,
            List<Section<?>> sections, int mouseX, int mouseY, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private static void createaddonorganizer$suppressNativeClick(CreativeModeInventoryScreen screen,
            List<Section<?>> sections, float scrollOffs, double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private static void createaddonorganizer$suppressNativeScroll(CreativeModeInventoryScreen screen,
            List<Section<?>> sections, double mouseX, double mouseY, double scrollY,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}

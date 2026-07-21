package com.sockywocky.createaddonorganizer.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.mcexpanded.fancytabsections.Section.Section;
import net.mcexpanded.fancytabsections.creativetab.IndexPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

@Mixin(value = IndexPanel.class, remap = false)
public class IndexPanelMixin {

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private static void createaddonorganizer$playClickSound(CreativeModeInventoryScreen screen,
            List<Section<?>> sections, float scrollOffs, double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (button == 0 && cir.getReturnValueZ()) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}

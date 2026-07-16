package com.sockywocky.createaddonorganizer.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.sockywocky.createaddonorganizer.AbsorbedTabs;
import com.sockywocky.createaddonorganizer.client.CollapseSync;
import com.sockywocky.createaddonorganizer.client.SectionIndexPanel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;

@Mixin(CreativeModeInventoryScreen.class)
public class CreativeModeInventoryScreenMixin {

    @Redirect(
            method = "init",
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/common/CreativeModeTabRegistry;getSortedCreativeModeTabs()Ljava/util/List;"))
    private List<CreativeModeTab> createaddonorganizer$hideAbsorbedFromTabBar() {
        List<CreativeModeTab> all = CreativeModeTabRegistry.getSortedCreativeModeTabs();
        return all.stream().filter(tab -> {

            if (tab.getType() == CreativeModeTab.Type.CATEGORY
                    && (tab.getDisplayItems() == null || tab.getDisplayItems().isEmpty())) {
                return false;
            }
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            return id == null || !AbsorbedTabs.IDS.contains(id);
        }).toList();
    }

    @ModifyArg(
            method = "renderLabels",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"),
            index = 2)
    private int createaddonorganizer$shiftTitleX(int x) {
        return SectionIndexPanel.active() ? SectionIndexPanel.titleX() : x;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void createaddonorganizer$renderSectionIndex(GuiGraphics guiGraphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        SectionIndexPanel.render((CreativeModeInventoryScreen) (Object) this, guiGraphics, mouseX, mouseY);
        CollapseSync.tick();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void createaddonorganizer$sectionIndexClick(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (SectionIndexPanel.mouseClicked((CreativeModeInventoryScreen) (Object) this, mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void createaddonorganizer$sectionIndexRelease(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (SectionIndexPanel.mouseReleased((CreativeModeInventoryScreen) (Object) this, mouseX, mouseY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void createaddonorganizer$sectionIndexScroll(double mouseX, double mouseY, double scrollX,
            double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (SectionIndexPanel.mouseScrolled((CreativeModeInventoryScreen) (Object) this, mouseX, mouseY, scrollY)) {
            cir.setReturnValue(true);
        }
    }

}

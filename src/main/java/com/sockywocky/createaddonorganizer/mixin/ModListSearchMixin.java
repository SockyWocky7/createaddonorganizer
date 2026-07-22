package com.sockywocky.createaddonorganizer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.sockywocky.createaddonorganizer.createaddonorganizer;

import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforgespi.language.IModInfo;

@Mixin(ModListScreen.class)
public abstract class ModListSearchMixin {

    private static final String SEARCH_ALIASES = "cao createaddonorganizer create addon organizer";

    @Redirect(method = "lambda$reloadMods$10",
            require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforgespi/language/IModInfo;getDisplayName()Ljava/lang/String;"))
    private static String createaddonorganizer$searchableDisplayName(IModInfo info) {
        String name = info.getDisplayName();
        return createaddonorganizer.MODID.equals(info.getModId()) ? name + " " + SEARCH_ALIASES : name;
    }
}

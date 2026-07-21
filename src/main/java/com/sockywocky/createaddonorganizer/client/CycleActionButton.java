package com.sockywocky.createaddonorganizer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class CycleActionButton extends Button {
    private final Runnable onBackward;

    CycleActionButton(int x, int y, int width, int height, Component message, Runnable onForward, Runnable onBackward) {
        super(x, y, width, height, message, b -> onForward.run(), DEFAULT_NARRATION);
        this.onBackward = onBackward;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            if (this.active && this.visible && this.clicked(mouseX, mouseY)) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                onBackward.run();
                return true;
            }
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

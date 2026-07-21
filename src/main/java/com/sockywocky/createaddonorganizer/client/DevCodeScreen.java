package com.sockywocky.createaddonorganizer.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DevCodeScreen extends Screen {
    private final Screen parent;
    private EditBox codeBox;
    private Button okButton;

    public DevCodeScreen(Screen parent) {
        super(Component.translatable("createaddonorganizer.devmode.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        codeBox = new EditBox(this.font, this.width / 2 - 100, this.height / 2 - 24, 200, 20, Component.empty());
        codeBox.setMaxLength(32);
        codeBox.setFilter(s -> s.chars().allMatch(Character::isLetterOrDigit));
        codeBox.setResponder(s -> okButton.active = !s.isEmpty());
        addRenderableWidget(codeBox);
        setInitialFocus(codeBox);

        okButton = addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.ok"), b -> confirm())
                .bounds(this.width / 2 - 102, this.height / 2 + 4, 100, 20).build());
        okButton.active = false;
        addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.cancel"), b -> onClose())
                .bounds(this.width / 2 + 2, this.height / 2 + 4, 100, 20).build());
    }

    private void confirm() {
        if (DevMode.isLockedOut()) {
            return;
        }
        String code = codeBox.getValue().trim();
        if (code.isEmpty()) {
            return;
        }
        if (DevMode.checkCode(code)) {
            this.minecraft.setScreen(parent);
            Notice.show(Component.translatable("createaddonorganizer.devmode.activated"), Notice.GREEN);
        } else {
            this.minecraft.stop();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 54, 0xFFFFFFFF);
        boolean lockedOut = DevMode.isLockedOut();
        codeBox.active = !lockedOut;
        if (lockedOut) {
            long seconds = (DevMode.lockoutRemainingMillis() + 999) / 1000;
            g.drawCenteredString(this.font,
                    Component.translatable("createaddonorganizer.devmode.lockedout", seconds),
                    this.width / 2, this.height / 2 - 40, 0xFFFF5555);
            okButton.active = false;
        } else {
            okButton.active = !codeBox.getValue().isEmpty();
            if (!DevMode.isAuthorizedUser()) {
                g.drawCenteredString(this.font, Component.translatable("createaddonorganizer.devmode.unauthorized"),
                        this.width / 2, this.height / 2 - 40, 0xFFFF5555);
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}

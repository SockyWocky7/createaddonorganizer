package com.sockywocky.createaddonorganizer.client;

import java.io.IOException;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.sockywocky.createaddonorganizer.client.Presets.PresetData;
import com.sockywocky.createaddonorganizer.client.Presets.PresetRef;
import com.sockywocky.createaddonorganizer.createaddonorganizer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PresetsScreen extends Screen {
    private final Screen parent;
    private PresetList list;
    private String renamingRef;
    private EditBox renameBox;
    private RenameIconButton renameConfirm;
    private RenameIconButton renameCancel;

    public PresetsScreen(Screen parent) {
        super(Component.translatable("createaddonorganizer.colors.presets.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.presets.saveNew"),
                        b -> this.minecraft.setScreen(new NewPresetScreen(this)))
                .bounds(this.width / 2 - 100, 54, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.presets.import"),
                        b -> importPreset())
                .bounds(this.width / 2 - 100, 78, 200, 20).build());

        int listTop = 106;
        int listBottom = this.height - 40;
        list = new PresetList(this.minecraft, this.width, listBottom - listTop, listTop, 24);
        for (PresetRef ref : Presets.gallery()) {
            list.add(ref);
        }
        addRenderableWidget(list);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        float scale = 1.6f;
        g.pose().pushPose();
        g.pose().scale(scale, scale, scale);
        g.drawCenteredString(this.font, this.title, Math.round(this.width / 2 / scale), Math.round(16 / scale), 0xFFFFFFFF);
        g.pose().popPose();

        g.drawCenteredString(this.font, Component.translatable("createaddonorganizer.colors.presets.description"),
                this.width / 2, 34, 0xAAAAAAAA);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renamingRef != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
                return true;
            }
            if (renameBox.keyPressed(keyCode, scanCode, modifiers) || renameBox.canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (renameBox != null && renameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (renamingRef != null) {
            if (renameConfirm.mouseClicked(mouseX, mouseY, button)
                    || renameCancel.mouseClicked(mouseX, mouseY, button)
                    || renameBox.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            cancelRename();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void startRename(PresetRef ref) {
        renamingRef = ref.ref();
        renameBox = new EditBox(this.font, 0, 0, 100, 20, Component.empty());
        renameBox.setMaxLength(64);
        renameBox.setValue(ref.name());
        renameBox.setHighlightPos(0);
        renameBox.setFocused(true);
        renameConfirm = new RenameIconButton(true, Component.translatable("createaddonorganizer.colors.ok"),
                b -> confirmRename());
        renameCancel = new RenameIconButton(false, Component.translatable("createaddonorganizer.colors.cancel"),
                b -> cancelRename());
    }

    private void confirmRename() {
        if (renamingRef == null) {
            return;
        }
        String ref = renamingRef;
        String name = renameBox.getValue().trim();
        if (!name.isEmpty()) {
            Presets.rename(ref, name);
            list.updateName(ref, name);
        }
        cancelRename();
    }

    private void cancelRename() {
        renamingRef = null;
        renameBox = null;
        renameConfirm = null;
        renameCancel = null;
    }

    private void importPreset() {
        Presets.chooseImportFile().ifPresent(path -> {
            PresetData data = Presets.loadExternal(path);
            if (data == null) {
                Notice.show(Component.translatable("createaddonorganizer.colors.presets.import.failed"), Notice.RED);
                return;
            }
            try {
                Presets.save(data);
                Notice.show(Component.translatable("createaddonorganizer.colors.presets.import.success", data.name()), Notice.GREEN);
                this.rebuildWidgets();
            } catch (IOException e) {
                createaddonorganizer.LOGGER.warn("[CAO] failed to import preset {}", path, e);
            }
        });
    }

    private void applyWithConfirm(PresetRef ref) {
        Component message = Component.translatable("createaddonorganizer.colors.presets.applyConfirm.message");
        if ("Rainbow".equalsIgnoreCase(ref.name())) {
            message = message.copy().append("\n\n").append(
                    Component.translatable("createaddonorganizer.colors.presets.rainbowWarning")
                            .withStyle(ChatFormatting.RED));
        }
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                PresetData data = Presets.load(ref.ref());
                if (data != null) {
                    Presets.applyToConfig(data);
                    Presets.applyLive();
                    Notice.show(Component.translatable("createaddonorganizer.colors.presets.applied", ref.name()), Notice.GREEN);
                }
            }
            this.minecraft.setScreen(this);
        }, Component.translatable("createaddonorganizer.colors.presets.applyConfirm.title"), message));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private class PresetList extends ContainerObjectSelectionList<PresetList.Row> {
        PresetList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
        }

        void add(PresetRef ref) {
            addEntry(new Row(ref));
        }

        void updateName(String ref, String newName) {
            for (Row row : children()) {
                if (row.ref.ref().equals(ref)) {
                    row.ref = new PresetRef(ref, newName);
                }
            }
        }

        @Override
        public int getRowWidth() {
            return 320;
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private PresetRef ref;
            private final Button edit;

            Row(PresetRef ref) {
                this.ref = ref;
                this.edit = Button.builder(Component.translatable("createaddonorganizer.colors.edit"),
                                b -> minecraft.setScreen(new PresetEditScreen(PresetsScreen.this, ref)))
                        .size(44, 20).build();
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(edit);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(edit);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (super.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                if (button == 0 && Screen.hasControlDown() && !ref.builtin()) {
                    PresetsScreen.this.startRename(ref);
                    return true;
                }
                if (button == 0) {
                    applyWithConfirm(ref);
                    return true;
                }
                return false;
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int rowWidth, int rowHeight,
                    int mouseX, int mouseY, boolean hovered, float partialTick) {
                int textY = top + (rowHeight - 8) / 2;
                String tag = ref.builtin() ? Component.translatable("createaddonorganizer.colors.presets.builtin").getString() : "";

                edit.setX(left + rowWidth - edit.getWidth());
                edit.setY(top + (rowHeight - 20) / 2);
                int labelX = edit.getX() - 10 - font.width(tag);

                if (ref.ref().equals(renamingRef)) {
                    renameCancel.setX(edit.getX() - 22);
                    renameCancel.setY(edit.getY());
                    renameConfirm.setX(renameCancel.getX() - 22);
                    renameConfirm.setY(edit.getY());
                    renameBox.setX(left);
                    renameBox.setY(edit.getY());
                    renameBox.setWidth(renameConfirm.getX() - 4 - left);
                    renameBox.render(g, mouseX, mouseY, partialTick);
                    renameConfirm.render(g, mouseX, mouseY, partialTick);
                    renameCancel.render(g, mouseX, mouseY, partialTick);
                } else if ("Rainbow".equalsIgnoreCase(ref.name())) {
                    drawRainbowName(g, ref.name(), left + 4, textY);
                    if (!tag.isEmpty()) {
                        g.drawString(font, tag, labelX, textY, 0xFFAAAAAA);
                    }
                } else {
                    int nameColor = "Images".equalsIgnoreCase(ref.name()) ? 0xFFFFFF55 : 0xFFFFFFFF;
                    g.drawString(font, ref.name(), left + 4, textY, nameColor);
                    if (!tag.isEmpty()) {
                        g.drawString(font, tag, labelX, textY, 0xFFAAAAAA);
                    }
                }
                edit.render(g, mouseX, mouseY, partialTick);
            }

            private void drawRainbowName(GuiGraphics g, String name, int x, int y) {
                int cursorX = x;
                for (int i = 0; i < name.length(); i++) {
                    String ch = name.substring(i, i + 1);
                    float hue = (float) i / name.length();
                    int color = 0xFF000000 | ColorUtil.hsvToRgb(hue, 1f, 1f);
                    g.drawString(font, ch, cursorX, y, color);
                    cursorX += font.width(ch);
                }
            }
        }
    }
}

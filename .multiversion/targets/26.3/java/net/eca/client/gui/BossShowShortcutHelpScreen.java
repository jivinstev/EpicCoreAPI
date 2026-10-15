package net.eca.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.util.List;

//快捷键说明独立显示，避免持续占用编辑工作区。
final class BossShowShortcutHelpScreen extends Screen implements BossShowEditorSessionScreen {

    // GLFW_KEY_ESCAPE value; GLFW is no longer exposed to this target's sources.
    private static final int KEY_ESCAPE = net.eca.mv.GLFW.GLFW_KEY_ESCAPE;

    private static final List<String> SHORTCUT_KEYS = List.of(
        "space", "arrows", "home_end", "in_out", "clipboard", "undo", "delete", "context", "input"
    );

    BossShowShortcutHelpScreen() {
        super(Component.translatable("gui.eca.bossshow.editor.shortcuts.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.eca.bossshow.editor.back"), b -> closePanel())
            .bounds(this.width / 2 - 60, this.height - 34, 120, 20).build());
    }

    private void closePanel() {
        this.minecraft.gui.setScreen(new BossShowEditorScreen());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == KEY_ESCAPE) {
            closePanel();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int panelWidth = Math.min(440, this.width - 24);
        int left = this.width / 2 - panelWidth / 2;
        int top = 18;
        int bottom = Math.min(this.height - 44, top + 26 + SHORTCUT_KEYS.size() * 17);
        g.fill(left, top, left + panelWidth, bottom, 0xE8171A22);
        int outlineHeight = bottom - top;
        g.fill(left, top, left + panelWidth, top + 1, 0xFF555B6A);
        g.fill(left, bottom - 1, left + panelWidth, bottom, 0xFF555B6A);
        g.fill(left, top + 1, left + 1, bottom - 1, 0xFF555B6A);
        g.fill(left + panelWidth - 1, top + 1, left + panelWidth, bottom - 1, 0xFF555B6A);
        g.centeredText(this.font, this.title, this.width / 2, top + 8, 0xFFFFFFFF);
        int y = top + 26;
        for (String key : SHORTCUT_KEYS) {
            g.text(this.font, Component.translatable("gui.eca.bossshow.editor.shortcuts." + key),
                left + 12, y, 0xFFD5D9E2, false);
            y += 17;
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }
}

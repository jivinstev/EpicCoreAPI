package net.eca.client.gui;

import net.eca.util.EcaLogger;
import net.eca.util.shader_generator.ShaderModuleDefinition;
import net.eca.util.shader_generator.ShaderModuleInstance;
import net.eca.util.shader_generator.ShaderProjectCodec;
import net.eca.util.shader_generator.ShaderProjectCodec.ProjectRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class ShaderEffectDetailsScreen extends Screen {

    private static final int PANEL_WIDTH = 360;
    private final Screen parent;
    private final Screen returnScreen;
    private final ShaderModuleInstance effect;
    private final ProjectRef projectRef;
    private final Consumer<ShaderModuleInstance> confirmHandler;
    private int scroll;
    private int visibleRows;
    private List<ShaderModuleDefinition.Parameter> visibleParameters = List.of();

    public ShaderEffectDetailsScreen(
        Screen parent,
        Screen returnScreen,
        ShaderModuleDefinition definition,
        ProjectRef projectRef,
        Consumer<ShaderModuleInstance> confirmHandler
    ) {
        super(Component.translatable("gui.eca.shader_generator.effects.details_title",
            Component.translatable(definition.displayName())));
        this.parent = parent;
        this.returnScreen = returnScreen;
        this.effect = definition.createInstance();
        this.projectRef = projectRef;
        this.confirmHandler = confirmHandler;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int rowY = 44;
        int controlsBottom = height - 42;

        /* 自定义图像元素：导入/替换图片按钮置顶 */
        if (effect.definition().id().equals("image_element")) {
            addRenderableWidget(Button.builder(
                Component.translatable(effect.imagePath() == null
                    ? "gui.eca.shader_generator.effects.import_image"
                    : "gui.eca.shader_generator.effects.replace_image"),
                button -> importImage()
            ).bounds(left, rowY, PANEL_WIDTH, 20).build());
            rowY += 26;
        }

        /* 颜色按钮 */
        addRenderableWidget(Button.builder(
            Component.translatable("gui.eca.shader_generator.parameter.color"),
            button -> openColorPicker()
        ).bounds(left, rowY, PANEL_WIDTH, 20).build());
        rowY += 26;

        /* 参数列表 */
        visibleParameters = effect.definition().parameters().stream()
            .filter(parameter -> !isColorParameter(parameter.key()))
            .toList();
        visibleRows = Math.max(1, (controlsBottom - rowY) / 22);
        int maxScroll = Math.max(0, visibleParameters.size() - visibleRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int end = Math.min(visibleParameters.size(), scroll + visibleRows);
        for (int index = scroll; index < end; index++) {
            ShaderModuleDefinition.Parameter parameter = visibleParameters.get(index);
            int y = rowY + (index - scroll) * 22;
            addRenderableWidget(Button.builder(
                Component.literal("-"),
                button -> adjust(parameter, -parameter.step())
            ).bounds(left + 220, y, 36, 20).build());
            if (isAlphaParameter(parameter.key())) {
                addRenderableWidget(new ShaderPercentEditWidget(
                    left + 260,
                    y,
                    56,
                    20,
                    Component.translatable(parameter.displayName()),
                    () -> effect.value(parameter.key()),
                    value -> setParameter(parameter, (float) value),
                    value -> Component.literal(ShaderPercentEditWidget.displayPercent(value) + "%")
                ));
            } else {
                addRenderableWidget(Button.builder(
                    Component.literal(String.format(Locale.ROOT, "%.2f", effect.value(parameter.key()))),
                    button -> {}
                ).bounds(left + 260, y, 56, 20).build());
            }
            addRenderableWidget(Button.builder(
                Component.literal("+"),
                button -> adjust(parameter, parameter.step())
            ).bounds(left + 320, y, 40, 20).build());
        }

        addRenderableWidget(Button.builder(
            Component.translatable("gui.eca.shader_generator.button.confirm"),
            button -> confirm()
        ).bounds(width / 2 - 104, height - 30, 96, 20).build());
        addRenderableWidget(Button.builder(
            Component.translatable("gui.eca.shader_generator.button.cancel"),
            button -> onClose()
        ).bounds(width / 2 + 8, height - 30, 96, 20).build());
    }

    private void adjust(ShaderModuleDefinition.Parameter parameter, float delta) {
        setParameter(parameter, effect.value(parameter.key()) + delta);
        rebuildWidgets();
    }

    private void setParameter(ShaderModuleDefinition.Parameter parameter, float value) {
        effect.setValue(parameter.key(), value);
    }

    private void openColorPicker() {
        Minecraft.getInstance().gui.setScreen(new ShaderColorPickerScreen(
            this,
            effect.value("color_r"),
            effect.value("color_g"),
            effect.value("color_b"),
            effect.value("color_a"),
            (red, green, blue, alpha) -> {
                effect.setValue("color_r", red);
                effect.setValue("color_g", green);
                effect.setValue("color_b", blue);
                effect.setValue("color_a", alpha);
            }
        ));
    }

    private void importImage() {
        if (projectRef == null) {
            return;
        }
        try {
            FileDialog dialog = new FileDialog((Frame) null, "Select PNG Image", FileDialog.LOAD);
            dialog.setFilenameFilter((directory, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
            dialog.setVisible(true);
            String selected = dialog.getFile() == null
                ? null
                : new File(dialog.getDirectory(), dialog.getFile()).getAbsolutePath();
            if (selected != null) {
                effect.setImagePath(ShaderProjectCodec.importTexture(projectRef, Path.of(selected)));
                clearWidgets();
                setFocused(null);
                init();
            }
        } catch (Exception e) {
            EcaLogger.error("File dialog failed: {}", e.getMessage());
        }
    }

    private void confirm() {
        if (effect.definition().id().equals("image_element") && effect.imagePath() == null) {
            return;
        }
        confirmHandler.accept(effect);
        Minecraft.getInstance().gui.setScreen(returnScreen);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        int maxScroll = Math.max(0, visibleParameters.size() - visibleRows);
        int next = Math.max(0, Math.min(maxScroll, scroll + (delta > 0.0 ? -1 : 1)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = (width - PANEL_WIDTH) / 2;
        /* 颜色色块：紧跟在标题下方，与颜色按钮的 y=44（或含导入则 y=70）对齐 */
        int swatchY = effect.definition().id().equals("image_element") ? 70 : 44;
        graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
        int color = colorArgb();
        graphics.fill(left + 8, swatchY + 4, left + 44, swatchY + 16, color);
        int outlineX = left + 8;
        int outlineY = swatchY + 4;
        graphics.fill(outlineX, outlineY, outlineX + 36, outlineY + 1, 0xFFFFFFFF);
        graphics.fill(outlineX, outlineY + 11, outlineX + 36, outlineY + 12, 0xFFFFFFFF);
        graphics.fill(outlineX, outlineY + 1, outlineX + 1, outlineY + 11, 0xFFFFFFFF);
        graphics.fill(outlineX + 35, outlineY + 1, outlineX + 36, outlineY + 11, 0xFFFFFFFF);

        /* 参数标签：与 init 中 rowY + (index - scroll) * 22 对应 */
        int paramBaseY = swatchY + 26;
        int end = Math.min(visibleParameters.size(), scroll + visibleRows);
        for (int index = scroll; index < end; index++) {
            ShaderModuleDefinition.Parameter parameter = visibleParameters.get(index);
            int y = paramBaseY + (index - scroll) * 22;
            graphics.text(
                font,
                Component.translatable(parameter.displayName()),
                left,
                y,
                0xFFC7CBD1,
                false
            );
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private int colorArgb() {
        return channel(effect.value("color_a")) << 24
            | channel(effect.value("color_r")) << 16
            | channel(effect.value("color_g")) << 8
            | channel(effect.value("color_b"));
    }

    private static boolean isColorParameter(String key) {
        return key.equals("color_r")
            || key.equals("color_g")
            || key.equals("color_b")
            || key.equals("color_a");
    }

    private static boolean isAlphaParameter(String key) {
        return key.endsWith("_alpha") || key.equals("alpha");
    }

    private static int channel(float value) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F);
    }
}

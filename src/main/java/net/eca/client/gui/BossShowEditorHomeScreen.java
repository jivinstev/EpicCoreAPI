package net.eca.client.gui;


import net.eca.network.BossShowDeleteEditorPacket;
import net.eca.network.BossShowExitEditorPacket;
import net.eca.network.BossShowOpenEditorHomePacket;
import net.eca.network.NetworkHandler;
import net.eca.util.bossshow.BossShowDefinition;
import net.eca.util.bossshow.BossShowEditorState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

//BossShow 编辑器 Home 界面：选择已有定义或进入实体选择模式新建
public class BossShowEditorHomeScreen extends Screen implements BossShowEditorSessionScreen {

    private DefList defList;
    private Button createBtn;
    private Button closeBtn;

    public BossShowEditorHomeScreen() {
        super(Component.translatable("gui.eca.bossshow.home.title"));
    }

    public static void openFromPacket(BossShowOpenEditorHomePacket msg) {
        BossShowEditorState.beginSession(msg.definitions());
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreenAndShow(new BossShowEditorHomeScreen()));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;

        //=== 顶部 New cutscene 按钮 ===
        createBtn = Button.builder(Component.translatable("gui.eca.bossshow.home.new"), b -> startSelectionMode())
            .bounds(centerX - 130, 50, 260, 24)
            .build();
        this.addRenderableWidget(createBtn);

        //=== 中间 Existing list ===
        int listTop = 110;
        int listBottom = this.height - 40;
        defList = new DefList(this.minecraft, this.width - 40, listBottom - listTop, listTop, 24);
        defList.setX(20);
        for (BossShowDefinition def : BossShowEditorState.getAvailableDefs()) {
            defList.addDef(def);
        }
        this.addWidget(defList);

        //=== 底部 Close ===
        closeBtn = Button.builder(Component.translatable("gui.eca.bossshow.home.close"), b -> doClose())
            .bounds(centerX - 60, this.height - 28, 120, 20)
            .build();
        this.addRenderableWidget(closeBtn);
    }

    //进入实体选择模式：关闭 Screen 让游戏恢复运行，事件处理器接管
    private void startSelectionMode() {
        BossShowEditorState.enterSelectionMode();
        this.minecraft.setScreenAndShow(null);
    }

    //进入"播放选择"模式：和 selection 类似，但右键命中后客户端发包让服务端播放
    private void startPlaySelection(BossShowDefinition def) {
        BossShowEditorState.enterPlaySelection(def.id());
        this.minecraft.setScreenAndShow(null);
    }

    private void editExisting(BossShowDefinition def) {
        BossShowEditorState.enter(def);
        //没有锚点时，把 anchor 落到玩家前方 4 格的空间坐标上（不绑定实体）
        //非空定义保留录制参考朝向，让无实体预览维持原有空间布局。
        if (!BossShowEditorState.hasAnchor()) {
            LocalPlayer p = this.minecraft.player;
            if (p != null) {
                Vec3 look = p.getViewVector(1.0f);
                double ax = p.getX() + look.x * 4.0;
                double ay = p.getY();
                double az = p.getZ() + look.z * 4.0;
                if (def.frames().isEmpty()) {
                    BossShowEditorState.setAnchor(null, ax, ay, az, 0f);
                } else {
                    BossShowEditorState.setAnchorPositionKeepYaw(null, ax, ay, az);
                }
            }
        }
        this.minecraft.setScreenAndShow(new BossShowEditorScreen());
    }

    //请求删除：弹二次确认 → 发包 → 服务端删完会重发 Home 包刷新列表
    private void requestDelete(BossShowDefinition def) {
        ConfirmScreen confirm = new ConfirmScreen(
            confirmed -> {
                if (confirmed) {
                    NetworkHandler.sendToServer(new BossShowDeleteEditorPacket(def.id()));
                    //服务端会重发 Home 包，届时 openFromPacket 会重建本界面
                }
                this.minecraft.setScreenAndShow(this);
            },
            Component.translatable("gui.eca.bossshow.home.delete.title"),
            Component.translatable("gui.eca.bossshow.home.delete.body", def.id().toString())
        );
        this.minecraft.setScreenAndShow(confirm);
    }

    private void doClose() {
        NetworkHandler.sendToServer(new BossShowExitEditorPacket());
        BossShowEditorState.exit();
        this.minecraft.setScreenAndShow(null);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == 256 /* GLFW_KEY_ESCAPE */) {
            if (this.getFocused() instanceof EditBox eb && eb.isFocused()) {
                eb.setFocused(false);
                this.setFocused(null);
                return true;
            }
            doClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        //完全透明，世界透出来

        g.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        g.centeredText(this.font,
            Component.translatable("gui.eca.bossshow.home.subtitle"),
            this.width / 2, 30, 0xAAAAAA);

        //创建按钮下方说明
        g.centeredText(this.font,
            Component.translatable("gui.eca.bossshow.home.new_hint"),
            this.width / 2, 80, 0xFF888888);

        //列表标题
        int listHeaderY = 96;
        g.centeredText(this.font,
            Component.translatable("gui.eca.bossshow.home.existing",
                BossShowEditorState.getAvailableDefs().size()),
            this.width / 2, listHeaderY, 0xFFAAAAAA);

        //列表
        if (defList != null) defList.extractRenderState(g, mouseX, mouseY, partialTick);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    //=== 内嵌 def 列表 widget ===
    private class DefList extends ObjectSelectionList<DefList.DefEntry> {
        DefList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
        }

        public void addDef(BossShowDefinition def) {
            this.addEntry(new DefEntry(def));
        }

        @Override
        public int getRowWidth() {
            return this.width - 12;
        }

        class DefEntry extends ObjectSelectionList.Entry<DefEntry> {
            private final BossShowDefinition def;
            private final Button playBtn;
            private final Button editBtn;
            private final Button deleteBtn;

            DefEntry(BossShowDefinition def) {
                this.def = def;
                this.playBtn = Button.builder(Component.translatable("gui.eca.bossshow.home.play"), b -> startPlaySelection(this.def))
                    .bounds(0, 0, 40, 18)
                    .build();
                this.editBtn = Button.builder(Component.translatable("gui.eca.bossshow.home.edit"), b -> editExisting(this.def))
                    .bounds(0, 0, 40, 18)
                    .build();
                this.deleteBtn = Button.builder(Component.translatable("gui.eca.bossshow.home.delete"), b -> requestDelete(this.def))
                    .bounds(0, 0, 40, 18)
                    .build();
            }

            @Override
            public Component getNarration() {
                return Component.literal(def.id().toString());
            }

            @Override
            public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean isHovering, float partialTick) {
                int top = this.getContentY();
                int left = this.getContentX();
                int width = this.getContentWidth();
                Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(def.targetType());
                String idLine = def.id().toString();
                int contentCount = 0;
                for (BossShowDefinition.Frame f : def.frames()) { if (f.keyframe() != null) contentCount++; }
                String meta = (typeId != null ? typeId.toString() : "?")
                    + "    " + def.frames().size() + " frames    "
                    + contentCount + " content ticks    " + def.trigger().type();
                g.text(Minecraft.getInstance().font, idLine, left + 4, top + 2, 0xFFFFFF, false);
                g.text(Minecraft.getInstance().font, meta, left + 4, top + 12, 0xAAAAAA, false);

                //右侧按钮（从右到左）：删除 | 编辑 | 播放
                deleteBtn.setX(left + width - 44);
                deleteBtn.setY(top + 2);
                editBtn.setX(left + width - 88);
                editBtn.setY(top + 2);
                playBtn.setX(left + width - 132);
                playBtn.setY(top + 2);
                deleteBtn.extractRenderState(g, mouseX, mouseY, partialTick);
                editBtn.extractRenderState(g, mouseX, mouseY, partialTick);
                playBtn.extractRenderState(g, mouseX, mouseY, partialTick);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                if (deleteBtn.mouseClicked(event, doubleClick)) {
                    return true;
                }
                if (editBtn.mouseClicked(event, doubleClick)) {
                    return true;
                }
                if (playBtn.mouseClicked(event, doubleClick)) {
                    return true;
                }
                return super.mouseClicked(event, doubleClick);
            }
        }
    }
}

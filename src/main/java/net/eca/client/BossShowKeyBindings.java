package net.eca.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.eca.EcaMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

//ECA 客户端按键绑定注册
@EventBusSubscriber(modid = EcaMod.MOD_ID, value = Dist.CLIENT)
public final class BossShowKeyBindings {

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "keys"));

    //J = 开始/恢复录制
    public static final KeyMapping REC_START = new KeyMapping(
        "key.eca.bossshow.rec_start",
        KeyConflictContext.UNIVERSAL,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_J,
        CATEGORY
    );

    //I = 暂停录制
    public static final KeyMapping REC_PAUSE = new KeyMapping(
        "key.eca.bossshow.rec_pause",
        KeyConflictContext.UNIVERSAL,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_I,
        CATEGORY
    );

    private BossShowKeyBindings() {}

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(REC_START);
        event.register(REC_PAUSE);
    }
}

package net.eca.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.eca.EcaMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;


//ECA 客户端按键绑定注册
@EventBusSubscriber(modid = EcaMod.MOD_ID, value = Dist.CLIENT)
public final class BossShowKeyBindings {

    public static final net.minecraft.client.KeyMapping.Category CATEGORY =
        net.minecraft.client.KeyMapping.Category.register(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("eca", "categories"));

    //J = 开始/恢复录制
    public static final KeyMapping REC_START = new KeyMapping(
        "key.eca.bossshow.rec_start",
        KeyConflictContext.UNIVERSAL,
        InputConstants.getKey("key.keyboard.j"),
        CATEGORY
    );

    //I = 暂停录制
    public static final KeyMapping REC_PAUSE = new KeyMapping(
        "key.eca.bossshow.rec_pause",
        KeyConflictContext.UNIVERSAL,
        InputConstants.getKey("key.keyboard.i"),
        CATEGORY
    );

    private BossShowKeyBindings() {}

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(REC_START);
        event.register(REC_PAUSE);
    }
}

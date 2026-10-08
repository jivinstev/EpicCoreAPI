package net.eca.init;

import net.eca.EcaMod;
import net.eca.client.render.ArcaneRenderTypes;
import net.eca.client.render.AuroraRenderTypes;
import net.eca.client.render.BlackHoleRenderTypes;
import net.eca.client.render.CosmosRenderTypes;
import net.eca.client.render.DreamSakuraRenderTypes;
import net.eca.client.render.ForestRenderTypes;
import net.eca.client.render.HackerRenderTypes;
import net.eca.client.render.OceanRenderTypes;
import net.eca.client.render.StarlightRenderTypes;
import net.eca.client.render.StormRenderTypes;
import net.eca.client.render.TheLastEndRenderTypes;
import net.eca.client.render.VolcanoRenderTypes;
import net.eca.client.render.shader.ArcaneShader;
import net.eca.client.render.shader.AuroraShader;
import net.eca.client.render.shader.BlackHoleShader;
import net.eca.client.render.shader.FilterRenderer;
import net.eca.client.render.shader.CosmosShader;
import net.eca.client.render.shader.DreamSakuraShader;
import net.eca.client.render.shader.ForestShader;
import net.eca.client.render.shader.HackerShader;
import net.eca.client.render.shader.OceanShader;
import net.eca.client.render.shader.StarlightShader;
import net.eca.client.render.shader.StormShader;
import net.eca.client.render.shader.TheLastEndShader;
import net.eca.client.render.shader.VolcanoShader;
import net.eca.client.render.shader.EcaShaderInstance;
import net.eca.client.render.shader.ShaderRegistration;
import net.eca.client.render.preset.ShaderPresetRegistry;
import net.eca.util.EcaLogger;
import net.eca.util.entity_extension.GlobalEffectRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.io.IOException;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = EcaMod.MOD_ID, value = Dist.CLIENT)
public class ModRenderTypes {

    //26.x 没有 RegisterShadersEvent：客户端初始化时先从模组 jar 注册，资源重载时再按资源包重建
    @SubscribeEvent
    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) throws IOException {
        onRegisterShaders(ShaderRegistration.fromModJar());
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "shaders"), (ResourceManagerReloadListener) manager -> {
            try {
                onRegisterShaders(ShaderRegistration.of(manager));
            } catch (IOException e) {
                EcaLogger.warn("[ModRenderTypes] failed to reload shaders: {}", e.toString());
            }
        });
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        EcaShaderInstance.endFrame();
    }

    public static void onRegisterShaders(ShaderRegistration event) throws IOException {
        TheLastEndShader.register(event);
        DreamSakuraShader.register(event);
        ForestShader.register(event);
        OceanShader.register(event);
        StormShader.register(event);
        VolcanoShader.register(event);
        ArcaneShader.register(event);
        AuroraShader.register(event);
        HackerShader.register(event);
        StarlightShader.register(event);
        CosmosShader.register(event);
        BlackHoleShader.register(event);
        FilterRenderer.registerShaders(event);

        registerSkyboxPresets();

        //重建所有第三方自定义预设的 ShaderInstance（首帧加载与每次资源重载都会触发）
        ShaderPresetRegistry.onRegisterShaders(event);
    }

    private static void registerSkyboxPresets() {
        GlobalEffectRegistry.registerSkyboxPreset(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "the_last_end"), TheLastEndRenderTypes.SKYBOX);
        GlobalEffectRegistry.registerSkyboxPreset(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "dream_sakura"), DreamSakuraRenderTypes.SKYBOX);
        GlobalEffectRegistry.registerSkyboxPreset(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "forest"), ForestRenderTypes.SKYBOX);
        GlobalEffectRegistry.registerSkyboxPreset(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "ocean"), OceanRenderTypes.SKYBOX);
        GlobalEffectRegistry.registerSkyboxPreset(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "storm"), StormRenderTypes.SKYBOX);
        GlobalEffectRegistry.registerSkyboxPreset(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "volcano"), VolcanoRenderTypes.SKYBOX);
        GlobalEffectRegistry.registerSkyboxPreset(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "arcane"), ArcaneRenderTypes.SKYBOX);
        GlobalEffectRegistry.registerSkyboxPreset(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "aurora"), AuroraRenderTypes.SKYBOX);
        GlobalEffectRegistry.registerSkyboxPreset(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "hacker"), HackerRenderTypes.SKYBOX);
        GlobalEffectRegistry.registerSkyboxPreset(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "starlight"), StarlightRenderTypes.SKYBOX);
        GlobalEffectRegistry.registerSkyboxPreset(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "cosmos"), CosmosRenderTypes.SKYBOX);
        GlobalEffectRegistry.registerSkyboxPreset(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "black_hole"), BlackHoleRenderTypes.SKYBOX);
    }

    private ModRenderTypes() {}
}

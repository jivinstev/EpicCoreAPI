package net.eca.init;

import net.eca.config.EcaConfiguration;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;

@SuppressWarnings("removal")
//Mod配置注册类
public class ModConfigs {
    public static void register() {
        ModLoadingContext.get().getActiveContainer().registerConfig(
            ModConfig.Type.COMMON,
            EcaConfiguration.SPEC,
            "eca.toml"
        );
    }
}

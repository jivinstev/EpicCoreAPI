package net.eca.init;

import net.eca.EcaMod;
import net.eca.command.EcaCommand;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid = EcaMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        EcaCommand.register(event.getDispatcher());
    }
}

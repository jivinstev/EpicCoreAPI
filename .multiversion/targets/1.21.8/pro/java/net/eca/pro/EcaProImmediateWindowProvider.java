// Fork multi-version layer: 1.21.8 version of the authors' file (FML 9's ImmediateWindowProvider; see the comments below).
package net.eca.pro;

import net.eca.coremod.RuntimeExtensionBridge;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;


// This service is loaded before transformation services, closing the unprotected startup window.
public final class EcaProImmediateWindowProvider implements ImmediateWindowProvider {
    static {
        RuntimeExtensionBridge.prepareEarly();
        RuntimeExtensionBridge.afterAgentReady();
    }

    @Override
    public String name() {
        return "eca_pro_bootstrap";
    }

    @Override
    public Runnable initialize(String[] arguments) {
        return () -> {
        };
    }

    // FML 9 (NeoForge 21.8) has no updateModuleReads: ImmediateWindowHandler loads the provider with a plain
    // ServiceLoader.load(serviceLayer, ImmediateWindowProvider.class) and never hands it the game layer. FML 7 called it
    // so a provider that opens GLFW/OpenGL itself could add the reads it needs to the game's module layer; this provider
    // draws nothing and owns no window, so there is nothing to replace it with and the method is gone.

    // FML 7 (NeoForge 21.5): ImmediateWindowProvider is an EarlyLoadingScreenController, and the window/overlay methods the
    // 1.21.4 version stubbed out (updateFramebufferSize, setupMinecraftWindow, positionWindow, loadingOverlay, getGLVersion)
    // left the interface. This provider still draws nothing and never owns the window.
    @Override
    public long takeOverGlfwWindow() {
        return 0L;
    }

    @Override
    public void periodicTick() {
    }

    @Override
    public void updateProgress(String label) {
    }

    @Override
    public void completeProgress() {
    }

    @Override
    public void crash(String message) {
    }
}

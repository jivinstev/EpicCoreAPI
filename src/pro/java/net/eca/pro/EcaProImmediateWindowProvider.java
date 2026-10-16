// Fork multi-version layer: 1.21.5 version of the authors' file (FML 7's ImmediateWindowProvider; see the comment below).
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

    @Override
    public void updateModuleReads(ModuleLayer layer) {
    }

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

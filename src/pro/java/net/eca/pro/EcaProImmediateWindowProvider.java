// Fork multi-version layer: 1.21.10 version of the authors' file (FML 10's ImmediateWindowProvider: initialize takes the
// ProgramArgs and returns nothing, the provider is told the Minecraft and NeoForge versions, and it must be able to show
// a fatal loading error and exit). Built on the 1.21.8 version; see its comments for the methods that left the interface.
package net.eca.pro;

import net.eca.coremod.RuntimeExtensionBridge;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.EarlyLoadingScreenController;
import net.neoforged.fml.loading.ProgramArgs;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;


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
    public void initialize(ProgramArgs arguments) {
    }

    @Override
    public void setMinecraftVersion(String version) {
    }

    @Override
    public void setNeoForgeVersion(String version) {
    }

    // FML 10 asks the active provider to show a loading failure and exit. This provider draws nothing and owns no window, so
    // there is nothing to show it in: the issues go to stderr (the crash report FML writes carries them too) and the
    // process ends, which is what the window-owning provider does once its dialog is dismissed.
    @Override
    public void displayFatalErrorAndExit(List<ModLoadingIssue> issues, Path modsFolder, Path logFile, Path crashReportFile) {
        for (ModLoadingIssue issue : issues) {
            System.err.println("[eca_pro_bootstrap] " + issue);
        }
        System.exit(1);
    }

    // FML 9 (NeoForge 21.8) has no updateModuleReads: ImmediateWindowHandler loads the provider with a plain
    // ServiceLoader.load(serviceLayer, ImmediateWindowProvider.class) and never hands it the game layer. FML 7 called it
    // so a provider that opens GLFW/OpenGL itself could add the reads it needs to the game's module layer; this provider
    // draws nothing and owns no window, so there is nothing to replace it with and the method is gone.

    // FML 7 (NeoForge 21.5): ImmediateWindowProvider is an EarlyLoadingScreenController, and the window/overlay methods the
    // 1.21.4 version stubbed out (updateFramebufferSize, setupMinecraftWindow, positionWindow, loadingOverlay, getGLVersion)
    // left the interface. This provider still draws nothing and never owns the window.
    // FML 12 (NeoForge 26.x): takeOverGlfwWindow is gone; the window is handed over through handOverToMinecraft, which
    // returns the window state Minecraft should restore. This provider owns no window, so it reports Minecraft's default size.
    @Override
    public EarlyLoadingScreenController.WindowState handOverToMinecraft(Supplier<Object> renderBackend) {
        return new EarlyLoadingScreenController.WindowState(0, 0, 854, 480, false, false, false, false);
    }

    // This provider polls no window events, so there is nothing to stop before the handoff.
    @Override
    public void stopEventPolling() {
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

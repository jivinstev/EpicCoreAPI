// MV-STAND-IN: 26.3 kept this stand-in over 26.2's FML 10/11 port (cascade 2026-10-21): FML earlydisplay 12.0.12's
// MV-STAND-IN: LoadingScreenRenderer.renderToFramebuffer(RenderContext) only renders the elements (no activate/glClear/deactivate;
// MV-STAND-IN: a backend abstraction), so that port has nothing to edit here (ECA-015).
// MV-STAND-IN: 1.21.11's prepared copy of the authors' RuntimeExtensionBridge as it was before 1.21.10 took 1.21.8's overlay
// MV-STAND-IN: (EARLY_DISPLAY_TARGET = LoadingScreenRenderer), unchanged; only so that 1.21.10 fix does not reach 1.21.11 (and 26.x, which inherit it) unported and ungated.
// MV-STAND-IN: 1.21.10's prepared copy of the authors' RuntimeExtensionBridge, unchanged, as it was before the 1.21.8 overlay for
// MV-STAND-IN: FML 9's LoadingScreenRenderer, unchanged; only so that 1.21.8 fix does not reach 1.21.10 (FML 10, its own GUI code) unported and ungated.
package net.eca.coremod;

import net.eca.coremod.EarlyLogWriter;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

/**
 * Optional runtime-extension bridge. Platform artifacts contain only this inert boundary.
 */
public final class RuntimeExtensionBridge {
    public static final String EARLY_DISPLAY_TARGET = "net/neoforged/fml/earlydisplay/DisplayWindow";
    private static final String PROVIDER = "net.eca.pro.EcaProRuntime";
    private static final String EARLY_DISPLAY_TRANSFORMER = "net.eca.coremod.LoadingScreenTransformer";
    private static volatile Class<?> provider;
    private static volatile Class<?> earlyDisplayTransformer;
    private static volatile boolean resolved;
    private static volatile boolean earlyDisplayResolved;
    private static volatile boolean earlyPrepared;

    private RuntimeExtensionBridge() {
    }

    public static synchronized void prepareEarly() {
        if (earlyPrepared) return;
        earlyPrepared = true;
        EarlyLogWriter.resetForNewSession();
        invoke("prepareEarly");
    }

    public static void afterAgentReady() {
        invoke("afterAgentReady");
    }

    public static void refreshInterception() {
        invoke("refreshInterception");
    }

    public static void onLoadComplete() {
        invoke("onLoadComplete");
    }

    public static boolean hasEarlyDisplayTransformer() {
        return resolveEarlyDisplayTransformer() != null;
    }

    public static void installEarlyDisplayTransformer(Instrumentation instrumentation) {
        Class<?> type = resolveEarlyDisplayTransformer();
        if (type == null || instrumentation == null) return;
        try {
            Object instance = type.getDeclaredConstructor().newInstance();
            if (!(instance instanceof ClassFileTransformer transformer)) return;
            instrumentation.addTransformer(transformer, true);
            for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
                if (!EARLY_DISPLAY_TARGET.replace('/', '.').equals(loaded.getName())) continue;
                instrumentation.retransformClasses(loaded);
                break;
            }
            EarlyLogWriter.info("[RuntimeExtensionBridge] Early display transformer registered");
        } catch (Throwable t) {
            EarlyLogWriter.info("[RuntimeExtensionBridge] Early display transformer failed: " + rootMessage(t));
        }
    }

    public static byte[] transformEarlyDisplay(byte[] classBytes) {
        Class<?> type = resolveEarlyDisplayTransformer();
        if (type == null) return null;
        try {
            Method method = type.getMethod("transform", byte[].class);
            Object result = method.invoke(null, (Object) classBytes);
            return result instanceof byte[] transformed ? transformed : null;
        } catch (Throwable t) {
            EarlyLogWriter.info("[RuntimeExtensionBridge] Early display transformation failed: " + rootMessage(t));
            return null;
        }
    }

    private static Object invoke(String methodName) {
        return invoke(methodName, new Class<?>[0]);
    }

    private static Object invoke(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        Class<?> type = resolveProvider();
        if (type == null) return null;
        try {
            Method method = type.getMethod(methodName, parameterTypes);
            return method.invoke(null, arguments);
        } catch (Throwable t) {
            EarlyLogWriter.info("[RuntimeExtensionBridge] " + methodName + " failed: " + rootMessage(t));
            return null;
        }
    }

    private static Class<?> resolveProvider() {
        if (resolved) return provider;
        synchronized (RuntimeExtensionBridge.class) {
            if (resolved) return provider;
            try {
                provider = Class.forName(PROVIDER, true, RuntimeExtensionBridge.class.getClassLoader());
            } catch (ClassNotFoundException ignored) {
                provider = null;
            } catch (Throwable t) {
                EarlyLogWriter.info("[RuntimeExtensionBridge] Provider initialization failed: " + rootMessage(t));
            }
            resolved = true;
            return provider;
        }
    }

    private static Class<?> resolveEarlyDisplayTransformer() {
        if (earlyDisplayResolved) return earlyDisplayTransformer;
        synchronized (RuntimeExtensionBridge.class) {
            if (earlyDisplayResolved) return earlyDisplayTransformer;
            try {
                earlyDisplayTransformer = Class.forName(EARLY_DISPLAY_TRANSFORMER, true,
                        RuntimeExtensionBridge.class.getClassLoader());
            } catch (ClassNotFoundException ignored) {
                earlyDisplayTransformer = null;
            } catch (Throwable t) {
                EarlyLogWriter.info("[RuntimeExtensionBridge] Early display provider failed: " + rootMessage(t));
            }
            earlyDisplayResolved = true;
            return earlyDisplayTransformer;
        }
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}

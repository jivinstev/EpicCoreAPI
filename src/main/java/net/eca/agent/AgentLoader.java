// 1.21.10 overlay: the author's AgentLoader, except that a failed attach logs its whole cause chain (the original
// logs only "InvocationTargetException: null"), and enableSelfAttach writes ALLOW_ATTACH_SELF as the primitive
// boolean it is. The author's putObject(Boolean.TRUE) stored the low byte of Boolean.TRUE's compressed pointer
// into that one-byte field, so self-attach failed ("Can not attach to current VM") whenever that byte was 0x00,
// which depends on the JVM's command line: the intermittent shipped-jar failure.
package net.eca.agent;

import sun.misc.Unsafe;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Loads ECA's bundled agent into the current game JVM to support defensive
 * entity-state protection and compatibility transformations.
 * The attach target is the current process; the agent JAR comes from a bundled resource.
 * Called during coremod initialization before runtime transformers are registered.
 */
public final class AgentLoader {

    private static final String AGENT_RESOURCE_PATH = "/net/eca/agent/agent.jar";
    private static volatile Path loadedAgentPath;

    // Adjust the local JVM's self-attach gate so ECA can obtain Instrumentation after startup.
    public static void enableSelfAttach() {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);

            Class<?> vmClass = Class.forName("sun.tools.attach.HotSpotVirtualMachine");
            Field allowField = vmClass.getDeclaredField("ALLOW_ATTACH_SELF");

            Object base = unsafe.staticFieldBase(allowField);
            long offset = unsafe.staticFieldOffset(allowField);
            // ALLOW_ATTACH_SELF is a primitive boolean: putObject(Boolean.TRUE) stored the low byte of Boolean.TRUE's
            // compressed pointer, which is 0x00 on some JVM command lines (self-attach then fails). ECA-042.
            unsafe.putBoolean(base, offset, true);
            if (!unsafe.getBoolean(base, offset)) {
                throw new IllegalStateException("ALLOW_ATTACH_SELF did not take");
            }

            AgentLogWriter.info("[AgentLoader] Self-attach enabled via Unsafe");
        } catch (Throwable t) {
            // Fall back to the self-attach property if direct access is unavailable.
            System.setProperty("jdk.attach.allowAttachSelf", "true");
            AgentLogWriter.info("[AgentLoader] Self-attach enabled via system property (fallback)");
        }
    }

    // The Attach API needs a JAR path, so materialize the bundled agent in a temporary directory.
    public static boolean loadAgent() {
        if (EcaAgent.getInstrumentation() != null) {
            return true;
        }

        try {
            Path tmpDir = Files.createTempDirectory("eca-agent");
            Path agentJar = tmpDir.resolve("agent.jar");

            try (InputStream in = AgentLoader.class.getResourceAsStream(AGENT_RESOURCE_PATH)) {
                if (in == null) {
                    throw new FileNotFoundException("Agent resource not found: " + AGENT_RESOURCE_PATH);
                }
                Files.copy(in, agentJar, REPLACE_EXISTING);
            }
            loadedAgentPath = agentJar.toAbsolutePath().normalize();
            agentJar.toFile().deleteOnExit();
            tmpDir.toFile().deleteOnExit();

            // Attachment is restricted here to the JVM running this loader.
            String pid = String.valueOf(ProcessHandle.current().pid());
            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            Object vm = vmClass.getMethod("attach", String.class).invoke(null, pid);

            try {
                // Identify ECA's caller loader so agentmain can bridge the Instrumentation handle.
                String callerClassName = AgentLoader.class.getName();
                vmClass.getMethod("loadAgent", String.class, String.class)
                    .invoke(vm, agentJar.toAbsolutePath().toString(), callerClassName);
            } finally {
                vmClass.getMethod("detach").invoke(vm);
            }

            boolean success = EcaAgent.getInstrumentation() != null;
            AgentLogWriter.info("[AgentLoader] Agent load " + (success ? "succeeded" : "FAILED"));
            return success;

        } catch (Throwable t) {
            AgentLogWriter.error("[AgentLoader] Failed to load agent", t);
            for (Throwable c = t; c != null; c = c.getCause()) {
                AgentLogWriter.info("[AgentLoader] cause: " + c);
            }
            return false;
        }
    }

    public static Path getLoadedAgentPath() {
        return loadedAgentPath;
    }

    private AgentLoader() {}
}

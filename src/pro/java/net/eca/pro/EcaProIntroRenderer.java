package net.eca.pro;

import net.eca.agent.AgentLogWriter;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL32C.*;
import static org.lwjgl.opengl.GL32C.GL_BLEND;
import static org.lwjgl.opengl.GL32C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL32C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL32C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL32C.GL_LINEAR;
import static org.lwjgl.opengl.GL32C.GL_MAX_TEXTURE_SIZE;
import static org.lwjgl.opengl.GL32C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL32C.GL_RGBA;
import static org.lwjgl.opengl.GL32C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL32C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL32C.glActiveTexture;
import static org.lwjgl.opengl.GL32C.glBindTexture;
import static org.lwjgl.opengl.GL32C.glBlendFunc;
import static org.lwjgl.opengl.GL32C.glClear;
import static org.lwjgl.opengl.GL32C.glClearColor;
import static org.lwjgl.opengl.GL32C.glDeleteTextures;
import static org.lwjgl.opengl.GL32C.glEnable;
import static org.lwjgl.opengl.GL32C.glGenTextures;
import static org.lwjgl.opengl.GL32C.glGetInteger;
import static org.lwjgl.opengl.GL32C.glTexImage2D;
import static org.lwjgl.opengl.GL32C.glTexParameteri;

final class EcaProIntroRenderer {
    private static final String RESOURCE_ROOT = "/assets/eca/pro_intro/atlas_";
    private static final int ATLAS_COUNT = 3;
    private static final int ATLAS_COLUMNS = 4;
    private static final int ATLAS_ROWS = 4;
    private static final int FRAME_WIDTH = 854;
    private static final int FRAME_HEIGHT = 480;
    private static final int FRAME_COUNT = 48;
    private static final int FRAMES_PER_SECOND = 20;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private static byte[][] encodedAtlases;
    private static int[] textures;
    private static int program;
    private static int vao;
    private static int vbo;
    private static int samplerLocation;
    private static final String VERTEX_SOURCE = "#version 150 core\n"
            + "in vec2 Position;\n"
            + "in vec2 UV0;\n"
            + "out vec2 texCoord0;\n"
            + "void main() {\n"
            + "    gl_Position = vec4(Position, 0.0, 1.0);\n"
            + "    texCoord0 = UV0;\n"
            + "}\n";
    private static final String FRAGMENT_SOURCE = "#version 150 core\n"
            + "uniform sampler2D Sampler0;\n"
            + "in vec2 texCoord0;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    fragColor = texture(Sampler0, texCoord0);\n"
            + "}\n";
    private static long startNanos;
    private static boolean finished;
    private static boolean failed;

    private EcaProIntroRenderer() {
    }

    static synchronized boolean prepareResources() {
        if (failed) return false;
        if (encodedAtlases != null || textures != null) return true;
        try {
            byte[][] prepared = new byte[ATLAS_COUNT][];
            for (int index = 0; index < ATLAS_COUNT; index++) {
                String resource = RESOURCE_ROOT + index + ".png";
                try (InputStream input = EcaProIntroRenderer.class.getResourceAsStream(resource)) {
                    if (input == null) throw new IllegalStateException("intro_resource_missing");
                    prepared[index] = input.readAllBytes();
                }
            }
            encodedAtlases = prepared;
            AgentLogWriter.info("[EcaProIntro] Animation resources prepared");
            return true;
        } catch (Throwable t) {
            failed = true;
            encodedAtlases = null;
            AgentLogWriter.info("[EcaProIntro] Resource preparation failed: " + t.getMessage());
            return false;
        }
    }

    static boolean render(Object displayContext) {
        if (finished || failed) {
            return false;
        }
        try {
            if (textures == null) initialize();
            if (startNanos == 0L) startNanos = System.nanoTime();

            long elapsed = Math.max(0L, System.nanoTime() - startNanos);
            int frame = (int) (elapsed * FRAMES_PER_SECOND / NANOS_PER_SECOND);
            if (frame >= FRAME_COUNT) {
                release();
                finished = true;
                return false;
            }

            draw(frame);
            return true;
        } catch (Throwable t) {
            failed = true;
            releaseQuietly();
            AgentLogWriter.info("[EcaProIntro] Rendering disabled after failure: " + t.getMessage());
            return false;
        }
    }

    private static void initialize() throws Exception {
        if (encodedAtlases == null && !prepareResources()) {
            throw new IllegalStateException("intro_resources_unavailable");
        }
        int atlasWidth = FRAME_WIDTH * ATLAS_COLUMNS;
        int atlasHeight = FRAME_HEIGHT * ATLAS_ROWS;
        int maximumTextureSize = glGetInteger(GL_MAX_TEXTURE_SIZE);
        if (atlasWidth > maximumTextureSize || atlasHeight > maximumTextureSize) {
            throw new IllegalStateException("texture_size_unsupported");
        }

        textures = new int[ATLAS_COUNT];
        for (int index = 0; index < ATLAS_COUNT; index++) {
            textures[index] = loadTexture(encodedAtlases[index], atlasWidth, atlasHeight);
        }
        encodedAtlases = null;
        initializeGeometry();
        AgentLogWriter.info("[EcaProIntro] Animation textures initialized");
    }

    private static int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("shader_compile_failed: " + log);
        }
        return shader;
    }

    private static void initializeGeometry() {
        int vertex = compileShader(GL_VERTEX_SHADER, VERTEX_SOURCE);
        int fragment = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SOURCE);
        program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glBindAttribLocation(program, 0, "Position");
        glBindAttribLocation(program, 1, "UV0");
        glLinkProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            program = 0;
            throw new IllegalStateException("shader_link_failed: " + log);
        }
        samplerLocation = glGetUniformLocation(program, "Sampler0");

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 16 * Float.BYTES, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private static int loadTexture(byte[] encoded, int expectedWidth, int expectedHeight) {
        ByteBuffer source = BufferUtils.createByteBuffer(encoded.length);
        source.put(encoded).flip();
        int[] width = new int[1];
        int[] height = new int[1];
        int[] channels = new int[1];
        ByteBuffer pixels = STBImage.stbi_load_from_memory(source, width, height, channels, 4);
        if (pixels == null) throw new IllegalStateException(STBImage.stbi_failure_reason());
        try {
            if (width[0] != expectedWidth || height[0] != expectedHeight) {
                throw new IllegalStateException("intro_resource_dimensions");
            }
            int texture = glGenTextures();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width[0], height[0], 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            return texture;
        } finally {
            STBImage.stbi_image_free(pixels);
        }
    }

    private static void draw(int frame) {
        int framesPerAtlas = ATLAS_COLUMNS * ATLAS_ROWS;
        int atlas = frame / framesPerAtlas;
        int cell = frame % framesPerAtlas;
        int column = cell % ATLAS_COLUMNS;
        int row = cell / ATLAS_COLUMNS;
        float atlasWidth = FRAME_WIDTH * ATLAS_COLUMNS;
        float atlasHeight = FRAME_HEIGHT * ATLAS_ROWS;
        float u0 = (column * FRAME_WIDTH + 0.5f) / atlasWidth;
        float u1 = ((column + 1) * FRAME_WIDTH - 0.5f) / atlasWidth;
        float vTop = (row * FRAME_HEIGHT + 0.5f) / atlasHeight;
        float vBottom = ((row + 1) * FRAME_HEIGHT - 0.5f) / atlasHeight;

        glClearColor(3f / 255f, 3f / 255f, 5f / 255f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textures[atlas]);
        float[] vertices = {
                -1f, 1f, u0, vTop,
                -1f, -1f, u0, vBottom,
                1f, -1f, u1, vBottom,
                1f, 1f, u1, vTop
        };
        glUseProgram(program);
        glUniform1i(samplerLocation, 0);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_DYNAMIC_DRAW);
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        glUseProgram(0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private static void release() {
        if (textures != null) {
            for (int texture : textures) {
                if (texture != 0) glDeleteTextures(texture);
            }
            textures = null;
        }
        if (vbo != 0) {
            glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (vao != 0) {
            glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (program != 0) {
            glDeleteProgram(program);
            program = 0;
        }
    }

    private static void releaseQuietly() {
        try {
            release();
        } catch (Throwable ignored) {
        }
    }
}

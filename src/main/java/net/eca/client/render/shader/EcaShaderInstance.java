package net.eca.client.render.shader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.eca.client.render.ShaderMaskSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

// Oculus兼容：覆写apply()，在Oculus锁定DepthColor后立刻解锁，使ECA着色器在光影模式下可见
public class EcaShaderInstance {

    private static final MethodHandle UNLOCK_DEPTH_COLOR;
    private static final MethodHandle RESTORE_BLEND;
    private static final MethodHandle IS_DEPTH_COLOR_LOCKED;
    private static final MethodHandle GET_IRIS_API;
    private static final MethodHandle IS_SHADER_PACK_IN_USE;

    static {
        MethodHandle unlockHandle = null;
        MethodHandle restoreBlendHandle = null;
        MethodHandle isLockedHandle = null;
        MethodHandle getApiHandle = null;
        MethodHandle isInUseHandle = null;
        try {
            Class<?> depthColorStorage = Class.forName("net.irisshaders.iris.gl.blending.DepthColorStorage");
            unlockHandle = MethodHandles.publicLookup().findStatic(
                depthColorStorage, "unlockDepthColor", MethodType.methodType(void.class)
            );
            isLockedHandle = MethodHandles.publicLookup().findStatic(
                depthColorStorage, "isDepthColorLocked", MethodType.methodType(boolean.class)
            );

            Class<?> blendModeStorage = Class.forName("net.irisshaders.iris.gl.blending.BlendModeStorage");
            restoreBlendHandle = MethodHandles.publicLookup().findStatic(
                blendModeStorage, "restoreBlend", MethodType.methodType(void.class)
            );

            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            getApiHandle = MethodHandles.publicLookup().findStatic(
                irisApiClass, "getInstance", MethodType.methodType(irisApiClass)
            );
            isInUseHandle = MethodHandles.publicLookup().findVirtual(
                irisApiClass, "isShaderPackInUse", MethodType.methodType(boolean.class)
            );
        } catch (ClassNotFoundException ignored) {
            // Oculus未安装
        } catch (Exception ignored) {
        }
        UNLOCK_DEPTH_COLOR = unlockHandle;
        RESTORE_BLEND = restoreBlendHandle;
        IS_DEPTH_COLOR_LOCKED = isLockedHandle;
        GET_IRIS_API = getApiHandle;
        IS_SHADER_PACK_IN_USE = isInUseHandle;
    }

    // ==================== ColorKey 共享状态 ====================

    private static float colorKeyR;
    private static float colorKeyG;
    private static float colorKeyB;
    private static float colorKeyEnabled;
    private static float colorKeyTolerance = 0.1f;

    public static void setColorKey(float r, float g, float b, float tolerance) {
        colorKeyR = r;
        colorKeyG = g;
        colorKeyB = b;
        colorKeyEnabled = 1.0f;
        colorKeyTolerance = tolerance;
    }

    public static void clearColorKey() {
        colorKeyR = 0.0f;
        colorKeyG = 0.0f;
        colorKeyB = 0.0f;
        colorKeyEnabled = 0.0f;
        colorKeyTolerance = 0.1f;
    }

    public static void applyColorKeyUniforms(Uniform colorKeyColorUniform, Uniform colorKeyToleranceUniform) {
        try {
            if (colorKeyColorUniform != null) {
                colorKeyColorUniform.set(colorKeyR, colorKeyG, colorKeyB, colorKeyEnabled);
            }
            if (colorKeyToleranceUniform != null) {
                colorKeyToleranceUniform.set(colorKeyTolerance);
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== LocalUvBounds 共享状态 ====================
    // 用于把图集 UV (texCoord0) 归一化回本地 [0,1] 空间，使 shader 的中心化数学
    // 对物品/子贴图也能正确工作。默认 (0,0)+(1,1) 即恒等映射，完全向后兼容。

    private static float localUvMinU = 0.0f;
    private static float localUvMinV = 0.0f;
    private static float localUvScaleU = 1.0f;
    private static float localUvScaleV = 1.0f;

    public static void setLocalUvBounds(float uMin, float vMin, float uScale, float vScale) {
        localUvMinU = uMin;
        localUvMinV = vMin;
        localUvScaleU = uScale;
        localUvScaleV = vScale;
    }

    public static void clearLocalUvBounds() {
        localUvMinU = 0.0f;
        localUvMinV = 0.0f;
        localUvScaleU = 1.0f;
        localUvScaleV = 1.0f;
    }

    // ==================== Opacity 共享状态 ====================
    // 设置全局着色器不透明度，覆写 COLOR_MODULATOR.a。1.0 为完全不透明，0.0 为完全透明。
    // 每一帧每个着色器实例调用 apply() 时取当前值。该值不会跨帧保留。

    private static float ecaOpacity = 1.0f;

    private static ShaderMaskSource shaderMaskSource = ShaderMaskSource.NONE;
    private static Identifier shaderMaskTexture;
    private static int shaderMaskColor;
    private static float shaderMaskTolerance = 0.05f;

    private final Uniform maskColorUniform;
    private final Uniform maskToleranceUniform;
    private final Uniform colorModulatorUniform;
    private boolean maskFromBaseTexture;

    public static void setOpacity(float opacity) {
        ecaOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
    }

    public static void clearOpacity() {
        ecaOpacity = 1.0f;
    }

    public static void setShaderMask(ShaderMaskSource source, Identifier texture,
                                     int color, float tolerance) {
        shaderMaskSource = source == null ? ShaderMaskSource.NONE : source;
        shaderMaskTexture = texture;
        shaderMaskColor = color & 0xFFFFFF;
        shaderMaskTolerance = Math.max(0.0f, tolerance);
    }

    public static void clearShaderMask() {
        shaderMaskSource = ShaderMaskSource.NONE;
        shaderMaskTexture = null;
        shaderMaskColor = 0;
        shaderMaskTolerance = 0.05f;
    }

    @Deprecated
    public static void setEntityMask(Identifier texture, int color, float tolerance) {
        setShaderMask(texture == null ? ShaderMaskSource.NONE : ShaderMaskSource.TEXTURE,
            texture, color, tolerance);
    }

    @Deprecated
    public static void clearEntityMask() {
        clearShaderMask();
    }

    public static void applyLocalUvBoundsUniforms(Uniform localUvMinUniform, Uniform localUvScaleUniform) {
        try {
            if (localUvMinUniform != null) {
                localUvMinUniform.set(localUvMinU, localUvMinV);
            }
            if (localUvScaleUniform != null) {
                localUvScaleUniform.set(localUvScaleU, localUvScaleV);
            }
        } catch (Exception ignored) {
        }
    }

    // 检测Oculus光影是否激活（光影包已启用且正在使用）
    public static boolean isOculusShadersActive() {
        if (GET_IRIS_API == null || IS_SHADER_PACK_IN_USE == null) {
            return false;
        }
        try {
            Object api = GET_IRIS_API.invoke();
            return (boolean) IS_SHADER_PACK_IN_USE.invoke(api);
        } catch (Throwable e) {
            return false;
        }
    }

    // ==================== 26.x 着色器程序 ====================
    // 26.x 没有逐个设置的 uniform：非采样器 uniform 都在 std140 块 EcaUniforms 中，由 PreparedRenderTypeMixin
    // 每次绘制时写入并绑定；GLSL 150 源码由 EcaShaderSources 在加载时升级为该布局。
    // ModelViewMat / ProjMat / GameTime / ScreenSize / GlintAlpha 由原版块提供（1.21 中原版同样在绘制时覆写它们）。

    public static final String BLOCK = "EcaUniforms";
    static final List<String> VANILLA_UNIFORMS = List.of("ModelViewMat", "ProjMat", "GameTime", "ScreenSize", "GlintAlpha");
    //所有 ECA 程序共用的成员顺序；程序 JSON 中额外的 uniform 依次追加在后
    static final List<String> STANDARD = List.of("ColorModulator", "CameraYaw", "CameraPitch", "MaskColor", "MaskTolerance",
        "ColorKeyColor", "ColorKeyTolerance", "LocalUvMin", "LocalUvScale", "Glow");
    private static final Map<String, Integer> STANDARD_COUNTS = Map.of("ColorModulator", 4, "CameraYaw", 1, "CameraPitch", 1,
        "MaskColor", 4, "MaskTolerance", 1, "ColorKeyColor", 4, "ColorKeyTolerance", 1, "LocalUvMin", 2, "LocalUvScale", 2, "Glow", 1);

    private static final Map<Identifier, EcaShaderInstance> CURRENT = new ConcurrentHashMap<>();
    private static final Map<RenderPipeline, Identifier> PIPELINE_PROGRAM = new ConcurrentHashMap<>();
    private static final Map<RenderPipeline, Runnable> PIPELINE_SETUP = new ConcurrentHashMap<>();
    private static final Map<String, RenderPipeline> PIPELINES = new ConcurrentHashMap<>();
    private static final Map<Integer, DynamicUniformStorage<Values>> STORAGE = new HashMap<>();

    private final Identifier location;
    private final ResourceProvider resourceProvider;
    private final Identifier vertexShader;
    private final Identifier fragmentShader;
    private final List<String> samplers = new ArrayList<>();
    private final Map<String, Uniform> uniforms = new LinkedHashMap<>();
    private final Map<String, Object> boundSamplers = new HashMap<>();
    private final int blockSize;

    public EcaShaderInstance(ResourceProvider resourceProvider, Identifier location, VertexFormat format) throws IOException {
        this.location = location;
        this.resourceProvider = resourceProvider;
        Identifier json = location.withPath("shaders/core/" + location.getPath() + ".json");
        JsonObject program;
        try (Reader reader = resourceProvider.getResourceOrThrow(json).openAsReader()) {
            program = JsonParser.parseReader(reader).getAsJsonObject();
        }
        this.vertexShader = coreId(program.get("vertex").getAsString());
        this.fragmentShader = coreId(program.get("fragment").getAsString());
        for (JsonElement sampler : program.getAsJsonArray("samplers")) {
            this.samplers.add(sampler.getAsJsonObject().get("name").getAsString());
        }
        Map<String, JsonObject> declared = new LinkedHashMap<>();
        for (JsonElement uniform : program.getAsJsonArray("uniforms")) {
            JsonObject u = uniform.getAsJsonObject();
            declared.put(u.get("name").getAsString(), u);
        }
        List<String> order = new ArrayList<>(STANDARD);
        for (String name : declared.keySet()) {
            if (!order.contains(name) && !VANILLA_UNIFORMS.contains(name)) {
                order.add(name);
            }
        }
        Std140SizeCalculator size = new Std140SizeCalculator();
        for (String name : order) {
            JsonObject u = declared.get(name);
            int count = u == null ? STANDARD_COUNTS.get(name) : (u.has("count") ? u.get("count").getAsInt() : 1);
            float[] values = new float[count];
            if (u != null && u.has("values")) {
                JsonArray v = u.getAsJsonArray("values");
                for (int i = 0; i < count && i < v.size(); i++) {
                    values[i] = v.get(i).getAsFloat();
                }
            } else if ("ColorModulator".equals(name)) {
                java.util.Arrays.fill(values, 1.0f);
            }
            Uniform.add(size, count);
            this.uniforms.put(name, new Uniform(name, values));
        }
        this.blockSize = size.get();
        this.maskColorUniform = getUniform("MaskColor");
        this.maskToleranceUniform = getUniform("MaskTolerance");
        this.colorModulatorUniform = getUniform("ColorModulator");
        CURRENT.put(location, this);
    }

    //JSON 中 "eca:ocean" 指 shaders/core/ocean.*，26.x 的着色器 id 带 core/ 前缀
    private static Identifier coreId(String ref) {
        Identifier id = Identifier.parse(ref);
        return id.withPath("core/" + id.getPath());
    }

    public static EcaShaderInstance create(ResourceProvider resourceProvider, Identifier location, VertexFormat format) throws IOException {
        return new EcaShaderInstance(resourceProvider, location, format);
    }

    public Uniform getUniform(String name) {
        return uniforms.get(name);
    }

    public Uniform safeGetUniform(String name) {
        Uniform uniform = uniforms.get(name);
        return uniform != null ? uniform : new Uniform(name, new float[16]);
    }

    //接受 AbstractTexture 或 GpuTextureView；绘制时由 bind() 绑定
    public void setSampler(String name, Object texture) {
        boundSamplers.put(name, texture);
    }

    public void close() {
        CURRENT.remove(location, this);
    }

    public Identifier getLocation() {
        return location;
    }

    Identifier vertexShader() {
        return vertexShader;
    }

    Identifier fragmentShader() {
        return fragmentShader;
    }

    ResourceProvider resourceProvider() {
        return resourceProvider;
    }

    List<Uniform> blockMembers() {
        return List.copyOf(uniforms.values());
    }

    static Iterable<EcaShaderInstance> current() {
        return CURRENT.values();
    }

    //同名管线只建一次；资源重载换掉实例后管线仍指向同一程序 id，绘制时取当前实例
    public RenderPipeline pipeline(String name, UnaryOperator<RenderPipeline.Builder> state) {
        return PIPELINES.computeIfAbsent(location + "/" + name, key -> {
            BindGroupLayout.Builder layout = BindGroupLayout.builder().withUniform(BLOCK, UniformType.UNIFORM_BUFFER);
            samplers.forEach(layout::withSampler);
            RenderPipeline pipeline = state.apply(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                .withLocation(location.withPath("pipeline/" + location.getPath() + "/" + name))
                .withVertexShader(vertexShader)
                .withFragmentShader(fragmentShader)
                .withBindGroupLayout(layout.build())).build();
            PIPELINE_PROGRAM.put(pipeline, location);
            return pipeline;
        });
    }

    public static State state(Supplier<? extends EcaShaderInstance> shader, Runnable setup) {
        return new State(shader, setup);
    }

    //1.21 的 ShaderStateShard：着色器，加上 setupRenderState 中设置 uniform 的回调
    public static final class State {
        private final Supplier<? extends EcaShaderInstance> shader;
        private final Runnable setup;

        private State(Supplier<? extends EcaShaderInstance> shader, Runnable setup) {
            this.shader = shader;
            this.setup = setup;
        }

        public RenderPipeline pipeline(String name, UnaryOperator<RenderPipeline.Builder> state) {
            EcaShaderInstance instance = shader.get();
            if (instance == null) {
                throw new IllegalStateException("ECA shader for render type " + name + " is not registered yet");
            }
            RenderPipeline pipeline = instance.pipeline(name, state);
            if (setup != null) {
                PIPELINE_SETUP.put(pipeline, setup);
            }
            return pipeline;
        }
    }

    /* 26.x 在记录几何体时调用 RenderType.prepare()，真正绘制在稍后统一提交。1.21 中 uniform 在提交前设置，
       ECA 的抠像 / UV / 遮罩等状态也是在每次渲染调用前后设置与清除的，所以这里在 prepare() 时取快照
       （RenderTypePrepareMixin），绘制时（PreparedRenderTypeMixin）只绑定快照。 */
    private static final Map<PreparedRenderType, Binding> PREPARED = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    private record Binding(GpuBufferSlice uniforms, List<Sampler> samplers) {}

    private record Sampler(String name, GpuTextureView view, GpuSampler sampler) {}

    public static void onPrepare(RenderPipeline pipeline, PreparedRenderType prepared) {
        Identifier program = PIPELINE_PROGRAM.get(pipeline);
        EcaShaderInstance instance = program == null ? null : CURRENT.get(program);
        if (instance == null) {
            return;
        }
        Runnable setup = PIPELINE_SETUP.get(pipeline);
        if (setup != null) {
            setup.run();
        }
        instance.apply();
        PREPARED.put(prepared, instance.snapshot(prepared.textures()));
    }

    public static void bindPrepared(PreparedRenderType prepared, RenderPass pass) {
        Binding binding = PREPARED.get(prepared);
        if (binding == null) {
            return;
        }
        pass.setUniform(BLOCK, binding.uniforms());
        for (Sampler sampler : binding.samplers()) {
            pass.bindTexture(sampler.name(), sampler.view(), sampler.sampler());
        }
    }

    //每帧开始：上一帧的快照与环形缓冲均已用完
    public static void endFrame() {
        PREPARED.clear();
        STORAGE.values().forEach(DynamicUniformStorage::endFrame);
    }

    private Binding snapshot(List<PreparedRenderType.Texture> textures) {
        DynamicUniformStorage<Values> storage = STORAGE.computeIfAbsent(blockSize,
            size -> new DynamicUniformStorage<>("ECA uniforms", size, 4));
        GpuBufferSlice slice = storage.writeUniform(Values.of(uniforms.values()));
        PreparedRenderType.Texture base = null;
        for (PreparedRenderType.Texture texture : textures) {
            if ("Sampler0".equals(texture.name())) {
                base = texture;
            }
        }
        List<Sampler> extra = new ArrayList<>();
        for (String sampler : samplers) {
            if (textures.stream().anyMatch(texture -> texture.name().equals(sampler))) {
                continue;
            }
            Object bound = "MaskSampler".equals(sampler) && maskFromBaseTexture ? null : boundSamplers.get(sampler);
            GpuTextureView view = bound instanceof AbstractTexture texture ? texture.getTextureView()
                : bound instanceof GpuTextureView textureView ? textureView : null;
            GpuSampler gpuSampler = bound instanceof AbstractTexture texture ? texture.getSampler()
                : RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            //未设置的采样器（如遮罩为 BASE_TEXTURE 或未启用）沿用 Sampler0，避免缺失采样器
            if (view == null && base != null) {
                view = base.textureView();
                gpuSampler = base.sampler();
            }
            if (view != null) {
                extra.add(new Sampler(sampler, view, gpuSampler));
            }
        }
        return new Binding(slice, extra);
    }

    public void apply() {
        applyShaderMask();
        // 覆写 COLOR_MODULATOR.a 为 ECA 不透明度：26.x 已无着色器颜色，基准为白色。
        if (colorModulatorUniform != null) {
            colorModulatorUniform.set(1.0f, 1.0f, 1.0f, ecaOpacity < 1.0f ? ecaOpacity : 1.0f);
        }
        // Oculus的MixinShaderInstance会在super.apply()的TAIL锁定DepthColor，
        // 导致非ExtendedShader/FallbackShader的着色器无法写入颜色和深度。
        // 在此立刻解锁，使ECA着色器正常渲染。
        if (UNLOCK_DEPTH_COLOR != null && IS_DEPTH_COLOR_LOCKED != null) {
            try {
                boolean wasLocked = (boolean) IS_DEPTH_COLOR_LOCKED.invokeExact();
                if (wasLocked) {
                    UNLOCK_DEPTH_COLOR.invokeExact();

                    // 额外保险：直接使用LWJGL GL调用绕过GlStateManager拦截
                    // 当光影开启时，GlStateManager的_depthMask和_colorMask会被
                    // MixinGlStateManager_DepthColorOverride拦截。即使调用unlockDepthColor()
                    // 内部也是通过GlStateManager，可能再次被拦截。直接GL调用可以彻底绕过。
                    GL11.glDepthMask(true);
                    GL11.glColorMask(true, true, true, true);
                }

                // 恢复混合状态（如果被锁定）
                if (RESTORE_BLEND != null) {
                    RESTORE_BLEND.invokeExact();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void applyShaderMask() {
        ShaderMaskSource source = shaderMaskSource;
        if (maskColorUniform != null) {
            if (source == ShaderMaskSource.NONE) {
                maskColorUniform.set(0.0f, 0.0f, 0.0f, 0.0f);
            } else {
                float red = (shaderMaskColor >> 16 & 0xFF) / 255.0f;
                float green = (shaderMaskColor >> 8 & 0xFF) / 255.0f;
                float blue = (shaderMaskColor & 0xFF) / 255.0f;
                maskColorUniform.set(red, green, blue, 1.0f);
            }
        }
        if (maskToleranceUniform != null) {
            maskToleranceUniform.set(shaderMaskTolerance);
        }
        maskFromBaseTexture = source == ShaderMaskSource.BASE_TEXTURE;
        if (source == ShaderMaskSource.TEXTURE && shaderMaskTexture != null) {
            AbstractTexture maskTexture = Minecraft.getInstance().getTextureManager().getTexture(shaderMaskTexture);
            setSampler("MaskSampler", maskTexture);
        }
    }

    //uniform 值：按 std140 写入 EcaUniforms 块
    public static final class Uniform {
        private final String name;
        private final float[] values;

        Uniform(String name, float[] values) {
            this.name = name;
            this.values = values;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return values.length;
        }

        public void set(float x) {
            values[0] = x;
        }

        public void set(float x, float y) {
            values[0] = x;
            values[1] = y;
        }

        public void set(float x, float y, float z) {
            values[0] = x;
            values[1] = y;
            values[2] = z;
        }

        public void set(float x, float y, float z, float w) {
            values[0] = x;
            values[1] = y;
            values[2] = z;
            values[3] = w;
        }

        public void set(float[] v) {
            System.arraycopy(v, 0, values, 0, Math.min(v.length, values.length));
        }

        public void set(Matrix4f matrix) {
            matrix.get(values);
        }

        static void add(Std140SizeCalculator size, int count) {
            switch (count) {
                case 1 -> size.putFloat();
                case 2 -> size.putVec2();
                case 3 -> size.putVec3();
                case 4 -> size.putVec4();
                case 16 -> size.putMat4f();
                default -> throw new IllegalArgumentException("unsupported uniform size " + count);
            }
        }

        static void write(Std140Builder builder, float[] values) {
            switch (values.length) {
                case 1 -> builder.putFloat(values[0]);
                case 2 -> builder.putVec2(values[0], values[1]);
                case 3 -> builder.putVec3(values[0], values[1], values[2]);
                case 4 -> builder.putVec4(values[0], values[1], values[2], values[3]);
                default -> builder.putMat4f(new Matrix4f().set(values));
            }
        }

        String glslType() {
            return switch (values.length) {
                case 1 -> "float";
                case 2 -> "vec2";
                case 3 -> "vec3";
                case 4 -> "vec4";
                default -> "mat4";
            };
        }
    }

    //绘制时的取值快照：数组按引用比较，故 DynamicUniformStorage 不会把改过值的绘制误判为重复
    private record Values(float[][] snapshot) implements DynamicUniformStorage.DynamicUniform {
        static Values of(Iterable<Uniform> members) {
            List<float[]> copy = new ArrayList<>();
            for (Uniform member : members) {
                copy.add(member.values.clone());
            }
            return new Values(copy.toArray(new float[0][]));
        }

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder builder = Std140Builder.intoBuffer(buffer);
            for (float[] v : snapshot) {
                Uniform.write(builder, v);
            }
        }
    }
}

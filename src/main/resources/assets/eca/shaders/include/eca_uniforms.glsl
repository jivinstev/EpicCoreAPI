#version 330

// Generated from this mod's core/*.json programs; the Java side reads the member order below.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 McColorModulator;
    vec3 McModelOffset;
    mat4 McTextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(std140) uniform EcaUniforms {
    vec4 ColorModulator;
    float GameTime;
    float Glow;
    float CameraYaw;
    float CameraPitch;
    vec4 MaskColor;
    float MaskTolerance;
    vec4 ColorKeyColor;
    float ColorKeyTolerance;
    vec2 LocalUvMin;
    vec2 LocalUvScale;
};

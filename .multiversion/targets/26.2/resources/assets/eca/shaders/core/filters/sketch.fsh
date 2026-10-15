#version 150
// Fork multi-version layer (26.2, #33): 26.2 renders with REVERSE-Z. Its projection swaps near and far (the depth buffer
// holds 1.0 at the near plane and 0.0 at the far plane; it is cleared to 0.0 and tested GREATER_THAN_OR_EQUAL). For one
// view distance the stored value is exactly 1.0 minus what 1.21.1 stored (the depth mapping is hyperbolic in distance,
// and swapping near/far turns d into 1 - d, with or without GL_ARB_clip_control), so the sampled depth is flipped back
// once on read and the authors' forward-Z maths below is unchanged.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 ScreenSize;
uniform float FilterStrength;

in vec2 texCoord;

out vec4 fragColor;

float linearize(float depth) {
    depth = 1.0 - depth;   // 26.2 reverse-Z -> the forward-Z value this formula expects
    float near = 0.05;
    float far = 1024.0;
    float z = near * far / (far - depth * (far - near));
    return min(z, 256.0);
}

float luminance(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 texel = 1.0 / ScreenSize;

    float d_c = linearize(texture(Sampler0, texCoord).r);
    float d_t = linearize(texture(Sampler0, texCoord + vec2(0.0, texel.y)).r);
    float d_b = linearize(texture(Sampler0, texCoord + vec2(0.0, -texel.y)).r);
    float d_l = linearize(texture(Sampler0, texCoord + vec2(-texel.x, 0.0)).r);
    float d_r = linearize(texture(Sampler0, texCoord + vec2(texel.x, 0.0)).r);
    float depthEdge = abs(d_t + d_b + d_l + d_r - 4.0 * d_c);

    float c_tl = luminance(texture(Sampler1, texCoord + vec2(-texel.x, texel.y)).rgb);
    float c_t  = luminance(texture(Sampler1, texCoord + vec2(0.0, texel.y)).rgb);
    float c_tr = luminance(texture(Sampler1, texCoord + vec2(texel.x, texel.y)).rgb);
    float c_l  = luminance(texture(Sampler1, texCoord + vec2(-texel.x, 0.0)).rgb);
    float c_r  = luminance(texture(Sampler1, texCoord + vec2(texel.x, 0.0)).rgb);
    float c_bl = luminance(texture(Sampler1, texCoord + vec2(-texel.x, -texel.y)).rgb);
    float c_b  = luminance(texture(Sampler1, texCoord + vec2(0.0, -texel.y)).rgb);
    float c_br = luminance(texture(Sampler1, texCoord + vec2(texel.x, -texel.y)).rgb);

    float c_gx = -c_tl - 2.0 * c_l - c_bl + c_tr + 2.0 * c_r + c_br;
    float c_gy = -c_tl - 2.0 * c_t - c_tr + c_bl + 2.0 * c_b + c_br;
    float colorEdge = sqrt(c_gx * c_gx + c_gy * c_gy);

    float depthStrength = smoothstep(0.5, 2.0, depthEdge);
    float colorStrength = smoothstep(0.15, 0.5, colorEdge);
    float edge = max(depthStrength, colorStrength);

    fragColor = vec4(mix(texture(Sampler1, texCoord).rgb, vec3(1.0 - edge), FilterStrength), 1.0);
}

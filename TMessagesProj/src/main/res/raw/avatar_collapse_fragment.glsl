#version 300 es

precision mediump float;

out vec4 fragColor;

uniform vec2 u_Resolution;
uniform float u_AvatarRadius;

uniform sampler2D u_Texture;
in vec2 v_TexCoord;

vec2 normalizeVec2(vec2 coord) {
    return 2.0 * (coord - 0.5 * u_Resolution.xy) / u_Resolution.y * vec2(1.0, -1.0);
}

void main() {
    vec2 uv = 2.0 * (gl_FragCoord.xy - 0.5 * u_Resolution.xy) / u_Resolution.y;

    float avatarRadius = u_AvatarRadius / u_Resolution.x;

    float dist = distance(v_TexCoord, vec2(0.5, 0.5));
    if (dist <= u_AvatarRadius) {
        fragColor = texture(u_Texture, v_TexCoord);
    } else {
        discard;
    }
}

#version 300 es

precision highp float;

out vec4 fragColor;

uniform vec2 u_Resolution;
uniform float u_AvatarRadius;
uniform sampler2D u_Texture;

uniform float u_BlurRadius;

// Progress changes
uniform float u_AvatarAlpha;
uniform float u_BlurAmount;
uniform float u_GradientRadius;

uniform vec2 u_AvatarCenterTransition;

uniform vec2 u_AvatarScale;
uniform vec2 u_AvatarCenter;

// normalized values
uniform float u_AttractionRadius;
uniform float u_TopAttractionPointRadius;

in vec2 v_TexCoord;

vec2 normalizeVec2(vec2 coord) {
    return 2.0 * (coord - 0.5 * u_Resolution.xy) / u_Resolution.y * vec2(1.0, -1.0);
}

vec4 blur9x9(sampler2D image, vec2 uv, vec2 resolution, float radius) {
    vec4 color = vec4(0.0);
    float total = 0.0;

    for (int x = -4; x <= 4; x++) {
        for (int y = -4; y <= 4; y++) {
            float weight = 1.0 - length(vec2(x, y)) / 5.656;
            vec2 offset = vec2(x, y) * radius / resolution;
            color += texture(image, uv + offset) * weight;
            total += weight;
        }
    }

    return color / total;
}

float calculateCombinedAttraction() {
    vec2 uv = 2.0 * (gl_FragCoord.xy - 0.5 * u_Resolution.xy) / u_Resolution.y;

    float distToAvatar = distance(uv, normalizeVec2(u_AvatarCenter));
    float distToTop = 1.0 + u_TopAttractionPointRadius - uv.y;

    float attraction1 = smoothstep(0.0, u_AttractionRadius, u_AttractionRadius - distToAvatar);
    float attraction2 = smoothstep(0.0, u_AttractionRadius, u_AttractionRadius - distToTop);
    float combinedAttraction = attraction1 + attraction2;

    return combinedAttraction;
}

vec4 calculateTexColor(vec2 scaledUV) {
    vec4 sharp = texture(u_Texture, scaledUV);
    vec4 blurred = blur9x9(u_Texture, scaledUV, u_Resolution, u_BlurRadius);

    vec4 texColor = mix(mix(sharp, blurred, u_BlurAmount), vec4(vec3(0.0), 1.0), u_AvatarAlpha);

    return texColor;
}

void main() {
    vec2 scaledUV = (v_TexCoord - 0.5) / u_AvatarScale + u_AvatarCenterTransition;

    float dist = distance(scaledUV, vec2(0.5, 0.5));

    if (dist < u_AvatarRadius - u_GradientRadius) {
        if (scaledUV.x < 0.0 || scaledUV.x > 1.0 || scaledUV.y < 0.0 || scaledUV.y > 1.0) {
            discard;
        }

        fragColor = calculateTexColor(scaledUV);
    } else if (dist >= u_AvatarRadius - u_GradientRadius && dist < u_AvatarRadius) {
        float combinedAttraction = calculateCombinedAttraction();
        vec4 texColor = calculateTexColor(scaledUV);

        if (combinedAttraction > 0.52) {
            float fade = smoothstep(u_GradientRadius, 0.0, u_AvatarRadius - dist);
            vec3 finalColor = mix(texColor.rgb, vec3(0.0), fade);
            fragColor = vec4(finalColor, texColor.a);
        } else {
            discard;
        }
    } else {
        float combinedAttraction = calculateCombinedAttraction();

        if (combinedAttraction > 0.52) {
            fragColor = vec4(vec3(0.0), 1.0);
        } else {
            discard;
        }
    }
}

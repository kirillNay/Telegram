precision mediump float;
uniform vec3 u_Resolution;
uniform float u_Time;

// Initial button position
uniform vec2 u_ShareButtonInitCord;
uniform vec2 u_ShareButtonCurrentCord;
uniform float u_ShareButtonRadius;

float circleWithBlur(vec2 uv, vec2 position, float radius, float blur) {
    float dist = distance(position, uv);
    dist = smoothstep(dist - blur, dist, radius);
    return dist * dist * dist * dist * dist;
}

float roundedRectangleWithBlur(vec2 uv, vec2 pos, vec2 size, float radius, float blur) {
    vec2 hSize = size / 2.0 - radius;
    float d = length(max(abs(uv - pos), hSize) - hSize);
    float r = smoothstep(-radius - blur, -radius + blur, -d);
    return r * r * r * r * r;
}

float normalizeX(float x) {
    return 2.0 * x / u_Resolution.x - 1.0;
}

float normalizeY(float y) {
    return 1.0 - 2.0 * y / u_Resolution.y;
}

vec2 normalizeVec2(vec2 coord) {
    return 2.0 * (coord - 0.5 * u_Resolution.xy) / u_Resolution.y;
}

void main() {
    vec2 uv = 2.0 * (gl_FragCoord.xy - 0.5 * u_Resolution.xy) / u_Resolution.y; // normalized fragment coordinate
    vec2 shareButtonInitCordNorm = 2.0 * (u_ShareButtonInitCord.xy - 0.5 * u_Resolution.xy) / u_Resolution.y * vec2(1., -1.); // normalized share button coordinate
    vec2 shareButtonCurrentCordNorm = 2.0 * (u_ShareButtonCurrentCord.xy - 0.5 * u_Resolution.xy) / u_Resolution.y * vec2(1., -1.);
    float shareButtonRadiousNorm = u_ShareButtonRadius / u_Resolution.y;

    float pixelIntensity = 0.0;
    float Time = u_Time / 5.0;

    float maxHeight = 1.0;
    float maxWidth = 1.0;

    float maxY = 0.0;
    float minX = 0.15;

    vec2 rect = vec2(shareButtonInitCordNorm.x, shareButtonInitCordNorm.y + Time * 2.0);
    vec2 rectSize = vec2(0.2 + Time * 3., 0.2 + Time);
    float rectRadius = 0.15;
    float rectBlur = 0.05;

    float circleIntensity = circleWithBlur(uv, shareButtonCurrentCordNorm, shareButtonRadiousNorm, 0.1);
    float rectIntecity = roundedRectangleWithBlur(uv, rect, rectSize, rectRadius, 0.1);

    pixelIntensity = smoothstep(0.99, 1.0, rectIntecity + circleIntensity);

    if (pixelIntensity >= 0.99) {
        if (rectIntecity > 0.) {
            float dist = distance(uv, shareButtonCurrentCordNorm);
            float alpha = smoothstep(shareButtonRadiousNorm - 0.1, shareButtonRadiousNorm * 1.5, dist);

            gl_FragColor = vec4(1.0, 1.0, 1.0, alpha);
        } else {
            gl_FragColor = vec4(1.0, 1.0, 1.0, 0.0);
        }

    } else {
        discard;
    }

}
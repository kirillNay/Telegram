precision mediump float;
uniform vec3 u_Resolution;
uniform float u_Progress;
uniform float u_ProgressF;

// Initial button position
uniform vec2 u_ShareButtonInitCord;
uniform float u_ShareButtonRadius;

uniform vec2 u_ShareButtonCurrentCord;

// Quick share selector
uniform vec2 u_SelectorFinalCenterCord;
uniform vec2 u_SelectorFinalSize; // x - width; y - height

float circleWithBlur(vec2 uv, vec2 position, float radius, float blur) {
    float dist = distance(position, uv);
    dist = smoothstep(dist - blur, dist, radius);
    return dist * dist * dist * dist * dist;
}

float roundedRectangleWithBlur(vec2 uv, vec2 pos, vec2 size, float radius, float blur) {
    vec2 hSize = size / 2.0 - radius;
    float d = length(max(abs(uv - pos), hSize) - hSize);
    float r = smoothstep(-radius - blur, -radius + blur, -d);
    return r;
}

vec2 normalizeVec2(vec2 coord) {
    return 2.0 * (coord - 0.5 * u_Resolution.xy) / u_Resolution.y * vec2(1., -1.);
}

float interpolateLinear(float start, float end, float factor) {
    return start + factor * (end - start);
}

float normalizeX(float x) {
    return 2.0 * x / u_Resolution.x - 1.0;
}

float normalizeY(float y) {
    return 1.0 - 2.0 * y / u_Resolution.y;
}

void main() {
    vec2 uv = 2.0 * (gl_FragCoord.xy - 0.5 * u_Resolution.xy) / u_Resolution.y; // normalized fragment coordinate
    vec2 shareButtonInitCordNorm = normalizeVec2(u_ShareButtonInitCord.xy); // normalized share button coordinate
    vec2 shareButtonCurrentCordNorm = normalizeVec2(u_ShareButtonCurrentCord.xy);
    float shareButtonRadiusNorm = u_ShareButtonRadius * 2. / u_Resolution.y;

    vec2 selectorFinalCordNorm = normalizeVec2(u_SelectorFinalCenterCord);

    float aspectRatio = u_Resolution.y / u_Resolution.x;
    // TODO come with better solution
    vec2 selectorFinalSizeNorm = vec2(u_SelectorFinalSize.x / u_Resolution.x * 1.5, u_SelectorFinalSize.y / u_Resolution.y * 3.);

    vec2 rect = vec2(interpolateLinear(shareButtonInitCordNorm.x, selectorFinalCordNorm.x, u_Progress), interpolateLinear(shareButtonInitCordNorm.y, selectorFinalCordNorm.y, u_ProgressF));
    float rectWidth = interpolateLinear(shareButtonRadiusNorm, selectorFinalSizeNorm.x, u_Progress) / 1.5;
    float rectHeight = interpolateLinear(shareButtonRadiusNorm, selectorFinalSizeNorm.y, min(1.0, u_ProgressF * 2.)) / 1.5;
    vec2 rectSize = vec2(rectWidth, rectHeight);
    float rectRadius = rectHeight * .5;

    float blur = interpolateLinear(0.1, 0., min(u_Progress, 1.));
    float circleIntensity = circleWithBlur(uv, shareButtonCurrentCordNorm, shareButtonRadiusNorm, blur);
    float rectIntecity = roundedRectangleWithBlur(uv, rect, rectSize, rectRadius, blur);

    float pixelIntensity = smoothstep(0.99, 1.0, rectIntecity + circleIntensity);

    if (pixelIntensity >= 0.99) {
        gl_FragColor = vec4(1.0, 1.0, 1.0, rectIntecity);
    } else {
        discard;
    }

}
precision mediump float;

uniform float hue;          // Hue        [0,1]
uniform float saturation;   // Saturation [0,1]
uniform float brightness;   // Brightness [-1,1]
uniform float contrast;     // Contrast   [-1,1]
uniform sampler2D uSampler;
varying vec2 vTextureCoord;

// Color Space Convert -> RGB to HSV
vec3 rgbToHsv(vec3 c) {
    float minVal = min(c.r, min(c.g, c.b));
    float maxVal = max(c.r, max(c.g, c.b));
    float delta = maxVal - minVal;

    vec3 hsv;
    hsv.z = maxVal;  // Value

    if (delta < 0.00001) {
        hsv.x = 0.0; // Hue
        hsv.y = 0.0; // Saturation
        return hsv;
    }

    hsv.y = delta / maxVal; // Saturation

    if (c.r >= maxVal) {
        hsv.x = (c.g - c.b) / delta; // Red is max
    } else if (c.g >= maxVal) {
        hsv.x = 2.0 + (c.b - c.r) / delta; // Green is max
    } else {
        hsv.x = 4.0 + (c.r - c.g) / delta; // Blue is max
    }

    hsv.x = mod(hsv.x / 6.0, 1.0); // Normalize hue to [0, 1]
    return hsv;
}

// Color Space Convert ->  HSV to RGB
vec3 hsvToRgb(vec3 c) {
    if (c.y <= 0.0) {
        return vec3(c.z); // Gray, no color
    }

    float h = c.x * 6.0; // Hue in [0, 6)
    float f = h - floor(h);
    float p = c.z * (1.0 - c.y);
    float q = c.z * (1.0 - f * c.y);
    float t = c.z * (1.0 - (1.0 - f) * c.y);

    if (h < 1.0) {
        return vec3(c.z, t, p);
    } else if (h < 2.0) {
        return vec3(q, c.z, p);
    } else if (h < 3.0) {
        return vec3(p, c.z, t);
    } else if (h < 4.0) {
        return vec3(p, q, c.z);
    } else if (h < 5.0) {
        return vec3(t, p, c.z);
    } else {
        return vec3(c.z, p, q);
    }
}


void main() {
    vec4 color = texture2D(uSampler, vTextureCoord);
    vec3 hsv = rgbToHsv(color.rgb);

    // Hue Adjustment
    hsv.x += hue;
    if (hsv.x > 1.0) hsv.x -= 1.0;
    if (hsv.x < 0.0) hsv.x += 1.0;

    // Saturation Adjustment
    hsv.y *= saturation;
    if (hsv.y > 1.0) hsv.y = 1.0;
    if (hsv.y < 0.0) hsv.y = 0.0;

    // brightness Adjustment
    //postColor.rgb += brightness;
    hsv.z += brightness;

    //Contrast Adjustment
    //postColor.rgb = (postColor.rgb - 0.5) * contrast + 0.5;
    hsv.z = mix(hsv.z, 0.5, 1.0 - contrast);

    gl_FragColor = vec4(hsvToRgb(hsv), color.a);
}
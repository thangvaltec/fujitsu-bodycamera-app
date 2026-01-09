precision mediump float;

uniform float hue;    // Hue
uniform float saturation;   // Saturation
uniform float brightness;   // Brightness
uniform float contrast;     // Contrast
uniform sampler2D uSampler;
varying vec2 vTextureCoord;

float hue2rgb(float p, float q, float t) {
    if (t < 0.0) t += 1.0;
    if (t > 1.0) t -= 1.0;
    if (t < 1.0 / 6.0) return p + (q - p) * 6.0 * t;
    if (t < 1.0 / 2.0) return q;
    if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
    return p;
}

// RGB to HSL conversion
vec3 RGBtoHSL(vec3 c) {
    float max = max(c.r, max(c.g, c.b));
    float min = min(c.r, min(c.g, c.b));
    float h = 0.0, s = 0.0, l = (max + min) / 2.0;

    if (max != min) {
        float delta = max - min;
        s = l > 0.5 ? delta / (2.0 - max - min) : delta / (max + min);

        if (max == c.r) {
            h = (c.g - c.b) / delta + (c.g < c.b ? 6.0 : 0.0);
        } else if (max == c.g) {
            h = (c.b - c.r) / delta + 2.0;
        } else {
            h = (c.r - c.g) / delta + 4.0;
        }
        h /= 6.0;
    }

    return vec3(h, s, l);
}

// HSL to RGB conversion
vec3 HSLtoRGB(vec3 hsl) {
    float r, g, b;
    float temp1, temp2;
    float h = hsl.x;
    float s = hsl.y;
    float l = hsl.z;

    if (s == 0.0) {
        r = g = b = l; // achromatic
    } else {
        temp2 = l < 0.5 ? l * (1.0 + s) : (l + s - l * s);
        temp1 = 2.0 * l - temp2;
        r = hue2rgb(temp1, temp2, h + 1.0 / 3.0);
        g = hue2rgb(temp1, temp2, h);
        b = hue2rgb(temp1, temp2, h - 1.0 / 3.0);
    }

    return vec3(r, g, b);
}



void main() {
    // 获取纹理颜色
    vec4 color = texture2D(uSampler, vTextureCoord);
    vec3 hsl = RGBtoHSL(color.rgb);

    // 调整色相
    hsl.x += hue / 360.0;  // 色相范围是 0-1，输入范围是 0-360
    if (hsl.x > 1.0) hsl.x -= 1.0;
    if (hsl.x < 0.0) hsl.x = 0.0;
    // 调整饱和度
    //hsl.y = mix(hsl.y, saturation, 1.0);

    // 调整亮度
    //hsl.z += brightness;

    // 调整对比度
    //hsl.z = mix(hsl.z, 0.5, 1.0 - contrast);

    // 转换回 RGB
    vec3 finalColor = HSLtoRGB(hsl);

    // 输出最终颜色
    gl_FragColor = vec4(finalColor, color.a);
}
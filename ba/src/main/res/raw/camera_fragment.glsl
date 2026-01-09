#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES sCamera;
varying vec2 vTextureCoord;

uniform int filterFlag;

//transform into gray
void grey(inout vec4 color){
    float weightMean = color.r * 0.3 + color.g * 0.59 + color.b * 0.11;
    color.r = color.g = color.b = weightMean;
}

void main() {
  vec4 color = texture2D(sCamera, vTextureCoord);
  if(filterFlag == 100){
    grey(color);
    gl_FragColor = color;
  }else{
    gl_FragColor = color;
  }

}
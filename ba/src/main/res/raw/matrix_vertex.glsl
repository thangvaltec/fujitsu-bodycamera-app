attribute vec4 aPosition;
attribute vec4 aTextureCoord;

varying vec2 vTextureCoord;
uniform mat4 textureTransform;

void main() {
  gl_Position = aPosition;
  vTextureCoord = (textureTransform * aTextureCoord).xy;
}
#version 430

in vec2 v_texcoords;

uniform sampler2D u_tex;

out vec4 f_color;

void main() {
    f_color = texture(u_tex, v_texcoords);
}
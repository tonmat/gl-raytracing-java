#version 430

in vec2 v_texcoords;

uniform sampler2D u_tex;

out vec4 f_color;

void main() {
    f_color = texture(u_tex, v_texcoords);
    f_color.rgb = vec3(1.0) - exp(-f_color.rgb * 4.0);
    f_color.rgb = pow(f_color.rgb, vec3(1.0 / 2.2));
}
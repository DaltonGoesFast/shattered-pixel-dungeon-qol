shader_type canvas_item;
render_mode blend_mix, unshaded;

uniform vec4 chroma_color : source_color = vec4(1.0, 0.031372549, 0.576470588, 1.0);
// x = bottom band height (0–1), y = left strip width (0–1), z = left strip top Y (0–1).
uniform vec3 l_shape_uv = vec3(0.185, 0.219, 0.0);

float l_shape_mask(vec2 uv, vec3 L) {
	float bot_h = L.x;
	float left_w = L.y;
	float left_top = L.z;
	float in_bottom = step(1.0 - bot_h, uv.y);
	float in_left = step(uv.x, left_w) * step(left_top, uv.y);
	return max(in_bottom, in_left);
}

void fragment() {
	float m = l_shape_mask(UV, l_shape_uv);
	COLOR = vec4(chroma_color.rgb, chroma_color.a * m);
}

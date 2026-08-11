shader_type canvas_item;
render_mode blend_mix, unshaded;

uniform vec4 chroma_color : source_color = vec4(1.0, 0.031372549, 0.576470588, 1.0);
// x = bottom band height (0–1), y = left strip width (0–1), z = left strip top Y (0–1).
uniform vec3 l_shape_uv = vec3(0.185, 0.219, 0.0);
uniform float edge_feather_v = 0.0;

float soft_above(float y, float edge, float soft) {
	if (soft <= 0.00001) {
		return step(edge, y);
	}
	return smoothstep(edge, edge + soft, y);
}

float l_shape_mask(vec2 uv, vec3 L, float feather_v) {
	float bot_h = L.x;
	float left_w = L.y;
	float left_top = L.z;
	float in_bottom = soft_above(uv.y, 1.0 - bot_h, feather_v);
	float in_left = step(uv.x, left_w) * soft_above(uv.y, left_top, feather_v);
	return max(in_bottom, in_left);
}

void fragment() {
	float m = l_shape_mask(UV, l_shape_uv, edge_feather_v);
	COLOR = vec4(chroma_color.rgb, chroma_color.a * m);
}

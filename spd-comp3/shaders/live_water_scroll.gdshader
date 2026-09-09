shader_type canvas_item;
render_mode blend_mix, unshaded;

// Tiled SPD water texture (e.g. environment/water0.png); scroll matches AlchemyScene SkinnedBlock offset.
uniform sampler2D water_tex : source_color, filter_nearest, repeat_enable;
uniform vec2 scroll_px = vec2(0.0);
uniform vec2 rect_px = vec2(1920.0, 1080.0);
// x = bottom band height (0–1), y = left strip width (0–1), z = left strip top Y (0–1).
uniform vec3 l_shape_uv = vec3(0.185, 0.219, 0.0);
// Height of a full-width top band (0–1). 0 = off.
uniform float top_bar_uv = 0.0;
// >1.0 stretches each tile over more pixels (chunkier caustics, closer to alchemy reference).
uniform float texture_scale = 4.5;
// UV.y (0 top, 1 bottom): linear fade of water toward black (alchemy bottom band ~lower quarter).
uniform float gradient_fade_start = 0.73;
uniform float gradient_fade_end = 1.0;
// Top fade toward black at the top of the screen. start >= end; both 0 = no extra fade.
uniform float top_gradient_fade_start = 0.0;
uniform float top_gradient_fade_end = 0.0;
// Soften L-shape edges along UV.y (top of bottom bar / left-strip top). 0 = hard cut.
uniform float edge_feather_v = 0.0;
// Soften the bottom edge of the top bar. 0 = hard cut.
uniform float top_edge_feather_v = 0.0;

float soft_above(float y, float edge, float soft) {
	if (soft <= 0.00001) {
		return step(edge, y);
	}
	return smoothstep(edge, edge + soft, y);
}

float soft_below(float y, float edge, float soft) {
	if (edge <= 0.00001) {
		return 0.0;
	}
	if (soft <= 0.00001) {
		return 1.0 - step(edge, y);
	}
	return 1.0 - smoothstep(edge - soft, edge, y);
}

float l_shape_mask(vec2 uv, vec3 L, float feather_v, float top_h, float top_feather) {
	float bot_h = L.x;
	float left_w = L.y;
	float left_top = L.z;
	float in_bottom = soft_above(uv.y, 1.0 - bot_h, feather_v);
	float in_left = step(uv.x, left_w) * soft_above(uv.y, left_top, feather_v);
	float in_top = soft_below(uv.y, top_h, top_feather);
	return max(max(in_bottom, in_left), in_top);
}

void fragment() {
	vec2 tex_sz = vec2(textureSize(water_tex, 0));
	vec2 pixel_pos = UV * rect_px;
	vec2 scaled_pos = pixel_pos / max(texture_scale, 0.001);
	vec2 sample_uv = (scaled_pos + scroll_px) / tex_sz;
	vec4 water = texture(water_tex, sample_uv);
	float g = clamp((UV.y - gradient_fade_start) / max(gradient_fade_end - gradient_fade_start, 0.001), 0.0, 1.0);
	water.rgb *= 1.0 - g;
	float tg = clamp(
		(top_gradient_fade_start - UV.y) / max(top_gradient_fade_start - top_gradient_fade_end, 0.001),
		0.0,
		1.0
	);
	water.rgb *= 1.0 - tg;
	float m = l_shape_mask(UV, l_shape_uv, edge_feather_v, top_bar_uv, top_edge_feather_v);
	water.a *= m;
	COLOR = water;
}

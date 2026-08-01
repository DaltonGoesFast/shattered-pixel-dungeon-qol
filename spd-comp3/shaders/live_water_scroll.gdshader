shader_type canvas_item;
render_mode blend_mix, unshaded;

// Tiled SPD water texture (e.g. environment/water0.png); scroll matches AlchemyScene SkinnedBlock offset.
uniform sampler2D water_tex : source_color, filter_nearest, repeat_enable;
uniform vec2 scroll_px = vec2(0.0);
uniform vec2 rect_px = vec2(1920.0, 1080.0);
// x = bottom band height (0–1), y = left strip width (0–1), z = left strip top Y (0–1).
uniform vec3 l_shape_uv = vec3(0.185, 0.219, 0.0);
// >1.0 stretches each tile over more pixels (chunkier caustics, closer to alchemy reference).
uniform float texture_scale = 4.5;
// UV.y (0 top, 1 bottom): linear fade of water toward black (alchemy bottom band ~lower quarter).
uniform float gradient_fade_start = 0.73;
uniform float gradient_fade_end = 1.0;

float l_shape_mask(vec2 uv, vec3 L) {
	float bot_h = L.x;
	float left_w = L.y;
	float left_top = L.z;
	float in_bottom = step(1.0 - bot_h, uv.y);
	float in_left = step(uv.x, left_w) * step(left_top, uv.y);
	return max(in_bottom, in_left);
}

void fragment() {
	vec2 tex_sz = vec2(textureSize(water_tex, 0));
	vec2 pixel_pos = UV * rect_px;
	vec2 scaled_pos = pixel_pos / max(texture_scale, 0.001);
	vec2 sample_uv = (scaled_pos + scroll_px) / tex_sz;
	vec4 water = texture(water_tex, sample_uv);
	float g = clamp((UV.y - gradient_fade_start) / max(gradient_fade_end - gradient_fade_start, 0.001), 0.0, 1.0);
	water.rgb *= 1.0 - g;
	float m = l_shape_mask(UV, l_shape_uv);
	water.a *= m;
	COLOR = water;
}

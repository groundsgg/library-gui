#version 330

// Overrides Minecraft's core/text vertex shader.
//
// Outside IS_GUI this file preprocesses to the vanilla source, byte for byte: world text, signs
// and nameplates must keep rendering exactly as before, because the same file serves them.
//
// Inside IS_GUI it recognises marker glyphs and redraws them somewhere else. That is what makes a
// hover effect possible at all: a tooltip renders only for the slot under the cursor and only for
// that slot's own item, so a glyph carried in an item's tooltip is inherently hover-scoped and
// item-scoped. The server never learns about hover; the client's own tooltip is the signal.
//
// A marker is identified by pixels inside its own sprite rather than by its colour, which leaves
// the whole vertex colour free to carry position — and lets the sprite state its own size, so one
// glyph can outline a shape of any dimensions instead of stamping a fixed box.

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
#endif

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
in ivec2 UV2;
#endif

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler2;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
#endif

out vec4 vertexColor;
out vec2 texCoord0;

#ifdef IS_GUI
// The glyph atlas. Vanilla's text.vsh does not declare it — only the fragment stage samples it —
// but the pipeline binds it either way.
uniform sampler2D Sampler0;

// Written into the first data pixel of every marker sprite. Font sheets are white or single
// channel intensity, so a sheet can never produce this triple by accident.
const ivec3 GROUNDS_ID = ivec3(0xFE, 0x4E, 0x2A);

int grounds_byte(float channel) {
    // UNORM8 to float is b/255 correctly rounded, so this recovers b exactly for all 256 values.
    return int(channel * 255.0 + 0.5);
}

// Reads the data pixel [index] steps inward from whichever corner this vertex sits on. The pixels
// are mirrored into all four corners of the sprite, so any of the quad's four vertices finds them.
ivec3 grounds_data(vec2 texel, vec2 direction, int index) {
    ivec2 position = ivec2(floor(texel));
    position.x -= index * int(direction.x);
    return ivec3(round(texelFetch(Sampler0, position, 0).rgb * 255.0));
}
#endif

void main() {
#ifdef IS_GUI
    // Which corner of the quad this vertex is, as 0 or 1 per axis. The atlas insets every glyph by
    // a hundredth of a texel, so a left edge lands just past an integer and a right edge just short
    // of one; step() on the fraction reads that back. gl_VertexID cannot be used instead — the
    // draw's base vertex is not a multiple of four.
    //
    // The atlas size has to be asked for, not assumed: a wrong size turns the fraction into noise,
    // the corners collapse onto each other, and the quad silently disappears.
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 texel = UV0 * texSize;
    vec2 corner = step(0.5, fract(texel));
    vec2 direction = corner * 2.0 - 1.0;

    if (grounds_data(texel, direction, 0) == GROUNDS_ID) {
        // The sprite states its own size, so the quad is drawn at the artwork's true dimensions
        // whatever height the font declared it at.
        vec2 content = vec2(grounds_data(texel, direction, 1).rg) + 1.0;

        // ProjMat is the GUI's orthographic matrix: m00 = 2/width, m11 = -2/height. The window
        // stores the ceiling of framebuffer/guiScale, so re-apply it, minus a hair for the round
        // trip through float.
        vec2 screen = ceil(2.0 / vec2(ProjMat[0][0], -ProjMat[1][1]) - 0.001);

        // The server cannot know where the window lands, so it sends the sprite's top-left corner
        // as an offset from the screen's centre and the shader supplies the centre.
        vec2 origin =
            floor(screen * 0.5) + vec2(grounds_byte(Color.r), grounds_byte(Color.g)) - 128.0;

        // Position already arrives in absolute GUI pixels — the tooltip's offset is folded in on
        // the CPU — so absolute coordinates can simply be written over it.
        gl_Position = ProjMat * ModelViewMat * vec4(origin + corner * content, Position.z, 1.0);
        // Crop the data row off whichever horizontal edge this vertex sits on, so it never shows.
        texCoord0 = UV0 - vec2(0.0, direction.y / texSize.y);
        // Drop the payload before it can tint anything; the sprite carries the colour.
        vertexColor = vec4(1.0);
        return;
    }
#endif

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
#else
    vertexColor = Color;
#endif
    texCoord0 = UV0;
}

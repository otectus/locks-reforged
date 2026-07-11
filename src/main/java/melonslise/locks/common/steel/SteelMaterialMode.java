package melonslise.locks.common.steel;

/**
 * How Locks decides whether to provide (and generate) its own native steel material.
 *
 * <ul>
 *   <li>{@link #AUTO} — inspect the standard Forge steel tags (ignoring Locks' own entries) and only
 *       provide the forms no other mod supplies. This is the default.</li>
 *   <li>{@link #FORCE_NATIVE} — always keep Locks' native steel recipes and ore generation on, even when
 *       an external steel economy is present.</li>
 *   <li>{@link #EXTERNAL_ONLY} — never provide native steel acquisition or generation, even if the common
 *       steel tags are empty (a warning is logged if a required form is missing).</li>
 * </ul>
 *
 * Native blocks and items stay registered under stable IDs in every mode — only <em>acquisition</em>
 * (recipes, ore generation, creative-tab presentation) is toggled, so existing worlds and stacks remain valid.
 */
public enum SteelMaterialMode
{
	AUTO,
	FORCE_NATIVE,
	EXTERNAL_ONLY
}

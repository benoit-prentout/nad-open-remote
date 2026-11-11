package org.openremote.nad

/**
 * How fast the knob moves the volume, relative to a plain linear sweep.
 * dB is already a logarithmic scale, so the fix for "gets loud too fast" is finer control up high:
 * 0.6 up to [comfort] dB, then easing linearly down to 0.2 at [max].
 */
fun volumeGain(db: Float, max: Float, comfort: Float = -45f): Float {
    if (db <= comfort || max <= comfort) return 0.6f
    return 0.6f - 0.4f * ((db - comfort) / (max - comfort)).coerceIn(0f, 1f)
}

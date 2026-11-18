package com.nuvio.app.features.luxo

/**
 * «ريموت موج» platform pieces.
 *
 * TV side: [mawjRemoteKey] presses a remote key inside Mawj, [mawjRemoteVolume] changes the TV's media volume.
 * Controller side: [mawjRemotePairRequest] asks the TV to show its pairing code by itself.
 */

/** Presses one remote key inside Mawj: up, down, left, right, ok, back. */
expect fun mawjRemoteKey(key: String)

/**
 * Changes the media volume: op is up, down, mute or set (with [value] 0..100).
 * Returns the volume after the change, 0..100, or -1 when it cannot be read.
 */
expect fun mawjRemoteVolume(op: String, value: Int = 0): Int

/** Asks the TV at [host] to show its pairing code; true when the TV answered. */
expect suspend fun mawjRemotePairRequest(host: String, port: Int, fromName: String): Boolean

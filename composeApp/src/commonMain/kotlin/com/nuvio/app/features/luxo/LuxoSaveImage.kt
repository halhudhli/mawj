package com.nuvio.app.features.luxo

/** Downloads an image in full resolution into the device gallery (Pictures/Luxo). */
expect suspend fun luxoSaveImageFromUrl(url: String, name: String): Boolean

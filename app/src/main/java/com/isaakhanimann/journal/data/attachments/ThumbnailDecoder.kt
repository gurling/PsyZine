// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import javax.inject.Inject

/** Decoder seam for allocation-failure and concurrency verification without changing originals. */
open class ThumbnailDecoder @Inject constructor() {
    open fun decode(path: String, options: BitmapFactory.Options): Bitmap? = BitmapFactory.decodeFile(path, options)
}

package com.nuvio.app.features.luxo

/** Feeds the «تابع المشاهدة» home-screen widget (Android); up to three lines of text. */
internal expect fun luxoPublishContinueWatching(lines: List<String>)

/** The nearest upcoming episode for the home-screen widget (null hides the line). */
internal expect fun luxoPublishWidgetNext(line: String?)

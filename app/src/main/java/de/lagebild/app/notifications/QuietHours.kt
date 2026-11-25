package de.lagebild.app.notifications

/** Ruhezeiten, z. B. 22:00–07:00 (auch über Mitternacht). Angaben in Minuten seit 0:00 Uhr. */
object QuietHours {
    fun isQuiet(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean = when {
        startMinutes == endMinutes -> false
        startMinutes < endMinutes -> nowMinutes in startMinutes until endMinutes
        else -> nowMinutes >= startMinutes || nowMinutes < endMinutes
    }

    fun format(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
}

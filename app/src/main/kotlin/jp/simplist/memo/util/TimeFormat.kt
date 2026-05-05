package jp.simplist.memo.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 検索結果カード等で使う「今日 HH:mm / 昨日 HH:mm / yyyy/MM/dd HH:mm」形式。 */
object TimeFormat {
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    fun relative(epochMs: Long): String {
        val now = Calendar.getInstance()
        val date = Calendar.getInstance().apply { timeInMillis = epochMs }
        val sameDay = now[Calendar.YEAR] == date[Calendar.YEAR] &&
            now[Calendar.DAY_OF_YEAR] == date[Calendar.DAY_OF_YEAR]
        if (sameDay) return "今日 ${timeFmt.format(Date(epochMs))}"
        val yesterday = (Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) })
        val isYesterday = yesterday[Calendar.YEAR] == date[Calendar.YEAR] &&
            yesterday[Calendar.DAY_OF_YEAR] == date[Calendar.DAY_OF_YEAR]
        if (isYesterday) return "昨日 ${timeFmt.format(Date(epochMs))}"
        return dateFmt.format(Date(epochMs))
    }

    /** ゴミ箱の「あと N 日」表示用。 */
    fun daysRemainingFromTrash(deletedAtMs: Long, retentionMs: Long): Int {
        val expireAt = deletedAtMs + retentionMs
        val remainingMs = expireAt - System.currentTimeMillis()
        return ((remainingMs / (24L * 60L * 60L * 1000L)).toInt()).coerceAtLeast(0)
    }
}

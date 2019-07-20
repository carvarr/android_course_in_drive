package curso.carlos.indrive.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class CacheManager {

    val prefs: SharedPreferences
    val PREFS_NAME = "curso.carlos.indrive.cache"

    constructor(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, 0)
    }


    fun setValue(key: String, value: String) {
        prefs.edit {
            putString(key, value)
        }
    }

    fun getValue(key: String): String {
        return prefs.getString(key, "")
    }

}
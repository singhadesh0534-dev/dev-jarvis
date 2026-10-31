package com.adesh.devjarvis.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class Contact(val id: Long, val name: String, val numbers: List<String>)

/** Phone ke contacts + fuzzy naam match ("Mummy", "Bhai", typos). */
@Singleton
class ContactRepository @Inject constructor(@ApplicationContext private val context: Context) {

    sealed interface Lookup {
        data class Found(val contact: Contact, val number: String) : Lookup
        data class Ambiguous(val options: List<Contact>) : Lookup
        data object NotFound : Lookup
        data object NoPermission : Lookup
    }

    private var cache: List<Contact> = emptyList()
    private var cachedAt = 0L

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** [query] can be a name/nickname or a raw phone number. */
    suspend fun resolve(query: String): Lookup {
        val digits = query.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 5 && digits.length >= query.count { !it.isWhitespace() } - 2) {
            return Lookup.Found(Contact(-1, query, listOf(digits)), digits)
        }
        if (!hasPermission()) return Lookup.NoPermission
        val all = load()
        val matches = FuzzyMatcher.rank(query, all) { listOf(it.name) }
        if (matches.isEmpty()) return Lookup.NotFound
        if (FuzzyMatcher.isAmbiguous(matches) { a, b -> a.name.equals(b.name, ignoreCase = true) }) {
            return Lookup.Ambiguous(matches.take(3).map { it.item })
        }
        val best = matches.first().item
        return Lookup.Found(best, best.numbers.first())
    }

    private suspend fun load(): List<Contact> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cache.isNotEmpty() && now - cachedAt < CACHE_MS) return@withContext cache
        val byId = LinkedHashMap<Long, Pair<String, MutableList<String>>>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val number = c.getString(2)?.filter { it.isDigit() || it == '+' } ?: continue
                val primary = c.getInt(3) == 1
                val entry = byId.getOrPut(id) { name to mutableListOf() }
                if (number !in entry.second) {
                    if (primary) entry.second.add(0, number) else entry.second.add(number)
                }
            }
        }
        cache = byId.map { (id, v) -> Contact(id, v.first, v.second) }.filter { it.numbers.isNotEmpty() }
        cachedAt = now
        cache
    }

    private companion object {
        const val CACHE_MS = 5 * 60_000L
    }
}

package com.npk.kfv.service

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong

inline fun <T> random(block: RandomDataGenerator.() -> T): T = RandomDataGenerator.block()

object RandomDataGenerator {

    enum class CountryType(val valueFun: (Locale) -> String) {
        Code({ locale -> locale.country }),
        Iso3({ locale -> locale.isO3Country }),
        Name({ locale -> locale.getDisplayName(Locale.ROOT) })
    }

    private val srand = Random.Default

    private val charPool = buildList {
        addAll('A'..'Z')
        addAll('a'..'z')
    }
    private val availableNames = listOf(
        "Avery", "Higgins", "Melody", "Carson", "Roland", "Woodard", "Kerry", "Rowe", "Kendrick", "Mclean", "Araceli", "Patrick", "Lourdes", "Liz",
        "Columbus", "Garcia", "Hoyt", "Bruce", "Lucinda", "Gardner", "Beth", "Salazar", "Zackary", "Wilson", "Juliette", "Underwood", "Constance",
        "Boyd", "Summers", "Timothy", "Daniels", "Johnson", "Weaver", "Tania", "Huerta", "Roberta", "Ferrell", "Hiram", "Horne", "Manning", "Roy"
    )
    private val availableLocalesByCountries by lazy {
        Locale.getISOCountries().map { countryCode -> Locale.of("", countryCode) }
    }
    private val availableCurrencies by lazy { Currency.getAvailableCurrencies() }

    private const val OBJECT_ID_LOW_ORDER_THREE_BYTES: Int = 0x00ffffff
    private val objectIdRandomValue: Long
    private val objectIdNextCounter: AtomicInteger

    init {
        val secureRandom = SecureRandom()
        objectIdRandomValue = secureRandom.nextLong() and OBJECT_ID_LOW_ORDER_THREE_BYTES.inv().toLong()
        objectIdNextCounter = AtomicInteger(secureRandom.nextInt())
    }

    fun uuid(): String = UUID.randomUUID().toString()

    fun objectId(): String {
        val timestamp = (Date().time / 1000).toInt()
        val nonce = objectIdRandomValue or (objectIdNextCounter.getAndIncrement() and OBJECT_ID_LOW_ORDER_THREE_BYTES).toLong()
        val bytes = ByteBuffer.allocate(12)
            .putInt(timestamp)
            .putLong(nonce)
            .array()
        return HexFormat.of().formatHex(bytes)
    }

    fun string(range: IntRange = 3..9): String {
        val chars = CharArray(srand.nextInt(range)) { charPool.random(srand) }
        return String(chars)
    }

    fun int(range: IntRange = 1..Int.MAX_VALUE): Int = srand.nextInt(range)

    fun long(range: LongRange = 1..Long.MAX_VALUE): Long = srand.nextLong(range)

    fun float(): Float = srand.nextFloat()

    fun double(): Double = srand.nextDouble()

    fun double(from: Double, until: Double): Double = srand.nextDouble(from, until)

    fun boolean(hitPercent: Int = 50): Boolean = hitPercent >= srand.nextInt(1..100)

    fun name(): String = availableNames.random(srand)

    fun country(type: CountryType = CountryType.Code): String = type.valueFun(availableLocalesByCountries.random(srand))

    fun currency(): Currency = availableCurrencies.random(srand)

    fun email(): String = "${name()}.${name()}@email.com".lowercase(Locale.ROOT)

    fun phoneNumber(): String = "+1-202-555-" + "${int(1..9999)}".padStart(4, '0')

    fun <K> from(vararg items: K): K = items.random(srand)

    fun <K> from(items: Collection<K>): K = items.random(srand)

}

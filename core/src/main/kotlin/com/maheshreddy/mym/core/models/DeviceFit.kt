package com.maheshreddy.mym.core.models

import kotlin.math.roundToInt

/** How fast this phone's CPU is relative to a Snapdragon 8 Gen 3 (the phone the baselines were measured on). */
enum class SocTier(val label: String, val factor: Double) {
    FLAGSHIP("Flagship", 1.0),
    UPPER("Upper mid-range", 0.6),
    MID("Mid-range", 0.35),
    LOW("Entry level", 0.18),
}

/** What the device check found. */
data class DeviceProfile(val totalRamMb: Int, val socModel: String?, val cores: Int, val tier: SocTier) {
    /** As advertised: Android reports RAM minus what the system reserves, so round up. */
    val ramGb: Int get() = kotlin.math.ceil(totalRamMb / 1024.0).toInt()
    companion object {
        /** Known SoC model ids (Build.SOC_MODEL) by tier; unknown chips fall back to RAM and core count. */
        private val flagship = Regex("""^(SM8650|SM8750|SM8850|SM8635|MT699\d|MT698\d|S5E99\d\d|Tensor G[45]|GS[0-9]0[1-9])""", RegexOption.IGNORE_CASE)
        private val upper = Regex("""^(SM8550|SM8475|SM8450|SM7675|SM7635|SM7550|MT689\d|S5E98\d\d|Tensor G[23]|GS201|ZUMA)""", RegexOption.IGNORE_CASE)

        fun of(totalRamMb: Int, socModel: String?, cores: Int): DeviceProfile {
            val soc = socModel?.trim()?.takeIf { it.isNotEmpty() && !it.equals("unknown", true) }
            val tier = when {
                soc != null && flagship.containsMatchIn(soc) -> SocTier.FLAGSHIP
                soc != null && upper.containsMatchIn(soc) -> SocTier.UPPER
                totalRamMb >= 11_000 && cores >= 8 -> SocTier.UPPER
                totalRamMb >= 5_500 -> SocTier.MID
                else -> SocTier.LOW
            }
            return DeviceProfile(totalRamMb, soc, cores, tier)
        }
    }
}

data class Fit(
    /** Fits in memory alongside the app and the other active models. */
    val fits: Boolean,
    /** Human-readable expected speed on this device, e.g. "~12 words/s" or "2× faster than real time". */
    val speed: String,
    /** Higher is faster; used for sorting. */
    val speedScore: Double,
)

/**
 * Estimates from measured baselines on a OnePlus 13R (Snapdragon 8 Gen 3): whisper small.en at 0.48× real time,
 * Qwen2.5-3B Q4_K_M at 12.4 tokens/s. Other models and phones are scaled by work per token / per second of audio
 * and by [SocTier.factor]. They are estimates; Settings → Performance shows the measured numbers.
 */
object DeviceFit {
    private const val LLM_BASE_TPS = 12.4
    private const val LLM_BASE_PARAMS = 3.0
    private const val STT_BASE_RTF = 0.48
    private const val STT_BASE_PARAMS = 0.24

    /** Leave room for Android, the app and the other loaded models: an LLM may use about half of RAM. */
    fun fit(m: CatalogModel, d: DeviceProfile): Fit {
        val budget = when (m.category) {
            ModelCategory.LLM -> d.totalRamMb * 0.5
            ModelCategory.SPEECH -> d.totalRamMb * 0.2
            else -> d.totalRamMb * 0.1
        }
        val fits = m.ramNeededMb <= budget
        return when (m.category) {
            ModelCategory.LLM -> {
                val tps = LLM_BASE_TPS * (LLM_BASE_PARAMS / m.activeParamsB.coerceAtLeast(0.3)) * d.tier.factor
                // ~0.75 words per token for English.
                Fit(fits, "~${(tps * 0.75).roundToInt().coerceAtLeast(1)} words/s", tps)
            }
            ModelCategory.SPEECH -> {
                val rtf = STT_BASE_RTF * (m.activeParamsB / STT_BASE_PARAMS) / d.tier.factor
                val speed = if (rtf < 1) "${formatX(1 / rtf)} faster than real time" else "${formatX(rtf)} slower than real time"
                Fit(fits, speed, 1 / rtf)
            }
            ModelCategory.EMBEDDING -> {
                val perEntryMs = (8 + 40 * m.activeParamsB) / d.tier.factor
                Fit(fits, "~${perEntryMs.roundToInt()} ms per entry", 1000 / perEntryMs)
            }
            ModelCategory.VOICE -> Fit(fits, if (d.tier.factor >= 0.35) "Speaks without delay" else "Short pause before speaking", d.tier.factor)
            ModelCategory.VAD -> Fit(true, "Real time", 100.0)
        }
    }

    private fun formatX(x: Double) = if (x >= 10) "${x.roundToInt()}×" else "%.1f×".format(x).replace(".0×", "×")

    /**
     * The "Recommended for your phone" pick per category: the first rung of each ladder that fits this phone and is
     * fast enough for conversation (LLM ≥ 8 tokens/s, speech ≥ 1.5× real time). Ladders hold only open-licence
     * models whose prompt format this app has been tested with; the rest of the catalogue can still be chosen.
     */
    fun recommended(d: DeviceProfile, multilingual: Boolean = false, models: List<CatalogModel> = Catalog.models): Map<ModelCategory, CatalogModel> {
        fun pick(ids: List<String>, ok: (Fit) -> Boolean): CatalogModel? {
            val ladder = ids.mapNotNull { id -> models.firstOrNull { it.id == id } }
            return ladder.firstOrNull { m -> fit(m, d).let { it.fits && ok(it) } } ?: ladder.lastOrNull()
        }
        return buildMap {
            pick(
                if (multilingual) listOf("whisper-small", "whisper-base", "whisper-tiny") else listOf("whisper-small-en", "whisper-base-en", "whisper-tiny-en"),
            ) { it.speedScore >= 1.5 }?.let { put(ModelCategory.SPEECH, it) }
            pick(LLM_LADDER) { it.speedScore >= 8.0 }?.let { put(ModelCategory.LLM, it) }
            pick(if (multilingual) listOf("qwen3-embedding-0.6b", "bge-small-en") else listOf("bge-small-en")) { true }?.let { put(ModelCategory.EMBEDDING, it) }
            pick(listOf("piper-en_GB-northern_english_male-medium")) { true }?.let { put(ModelCategory.VOICE, it) }
            pick(listOf("silero-vad-5.1.2")) { true }?.let { put(ModelCategory.VAD, it) }
        }
    }

    /** Tested LLMs, best first. */
    val LLM_LADDER = listOf("qwen3-4b-instruct-2507", "qwen2.5-1.5b")

    enum class Sort { RECOMMENDED, SIZE, SPEED, QUALITY }

    fun sorted(list: List<CatalogModel>, d: DeviceProfile, sort: Sort, recommendedIds: Set<String>): List<CatalogModel> = when (sort) {
        Sort.RECOMMENDED -> list.sortedWith(compareByDescending<CatalogModel> { it.id in recommendedIds }.thenByDescending { fit(it, d).fits }.thenByDescending { it.quality })
        Sort.SIZE -> list.sortedBy { it.sizeBytes }
        Sort.SPEED -> list.sortedByDescending { fit(it, d).speedScore }
        Sort.QUALITY -> list.sortedWith(compareByDescending<CatalogModel> { it.quality }.thenBy { it.sizeBytes })
    }
}

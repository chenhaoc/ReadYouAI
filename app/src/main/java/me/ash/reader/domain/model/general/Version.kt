package me.ash.reader.domain.model.general

/**
 * Application version number, consisting of three fields.
 *
 * - [major]: The major version number, such as 1
 * - [minor]: The major version number, such as 2
 * - [point]: The major version number, such as 3 (if converted to a string,
 * the value is: "1.2.3")
 */
class Version(numbers: List<String>) {

    private var major: Int = 0
    private var minor: Int = 0
    private var point: Int = 0
    private var suffixLabel: String? = null
    private var suffixNumber: Int? = null

    init {
        major = numbers.getOrNull(0)?.toIntOrNull() ?: 0
        minor = numbers.getOrNull(1)?.toIntOrNull() ?: 0
        point = numbers.getOrNull(2)?.toIntOrNull() ?: 0
        suffixLabel = numbers.getOrNull(3)?.takeIf { it.isNotBlank() }?.lowercase()
        suffixNumber = numbers.getOrNull(4)?.toIntOrNull()
    }

    constructor() : this(listOf())
    constructor(string: String?) : this(parse(string))

    override fun toString(): String =
        buildString {
            append("$major.$minor.$point")
            suffixLabel?.let { label ->
                append("-")
                append(label)
                suffixNumber?.let { number ->
                    append(".")
                    append(number)
                }
            }
        }

    /**
     * Use [major], [minor], [point] for comparison.
     *
     * 1. [major] <=> [other.major]
     * 2. [minor] <=> [other.minor]
     * 3. [point] <=> [other.point]
     */
    operator fun compareTo(other: Version): Int = when {
        major > other.major -> 1
        major < other.major -> -1
        minor > other.minor -> 1
        minor < other.minor -> -1
        point > other.point -> 1
        point < other.point -> -1
        else -> compareSuffix(other)
    }

    /**
     * Returns whether this version is larger [current] version and [skip] version.
     */
    fun whetherNeedUpdate(current: Version, skip: Version): Boolean = this > current && this > skip

    private fun compareSuffix(other: Version): Int = when {
        suffixLabel == null && other.suffixLabel == null -> 0
        suffixLabel == null -> -1
        other.suffixLabel == null -> 1
        suffixLabel != other.suffixLabel -> suffixLabel!!.compareTo(other.suffixLabel!!)
        else -> (suffixNumber ?: 0).compareTo(other.suffixNumber ?: 0)
    }

    companion object {
        private val versionRegex =
            Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([A-Za-z]+)(?:\.(\d+))?)?$""")

        private fun parse(string: String?): List<String> {
            val trimmed = string?.trim().orEmpty()
            if (trimmed.isEmpty()) return emptyList()

            val match = versionRegex.matchEntire(trimmed)
            if (match == null) {
                return trimmed.split(".")
            }

            return buildList {
                add(match.groupValues[1])
                add(match.groupValues[2].ifBlank { "0" })
                add(match.groupValues[3].ifBlank { "0" })
                val suffix = match.groupValues[4].lowercase()
                if (suffix.isNotBlank()) {
                    add(suffix)
                    val suffixBuild = match.groupValues[5]
                    if (suffixBuild.isNotBlank()) {
                        add(suffixBuild)
                    }
                }
            }
        }
    }
}

fun String?.toVersion(): Version = Version(this)

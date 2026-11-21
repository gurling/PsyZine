// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import androidx.room.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.settings.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.text.Normalizer
import java.util.Locale
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit

/** Persistent identities are independent of labels and of the source export's integer IDs. */
sealed interface SubjectId {
    val key: String
    data class Catalog(val name: String) : SubjectId { override val key = "pw:$name" }
    data class Local(val name: String) : SubjectId { override val key = "local:$name" }
    data class Definition(val id: Int) : SubjectId { override val key = "custom:$id" }
    companion object {
        fun parse(value: String): SubjectId = when {
            value.startsWith("pw:") && value.length > 3 -> Catalog(value.substring(3))
            value.startsWith("local:") && value.length > 6 -> Local(value.substring(6))
            value.startsWith("custom:") -> Definition(value.substring(7).toInt().also { require(it > 0) })
            else -> throw IllegalArgumentException("Invalid subject reference.")
        }
    }
}

@Entity(indices = [Index("parentCustomUnitId"), Index("childCustomUnitId"), Index("substanceName")])
@Serializable
data class DoseComponent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val parentCustomUnitId: Int,
    val substanceName: String? = null,
    val substanceUnit: String? = null,
    val childCustomUnitId: Int? = null,
    val dose: Double? = null,
    val isEstimate: Boolean = false,
    val estimatedDoseStandardDeviation: Double? = null,
    val note: String? = null,
    @Serializable(with = InstantSerializer::class) val creationDate: Instant = Instant.now(),
    @ColumnInfo(defaultValue = "0") val isHiddenInTimeline: Boolean = false,
)

@Entity(indices = [Index(value = ["customUnitId", "administrationRoute"], unique = true)])
@Serializable
data class CustomRoaInfo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customUnitId: Int,
    val administrationRoute: AdministrationRoute,
    @Serializable(with = InstantSerializer::class) val creationDate: Instant = Instant.now(),
    val lightMinDose: Double? = null,
    val commonMinDose: Double? = null,
    val strongMinDose: Double? = null,
    val heavyMinDose: Double? = null,
    val onsetMin: Double? = null, val onsetMax: Double? = null, val onsetUnit: String? = null,
    val comeupMin: Double? = null, val comeupMax: Double? = null, val comeupUnit: String? = null,
    val peakMin: Double? = null, val peakMax: Double? = null, val peakUnit: String? = null,
    val offsetMin: Double? = null, val offsetMax: Double? = null, val offsetUnit: String? = null,
)

@Entity
@Serializable
data class SubstanceTag(
    @PrimaryKey val id: String,
    val name: String,
    /** Keep wire color identities even if this version needs a theme fallback. */
    val color: String,
    val creationTime: Long? = null,
)

@Entity(primaryKeys = ["subjectId", "tagId"], indices = [Index("tagId")], foreignKeys = [
    ForeignKey(entity = SubstanceTag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
])
@Serializable
data class SubstanceTagMembership(val subjectId: String, val tagId: String, val isPrimary: Boolean = false)

fun normalizedTagName(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

data class DefinitionWithContent(
    @Embedded val unit: CustomUnit,
    @Relation(parentColumn = "id", entityColumn = "parentCustomUnitId") val components: List<DoseComponent>,
    @Relation(parentColumn = "id", entityColumn = "customUnitId") val roaInfos: List<CustomRoaInfo>,
)

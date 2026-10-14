// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.customunits

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDose
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toPreservedString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Draft fields survive recreation, but can only be used in the generation that loaded them. */
abstract class CustomUnitEditorViewModel(protected val guard: JournalSessionGuard,
    protected val savedState: SavedStateHandle) : ViewModel() {
    private fun <T> draft(default: T) = object : ReadWriteProperty<Any?, T> {
        private var value by mutableStateOf(default)
        private var initialized = false
        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            if (!initialized) { value = savedState.get<T>("unitDraft.${property.name}") ?: default; initialized = true }
            return value
        }
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this.value = value; initialized = true; savedState["unitDraft.${property.name}"] = value
        }
    }
    var name by draft("")
    var unit by draft("")
    var unitPlural by draft("")
    var originalUnit by draft("")
    var doseText by draft("")
    var estimatedDoseDeviationText by draft("")
    var isEstimate by draft(false)
    var isArchived by draft(false)
    var note by draft("")
    var substanceName by mutableStateOf(""); protected set
    var administrationRoute by mutableStateOf(AdministrationRoute.ORAL); protected set
    var roaDose by mutableStateOf<RoaDose?>(null); protected set
    var isUnitsFieldShown by mutableStateOf(false); protected set
    var numberOfIngestionsWithThisCustomUnit by mutableStateOf<Int?>(null); protected set
    var loading by mutableStateOf(false); private set
    var ready by mutableStateOf(false); private set
    var isSaving by mutableStateOf(false); private set
    var saveError by mutableStateOf<String?>(null); private set
    var requiresReopen by mutableStateOf(false); private set
    var errorTitle by mutableStateOf("Could not save unit"); private set
    var errors by mutableStateOf<Map<CustomUnitField, String>>(emptyMap()); private set
    var validationAttempt by mutableIntStateOf(0); private set
    var usedUnitCount by mutableStateOf<Int?>(null); private set
    protected var generation: String? = null
    protected var original: CustomUnit? = null
    private var loadAction: (suspend () -> Unit)? = null
    val canSave get() = ready && !isSaving && !loading && !requiresReopen
    val dose get() = doseText.replace(',', '.').nonnegativeDoubleOrNull()
    val estimatedDoseStandardDeviation get() = estimatedDoseDeviationText.replace(',', '.').nonnegativeDoubleOrNull()
    open val currentDoseClass get() = dose?.let { roaDose?.getDoseClass(it) }
    val retainUncertainty get() = isEstimate || original?.isEstimate == isEstimate
    // Keep a legacy correction field visible through clearing and intermediate valid keystrokes.
    val showRetainedUncertainty get() = original?.takeIf { !it.isEstimate }?.estimatedDoseStandardDeviation
        ?.let { !it.isFinite() || it < 0 } == true
    fun dismissSaveError() { saveError = null }
    fun dismissUsedUnit() { usedUnitCount = null }
    private fun revalidate() { if (validationAttempt > 0) errors = inputErrors() }
    fun onChangeOfName(value: String) { name = value; revalidate() }
    fun onChangeOfUnit(value: String) {
        val automatic = unitPlural.isBlank() || unitPlural == suggestedUnitPlural(unit)
        unit = value
        if (automatic) unitPlural = suggestedUnitPlural(value)
        revalidate()
    }
    fun onChangeOfUnitPlural(value: String) { unitPlural = value }
    fun onChangeOfOriginalUnit(value: String) { originalUnit = value; revalidate() }
    fun onChangeOfDose(value: String) { doseText = value.replace(',', '.'); revalidate() }
    fun onChangeOfEstimatedDoseDeviation(value: String) { estimatedDoseDeviationText = value.replace(',', '.'); revalidate() }
    fun onChangeOfIsEstimate(value: Boolean) { isEstimate = value; revalidate() }
    fun onChangeOfIsArchived(value: Boolean) { isArchived = value }
    fun onChangeOfNote(value: String) { note = value }
    protected open fun inputErrors() = customUnitInputErrors(name, unit, originalUnit, doseText,
        estimatedDoseDeviationText, retainUncertainty)
    protected fun validate(): Boolean {
        showErrors(inputErrors())
        return errors.isEmpty()
    }
    protected fun showErrors(value: Map<CustomUnitField, String>) { errors = value; validationAttempt++ }
    protected fun load(action: suspend () -> Unit) {
        loadAction = action
        if (loading) return
        loading = true; ready = false; saveError = null; requiresReopen = false; errorTitle = "Could not load unit"
        viewModelScope.launch {
            try {
                val token = guard.readSession(savedState.get<String>("journalGeneration")) { token ->
                    action()
                    token
                }
                // A failed initial read rolls back a newly seeded generation. Persist only a committed token.
                generation = token
                savedState["journalGeneration"] = token
                ready = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { requiresReopen = e is StaleJournalSession || e is CustomUnitProblem && e.requiresReopen; saveError = customUnitFailure(e, "load this unit") }
            finally { loading = false }
        }
    }
    fun retryLoad() { loadAction?.let { load(it) } }
    protected fun operation(actionName: String, dismiss: () -> Unit, action: suspend () -> Unit) {
        if (!canSave) return
        isSaving = true; saveError = null; requiresReopen = false; usedUnitCount = null; errorTitle = "Could not $actionName"
        viewModelScope.launch {
            try { guard.withSession(generation) { action() }; dismiss() }
            catch (e: CancellationException) { throw e }
            catch (e: UsedCustomUnit) { usedUnitCount = e.count }
            catch (e: Exception) { requiresReopen = e is StaleJournalSession || e is CustomUnitProblem && e.requiresReopen; saveError = customUnitFailure(e, actionName) }
            finally { isSaving = false }
        }
    }
    protected fun populate(unit: CustomUnit) {
        if (savedState.get<Boolean>("unitDraft.initialized") == true) return
        name = unit.name; this.unit = unit.unit; unitPlural = unit.unitPlural ?: ""
        originalUnit = unit.originalUnit
        doseText = unit.dose?.let { it.toPreservedString() } ?: ""
        estimatedDoseDeviationText = unit.estimatedDoseStandardDeviation?.let { it.toPreservedString() } ?: ""
        isEstimate = unit.isEstimate; isArchived = unit.isArchived; note = unit.note
        savedState["unitDraft.initialized"] = true
    }
    protected fun candidate(source: CustomUnit) = source.copy(
        name = if (name == source.name) name else name.trim(),
        unit = if (unit == source.unit) unit else unit.trim(),
        unitPlural = if (unitPlural == (source.unitPlural ?: "")) source.unitPlural else unitPlural.trim().ifBlank { null },
        originalUnit = if (originalUnit == source.originalUnit) originalUnit else originalUnit.trim(),
        dose = dose, estimatedDoseStandardDeviation = if (retainUncertainty) estimatedDoseStandardDeviation else null,
        isEstimate = isEstimate, isArchived = isArchived, note = note)
}

// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.definitions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.*
import androidx.navigation.toRoute
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.main.navigation.graphs.ChooseDoseCustomUnitRoute
import com.isaakhanimann.journal.ui.main.navigation.graphs.FinishIngestionRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import javax.inject.Inject

@HiltViewModel
class DefinitionDoseViewModel @Inject constructor(private val entries: ExperienceRepository,
    private val guard: JournalSessionGuard, private val substances: SubstanceRepository, private val state: SavedStateHandle) : ViewModel() {
    private val route = state.toRoute<ChooseDoseCustomUnitRoute>()
    var record by mutableStateOf<DefinitionWithContent?>(null); private set
    var options by mutableStateOf<List<AdministrationRoute>>(emptyList()); private set
    var selected by mutableStateOf(state.get<String>("definitionRoute")?.let { runCatching { AdministrationRoute.valueOf(it) }.getOrNull() }); private set
    var dose by mutableStateOf(state.get<String>("definitionAmount") ?: ""); private set
    var uncertainty by mutableStateOf(state.get<String>("definitionUncertainty") ?: ""); private set
    var estimate by mutableStateOf(state.get<Boolean>("definitionEstimate") ?: false); private set
    var loading by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var fields by mutableStateOf<Map<String, String>>(emptyMap()); private set
    private var generation: String? = null
    private var fingerprint: String? = null
    init { load() }
    fun dismissError() { error = null }
    fun changeDose(value: String) { dose = value; state["definitionAmount"] = value; fields = emptyMap() }
    fun changeUncertainty(value: String) { uncertainty = value; state["definitionUncertainty"] = value; fields = emptyMap() }
    fun changeEstimate(value: Boolean) { estimate = value; state["definitionEstimate"] = value }
    fun changeRoute(value: AdministrationRoute) { selected = value; state["definitionRoute"] = value.name }
    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            try {
                substances.initialize()
                val snapshot = guard.readSession(state.get<String>("journalGeneration") ?: route.journalGeneration) { token ->
                    generation = token
                    entries.definitionContents()
                }
                val current = snapshot.find { it.unit.id == route.customUnitId } ?: error("This definition was deleted. Choose another definition.")
                record = current
                if (current.unit.substanceName == null) {
                    val hash = snapshot.definitionFingerprint(current.unit.id)
                    check(state.get<String>("definitionDoseBase")?.let { it == hash } != false) { "This definition changed. Go back and choose an amount again." }
                    fingerprint = hash; state["definitionDoseBase"] = hash; state["journalGeneration"] = generation
                    val routes = snapshot.toGraph().availableRoutes(current.unit.id) { name -> substances.getSubstance(name)?.roas?.map { it.route }.orEmpty() }
                    options = routes.ifEmpty { AdministrationRoute.entries }
                    if (selected == null && options.size == 1) changeRoute(options.single())
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not load this definition. Try again." }
            finally { loading = false }
        }
    }
    fun next(unknown: Boolean, finish: (FinishIngestionRoute) -> Unit) {
        val unit = record?.unit ?: return
        if (loading || busy) return
        fields = buildMap {
            if (selected == null) put("route", "Choose the administration route you want to record.")
            if (!unknown) {
                numericInputError(dose, "Amount", required = true)?.let { put("dose", it) }
                if (estimate) numericInputError(uncertainty, "Uncertainty")?.let { put("uncertainty", it) }
            }
        }
        if (fields.isNotEmpty()) return
        busy = true
        viewModelScope.launch {
            val quantity = DoseQuantity(if (unknown) null else dose.replace(',', '.').nonnegativeDoubleOrNull(),
                !unknown && estimate, if (!unknown && estimate) uncertainty.replace(',', '.').nonnegativeDoubleOrNull() else null)
            var destination: FinishIngestionRoute? = null
            try {
                guard.readSession(generation) {
                    val all = entries.definitionContents()
                    check(all.definitionFingerprint(unit.id) == fingerprint) { "This definition changed. Go back and choose an amount again." }
                    check(all.first { it.unit.id == unit.id }.unit.isArchived.not()) { "This definition is archived. Unarchive it before adding a new entry." }
                    all.toGraph().resolve(unit.id, quantity)
                    destination = FinishIngestionRoute(requireNotNull(selected), quantity.isEstimate, unit.unit,
                        quantity.amount, quantity.uncertainty, unit.name, unit.id, generation, definitionFingerprint = fingerprint, startAtNow = route.startAtNow)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "This amount could not be calculated. Check the amount and definition." }
            finally { busy = false }
            destination?.let(finish)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefinitionDoseDestination(legacy: @Composable () -> Unit, finish: (FinishIngestionRoute) -> Unit,
    back: () -> Unit, model: DefinitionDoseViewModel = hiltViewModel()) {
    val record = model.record
    if (!model.loading && model.error == null && record?.unit?.substanceName != null) { legacy(); return }
    Scaffold(topBar = { TopAppBar(title = { Text("Log definition") }, navigationIcon = { TextButton(onClick = back) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (model.loading) item { CircularProgressIndicator(); Text("Loading definition") }
            else if (record != null) {
                item { Text(record.unit.name, style = MaterialTheme.typography.headlineSmall) }
                item { ChoiceField("Administration route", model.selected?.displayText.orEmpty(), model.options.map { it.displayText },
                    { value -> model.options.find { it.displayText == value }?.let(model::changeRoute) }, "Choose a route")
                    model.fields["route"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                item { OutlinedTextField(model.dose, model::changeDose, Modifier.fillMaxWidth(), label = { Text("Amount (${record.unit.unit})") },
                    isError = model.fields["dose"] != null, supportingText = { model.fields["dose"]?.let { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
                item { FormSwitch("Estimated amount", model.estimate, model::changeEstimate) }
                if (model.estimate) item { OutlinedTextField(model.uncertainty, model::changeUncertainty, Modifier.fillMaxWidth(), label = { Text("Standard deviation") },
                    isError = model.fields["uncertainty"] != null, supportingText = { model.fields["uncertainty"]?.let { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
                item { Button(onClick = { model.next(false, finish) }, enabled = !model.busy && model.error == null, modifier = Modifier.fillMaxWidth()) { Text("Continue") } }
                item { OutlinedButton(onClick = { model.next(true, finish) }, enabled = !model.busy && model.error == null, modifier = Modifier.fillMaxWidth()) { Text("Log unknown dose") } }
            } else item { Text("This definition is unavailable."); TextButton(onClick = model::load) { Text("Retry") } }
        }
    }
    model.error?.let { message -> AlertDialog(onDismissRequest = model::dismissError, title = { Text("Could not use definition") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = model::dismissError) { Text("OK") } }) }
}

/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose.customunit

import androidx.compose.foundation.layout.navigationBars

import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.animation.AnimatedVisibility
import com.isaakhanimann.journal.ui.theme.LocalPsyZinePalette
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.isaakhanimann.journal.data.room.nonnegativeDoubleOrNull
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import com.isaakhanimann.journal.data.room.CustomUnitField
import com.isaakhanimann.journal.data.room.numericInputError
import com.isaakhanimann.journal.ui.tabs.settings.customunits.CustomUnitEditorStatus
import com.isaakhanimann.journal.ui.tabs.settings.customunits.add.unitFieldFocus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.DoseClass
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDose
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose.StandardDeviationExplanation
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.search.suggestion.models.justUnit
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.dose.RoaDosePreviewProvider
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.dose.RoaDoseView
import com.isaakhanimann.journal.ui.theme.horizontalPadding

@Composable
fun ChooseDoseCustomUnitScreen(
    navigateToChooseTimeAndMaybeColor: (
        administrationRoute: AdministrationRoute,
        units: String?,
        isEstimate: Boolean,
        dose: Double?,
        estimatedDoseStandardDeviation: Double?,
        substanceName: String,
        customUnitId: Int?,
        journalGeneration: String?,
        customUnitFingerprint: String?
    ) -> Unit,
    navigateToCreateCustomUnit: (route: AdministrationRoute, substanceName: String) -> Unit,
    viewModel: ChooseDoseCustomUnitViewModel = hiltViewModel(),
    navigateBack: () -> Unit = {}
) {
    CustomUnitEditorStatus(viewModel, navigateBack)
    if (!viewModel.ready) return
    val navigate: (CustomUnit, Double?, Double?, Boolean, String, String) -> Unit = { unit, dose, uncertainty, estimate, generation, fingerprint ->
        navigateToChooseTimeAndMaybeColor(unit.administrationRoute, unit.unit, estimate, dose, uncertainty,
            unit.substanceName, unit.id, generation, fingerprint)
    }
    viewModel.customUnit?.let { customUnitUnwrapped ->
        ChooseDoseCustomUnitScreen(
            customUnit = customUnitUnwrapped,
            errors = viewModel.errors,
            validationAttempt = viewModel.validationAttempt,
            enabled = viewModel.canSave,
            roaDose = viewModel.roaDose,
            doseText = viewModel.doseText,
            doseRemark = viewModel.doseRemark,
            dose = viewModel.dose,
            onChangeDoseText = viewModel::onDoseTextChange,
            estimatedDoseDeviationText = viewModel.estimatedDoseDeviationText,
            onChangeEstimatedDoseDeviationText = viewModel::onEstimatedDoseDeviationChange,
            estimatedDoseDeviation = viewModel.estimatedDoseDeviation,
            isEstimate = viewModel.isEstimate,
            onChangeIsEstimate = {
                viewModel.onChangeOfIsEstimate(it)
            },
            navigateToNext = { viewModel.next(navigate = navigate) },
            navigateToCreateCustomUnit = navigateToCreateCustomUnit,
            useUnknownDoseAndNavigate = { viewModel.next(unknown = true, navigate = navigate) },
            currentDoseClass = viewModel.currentDoseClass,
            customUnitCalculationText = viewModel.customUnitCalculationText,
        )
    }
}

@Preview
@Composable
fun ChooseDoseCustomUnitScreenPreview(
    @PreviewParameter(RoaDosePreviewProvider::class) roaDose: RoaDose,
) {
    ChooseDoseCustomUnitScreen(
        customUnit = CustomUnit(
            substanceName = "Example substance",
            administrationRoute = AdministrationRoute.ORAL,
            dose = 10.0,
            estimatedDoseStandardDeviation = null,
            isEstimate = false,
            isArchived = false,
            originalUnit = "mg",
            name = "Big Spoon",
            unit = "spoon",
            unitPlural = "spoons",
            note = "Note about custom unit dose"
        ),
        roaDose = roaDose,
        doseText = "5",
        dose = 5.0,
        onChangeDoseText = {},
        estimatedDoseDeviationText = "",
        onChangeEstimatedDoseDeviationText = {},
        estimatedDoseDeviation = null,
        doseRemark = "This is a dose remark",
        isEstimate = false,
        onChangeIsEstimate = {},
        navigateToNext = {},
        navigateToCreateCustomUnit = { _: AdministrationRoute, _: String -> },
        useUnknownDoseAndNavigate = {},
        currentDoseClass = DoseClass.THRESHOLD,
        customUnitCalculationText = "2 pills x 20 mg = 40 mg",
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseDoseCustomUnitScreen(
    customUnit: CustomUnit,
    roaDose: RoaDose?,
    doseRemark: String?,
    dose: Double?,
    doseText: String,
    onChangeDoseText: (String) -> Unit,
    estimatedDoseDeviationText: String,
    estimatedDoseDeviation: Double?,
    onChangeEstimatedDoseDeviationText: (String) -> Unit,
    isEstimate: Boolean,
    onChangeIsEstimate: (Boolean) -> Unit,
    navigateToNext: () -> Unit,
    navigateToCreateCustomUnit: (route: AdministrationRoute, substanceName: String) -> Unit,
    useUnknownDoseAndNavigate: () -> Unit,
    currentDoseClass: DoseClass?,
    customUnitCalculationText: String?,
    errors: Map<CustomUnitField, String> = emptyMap(),
    validationAttempt: Int = 0,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    var fabHeight by remember { mutableStateOf(56.dp) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(customUnit.name, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        },
        floatingActionButton = {
            run {
                ExtendedFloatingActionButton(
                    modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBars).imePadding().onSizeChanged { fabHeight = with(density) { it.height.toDp() } }
                        .then(if (enabled) Modifier else Modifier.semantics { disabled() }),
                    onClick = { if (enabled) navigateToNext() },
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next"
                        )
                    },
                    text = { Text("Next") },
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .padding(bottom = fabHeight + 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            LinearProgressIndicator(
                progress = { 0.67f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            ElevatedCard(
                modifier = Modifier
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = 4.dp
                    )
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = horizontalPadding,
                        vertical = 10.dp
                    )
                ) {
                    Text(
                        text = "${customUnit.substanceName} ${customUnit.administrationRoute.displayText}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (!doseRemark.isNullOrBlank()) {
                        Text(text = doseRemark, style = MaterialTheme.typography.bodySmall)
                    }
                    if (customUnit.note.isNotBlank()) {
                        Text("${customUnit.name}: ${customUnit.note}")
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    if (roaDose != null) {
                        RoaDoseView(roaDose = roaDose)
                    }
                    Text(
                        text = customUnitCalculationText ?: "",
                        color = currentDoseClass?.getComposeColor(LocalPsyZinePalette.current)
                            ?: Color.Unspecified,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            ElevatedCard(
                modifier = Modifier.padding(
                    horizontal = horizontalPadding,
                    vertical = 4.dp
                )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(
                        horizontal = horizontalPadding,
                        vertical = 10.dp
                    )
                ) {
                    if (roaDose != null && customUnit.dose?.let { it.isFinite() && it > 0 } == true) {
                        CustomUnitRoaDoseView(roaDose, customUnit)
                    }
                    val focusManager = LocalFocusManager.current
                    val focusRequester = remember { FocusRequester() }
                    val uncertaintyFocus = remember { FocusRequester() }
                    var doseBlurred by remember { mutableStateOf(false) }
                    var uncertaintyBlurred by remember { mutableStateOf(false) }
                    val doseError = errors[CustomUnitField.DOSE] ?: numericInputError(doseText, "Dose", true)?.takeIf { doseBlurred }
                    val uncertaintyError = errors[CustomUnitField.UNCERTAINTY] ?: numericInputError(estimatedDoseDeviationText, "Uncertainty")?.takeIf { uncertaintyBlurred }
                    LaunchedEffect(validationAttempt) {
                        if (validationAttempt > 0) {
                            withFrameNanos { }
                            if (errors.containsKey(CustomUnitField.DOSE)) focusRequester.requestFocus()
                            else if (errors.containsKey(CustomUnitField.UNCERTAINTY)) uncertaintyFocus.requestFocus()
                        }
                    }
                    val textStyle = MaterialTheme.typography.titleMedium

                    val pluralizableUnit = customUnit.getPluralizableUnit()
                    OutlinedTextField(
                        enabled = enabled,
                        value = doseText,
                        onValueChange = {
                            onChangeDoseText(
                                it.replace(
                                    oldChar = ',',
                                    newChar = '.'
                                )
                            )
                        },
                        textStyle = textStyle,
                        label = { Text("Dose") },
                        isError = doseError != null,
                        supportingText = {
                            Column {
                                Text("Amount in ${pluralizableUnit.justUnit(dose ?: 1.0)}.")
                                Text(doseError ?: "Enter an amount, or choose Log unknown dose.")
                            }
                        },
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                        }),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .unitFieldFocus(focusRequester) { doseBlurred = true }
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Switch(enabled = enabled, checked = isEstimate, onCheckedChange = onChangeIsEstimate)
                        Text("Estimate", style = MaterialTheme.typography.titleMedium)
                    }
                    AnimatedVisibility(visible = isEstimate) {
                        Column {
                            OutlinedTextField(
                                enabled = enabled,
                                value = estimatedDoseDeviationText,
                                onValueChange = {
                                    onChangeEstimatedDoseDeviationText(
                                        it.replace(
                                            oldChar = ',',
                                            newChar = '.'
                                        )
                                    )
                                },
                                textStyle = textStyle,
                                label = { Text("Uncertainty") },
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                }),
                                isError = uncertaintyError != null,
                                supportingText = {
                                    Column {
                                        Text("Standard deviation in ${pluralizableUnit.justUnit(estimatedDoseDeviation ?: 1.0)}.")
                                        uncertaintyError?.let { Text(it) }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().unitFieldFocus(uncertaintyFocus) { uncertaintyBlurred = true }
                            )
                            val mean = doseText.nonnegativeDoubleOrNull()
                            val standardDeviation = estimatedDoseDeviationText.nonnegativeDoubleOrNull()
                            val isExplanationShown = mean != null && standardDeviation != null
                            AnimatedVisibility(isExplanationShown) {
                                if (mean != null && standardDeviation != null) {
                                    StandardDeviationExplanation(
                                        mean = mean,
                                        standardDeviation = standardDeviation,
                                        unit = pluralizableUnit.justUnit(mean)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            TextButton(enabled = enabled, onClick = useUnknownDoseAndNavigate) {
                Text(text = "Log unknown dose")
            }
            TextButton(enabled = enabled, onClick = {
                navigateToCreateCustomUnit(
                    customUnit.administrationRoute,
                    customUnit.substanceName
                )
            }) {
                Text(text = "Create new unit")
            }
        }
    }
}

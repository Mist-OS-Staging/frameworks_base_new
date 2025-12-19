/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.panels.ui.compose

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.dagger.SysUISingleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@SysUISingleton
class TileSpacingConfig @Inject constructor(
    private val context: Context
) {
    private val _horizontalSpacing = MutableStateFlow(DEFAULT_HORIZONTAL_SPACING_DP)
    val horizontalSpacing: StateFlow<Int> = _horizontalSpacing

    private val _verticalSpacing = MutableStateFlow(DEFAULT_VERTICAL_SPACING_DP)
    val verticalSpacing: StateFlow<Int> = _verticalSpacing

    companion object {
        const val DEFAULT_HORIZONTAL_SPACING_DP = 25
        const val DEFAULT_VERTICAL_SPACING_DP = 21
        
        const val MIN_SPACING_DP = 4
        const val MAX_SPACING_DP = 40
    }

    init {
        loadSpacingPreferences()
    }

    private fun loadSpacingPreferences() {
        val horizontal = Settings.System.getInt(
            context.contentResolver,
            Settings.System.QS_TILE_HORIZONTAL_SPACING,
            DEFAULT_HORIZONTAL_SPACING_DP
        ).coerceIn(MIN_SPACING_DP, MAX_SPACING_DP)
        
        val vertical = Settings.System.getInt(
            context.contentResolver,
            Settings.System.QS_TILE_VERTICAL_SPACING,
            DEFAULT_VERTICAL_SPACING_DP
        ).coerceIn(MIN_SPACING_DP, MAX_SPACING_DP)
        
        _horizontalSpacing.value = horizontal
        _verticalSpacing.value = vertical
    }

    fun setHorizontalSpacing(spacingDp: Int) {
        val clamped = spacingDp.coerceIn(MIN_SPACING_DP, MAX_SPACING_DP)
        _horizontalSpacing.value = clamped
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.QS_TILE_HORIZONTAL_SPACING,
            clamped
        )
    }

    fun setVerticalSpacing(spacingDp: Int) {
        val clamped = spacingDp.coerceIn(MIN_SPACING_DP, MAX_SPACING_DP)
        _verticalSpacing.value = clamped
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.QS_TILE_VERTICAL_SPACING,
            clamped
        )
    }

    fun getHorizontalSpacingDp(): Dp = _horizontalSpacing.value.dp
    fun getVerticalSpacingDp(): Dp = _verticalSpacing.value.dp

    fun resetToDefaults() {
        setHorizontalSpacing(DEFAULT_HORIZONTAL_SPACING_DP)
        setVerticalSpacing(DEFAULT_VERTICAL_SPACING_DP)
    }
}

@Composable
fun rememberHorizontalTileSpacing(config: TileSpacingConfig): State<Dp> {
    val spacingValue = config.horizontalSpacing.collectAsState()
    return object : State<Dp> {
        override val value: Dp
            get() = spacingValue.value.dp
    }
}

@Composable
fun rememberVerticalTileSpacing(config: TileSpacingConfig): State<Dp> {
    val spacingValue = config.verticalSpacing.collectAsState()
    return object : State<Dp> {
        override val value: Dp
            get() = spacingValue.value.dp
    }
}

@Composable
fun rememberTileSpacing(config: TileSpacingConfig): Pair<State<Dp>, State<Dp>> {
    return Pair(
        rememberHorizontalTileSpacing(config),
        rememberVerticalTileSpacing(config)
    )
}

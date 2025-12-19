/*
 * Copyright (C) 2024-2025 The Lunaris AOSP
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.dagger.SysUISingleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

enum class TileShapeStyle {
    ROUNDED,
    ROUNDED_RECTANGLE
}

@SysUISingleton
class TileShapeConfig @Inject constructor(
    private val context: Context
) {
    private val _shapeStyle = MutableStateFlow(TileShapeStyle.ROUNDED)
    val shapeStyle: StateFlow<TileShapeStyle> = _shapeStyle

    companion object {
        const val ROUNDED_RADIUS_DP = 50
        const val ROUNDED_RECT_RADIUS_DP = 28
    }

    init {
        loadShapePreference()
    }

    private fun loadShapePreference() {
        val style = Settings.System.getInt(
            context.contentResolver,
            Settings.System.QS_TILE_SHAPE_STYLE,
            TileShapeStyle.ROUNDED.ordinal
        )
        _shapeStyle.value = TileShapeStyle.values().getOrElse(style) { TileShapeStyle.ROUNDED }
    }

    fun setShapeStyle(style: TileShapeStyle) {
        _shapeStyle.value = style
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.QS_TILE_SHAPE_STYLE,
            style.ordinal
        )
    }

    fun getCornerRadius(tileWidth: Dp, tileHeight: Dp): Dp {
        return when (_shapeStyle.value) {
            TileShapeStyle.ROUNDED -> tileWidth / 2
            TileShapeStyle.ROUNDED_RECTANGLE -> ROUNDED_RECT_RADIUS_DP.dp
        }
    }

    fun getIconTileShape(tileSize: Dp): RoundedCornerShape {
        val radius = when (_shapeStyle.value) {
            TileShapeStyle.ROUNDED -> tileSize / 2
            TileShapeStyle.ROUNDED_RECTANGLE -> ROUNDED_RECT_RADIUS_DP.dp
        }
        return RoundedCornerShape(radius)
    }

    fun getLargeTileShape(tileWidth: Dp, tileHeight: Dp): RoundedCornerShape {
        val radius = when (_shapeStyle.value) {
            TileShapeStyle.ROUNDED -> tileHeight / 2
            TileShapeStyle.ROUNDED_RECTANGLE -> ROUNDED_RECT_RADIUS_DP.dp
        }
        return RoundedCornerShape(radius)
    }
}

@Composable
fun rememberTileShapeConfig(config: TileShapeConfig): State<TileShapeStyle> {
    return config.shapeStyle.collectAsState()
}

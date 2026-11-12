package com.carlog.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii from carlog-tokens.json `radius` and `size.button*.radius` (dp). */
object CarLogRadius {
    val tag = 36.dp
    val panel = 28.dp
    val row = 24.dp
    val sheet = 40.dp
    val buttonL = 32.dp
    val buttonM = 24.dp
    val buttonS = 20.dp
    /** Category badge, input fields use [row]. */
    val badge = 22.dp
    val tabs = 28.dp
    val tab = 22.dp
}

object CarLogShapes {
    val tag = RoundedCornerShape(CarLogRadius.tag)
    val panel = RoundedCornerShape(CarLogRadius.panel)
    val row = RoundedCornerShape(CarLogRadius.row)
    val sheet = RoundedCornerShape(CarLogRadius.sheet)
    val buttonL = RoundedCornerShape(CarLogRadius.buttonL)
    val buttonM = RoundedCornerShape(CarLogRadius.buttonM)
    val buttonS = RoundedCornerShape(CarLogRadius.buttonS)
    val badge = RoundedCornerShape(CarLogRadius.badge)
    val input = RoundedCornerShape(CarLogRadius.row)
    /** radius.chip = 999: chips, driver switch, count pill. */
    val chip = CircleShape
}

/** Material 3 shape mapping (dialogs use extraLarge, buttons use full by default; CarLogButton sets its own). */
val CarLogMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(CarLogRadius.buttonS),
    small = RoundedCornerShape(CarLogRadius.row),
    medium = RoundedCornerShape(CarLogRadius.panel),
    large = RoundedCornerShape(CarLogRadius.tag),
    extraLarge = RoundedCornerShape(CarLogRadius.sheet),
)

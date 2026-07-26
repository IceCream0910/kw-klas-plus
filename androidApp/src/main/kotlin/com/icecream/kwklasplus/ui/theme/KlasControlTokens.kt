package com.icecream.kwklasplus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.icecream.kwklasplus.R

val KlasControlShape = RoundedCornerShape(16.dp)
val KlasButtonHeight = 50.dp

@Composable
fun klasInverseButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.inversePrimary,
    contentColor = colorResource(R.color.klas_inverse_button_content),
)

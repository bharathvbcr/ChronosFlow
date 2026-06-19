package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.ChronosFlow.VBCR.core.ui.R

@Composable
fun ChronosFlowLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = "ChronosFlow logo"
) {
    Image(
        painter = painterResource(R.drawable.ic_chronosflow_logo),
        contentDescription = contentDescription,
        modifier = modifier
    )
}

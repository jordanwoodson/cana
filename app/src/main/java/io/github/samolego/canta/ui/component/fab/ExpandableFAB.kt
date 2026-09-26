package io.github.samolego.canta.ui.component.fab

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import io.github.samolego.canta.R

@Composable
fun ExpandableFAB(
    onBottomClick: () -> Unit,
    onTopClick: () -> Unit,
    modifier: Modifier = Modifier,
    topIcon: ImageVector = Icons.Default.Download,
    bottomIcon: ImageVector = Icons.Default.Add,
    topLabel: String = stringResource(R.string.import_action),
    bottomLabel: String = stringResource(R.string.create_action)
) {
    var isExpanded by remember { mutableStateOf(false) }

    val rotation by
            animateFloatAsState(
                    targetValue = if (isExpanded) 90f else 0f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "rotation"
            )

    val spacingAnimation by
            animateDpAsState(
                    targetValue = if (isExpanded) 8.dp else 0.dp,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "spacing"
            )

    Box(
        modifier = modifier.clip(
            shape = RoundedCornerShape(16.dp),
        ),
        contentAlignment = Alignment.BottomEnd,
        ) {
        Column(
                modifier = Modifier.background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacingAnimation)
        ) {
            // Import button
            if (isExpanded) {
                TextButton(onClick = { onTopClick(); isExpanded = false }) {
                    Icon(topIcon, null)
                    Spacer(Modifier.width(8.dp))
                    Text(topLabel)
                }
                TextButton(onClick = { onBottomClick(); isExpanded = false }) {
                    Icon(bottomIcon, null)
                    Spacer(Modifier.width(8.dp))
                    Text(bottomLabel)
                }
            }
            // Main FAB
            FloatingActionButton(
                modifier = Modifier.rotate(rotation),
                    onClick = {
                        isExpanded = !isExpanded
                    },
            ) {
                Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.more_actions),
                )
            }
        }
    }
}

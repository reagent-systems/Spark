package com.example.spark.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val animationDelay = index * 200
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = animationDelay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
}

@Composable
fun FastTypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "fast_typing")
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val animationDelay = index * 80 // Much faster staggered animation
            
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 240, // Faster animation
                        delayMillis = animationDelay,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "fast_dot_scale_$index"
            )
            
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 240,
                        delayMillis = animationDelay
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "fast_dot_alpha_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(scale)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
} 
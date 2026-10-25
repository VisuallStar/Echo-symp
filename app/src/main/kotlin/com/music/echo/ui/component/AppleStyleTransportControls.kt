package echo.music.iad1tya.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import echo.music.iad1tya.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Apple Music style playback transport controls.
 *
 * Displays a single horizontal row with Previous, Play/Pause, and Next controls.
 * Features solid, filled, rounded-corner glyphs in white, smooth morphing animations,
 * spring scale press feedback, directional tap nudges, continuous hold-to-scrub,
 * and haptic feedback.
 */
@Composable
fun AppleStyleTransportControls(
  isPlaying: Boolean,
  onPlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onSeekBackward: () -> Unit,
  onSeekForward: () -> Unit,
  modifier: Modifier = Modifier,
  canSkipPrevious: Boolean = true,
  canSkipNext: Boolean = true,
  tint: Color = Color.White,
) {
  Row(
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.fillMaxWidth(),
  ) {
    // Previous Track / Rewind
    AppleTransportButton(
      iconRes = R.drawable.ic_apple_previous,
      contentDescription = "Previous track",
      enabled = canSkipPrevious,
      tint = tint,
      iconSize = 42.dp,
      touchTargetSize = 56.dp,
      nudgeDirection = -1f,
      onClick = onPrevious,
      onLongPressSeek = onSeekBackward,
    )

    // Play / Pause in the center
    ApplePlayPauseButton(
      isPlaying = isPlaying,
      tint = tint,
      onClick = onPlayPause,
    )

    // Next Track / Fast Forward
    AppleTransportButton(
      iconRes = R.drawable.ic_apple_next,
      contentDescription = "Next track",
      enabled = canSkipNext,
      tint = tint,
      iconSize = 42.dp,
      touchTargetSize = 56.dp,
      nudgeDirection = 1f,
      onClick = onNext,
      onLongPressSeek = onSeekForward,
    )
  }
}

@Composable
private fun ApplePlayPauseButton(
  isPlaying: Boolean,
  tint: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val haptic = LocalHapticFeedback.current
  var isPressed by remember { mutableStateOf(false) }

  val scale by
    animateFloatAsState(
      targetValue = if (isPressed) 0.85f else 1f,
      animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
      label = "playPauseScale",
    )

  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .sizeIn(minWidth = 72.dp, minHeight = 72.dp)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .semantics {
          role = Role.Button
          contentDescription = if (isPlaying) "Pause" else "Play"
        }
        .pointerInput(Unit) {
          awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            isPressed = true
            val up = waitForUpOrCancellation()
            isPressed = false
            if (up != null) {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              onClick()
            }
          }
        },
  ) {
    AnimatedContent(
      targetState = isPlaying,
      transitionSpec = {
        (scaleIn(initialScale = 0.8f, animationSpec = tween(200, easing = LinearOutSlowInEasing)) +
            fadeIn(animationSpec = tween(200)))
          .togetherWith(
            scaleOut(
              targetScale = 0.8f,
              animationSpec = tween(200, easing = FastOutLinearInEasing),
            ) + fadeOut(animationSpec = tween(200))
          )
      },
      label = "PlayPauseMorph",
    ) { playing ->
      Image(
        painter =
          painterResource(
            if (playing) R.drawable.ic_apple_pause else R.drawable.ic_apple_play
          ),
        contentDescription = if (playing) "Pause" else "Play",
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(60.dp),
      )
    }
  }
}

@Composable
private fun AppleTransportButton(
  iconRes: Int,
  contentDescription: String,
  enabled: Boolean,
  tint: Color,
  iconSize: Dp,
  touchTargetSize: Dp,
  nudgeDirection: Float,
  onClick: () -> Unit,
  onLongPressSeek: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val haptic = LocalHapticFeedback.current
  var isPressed by remember { mutableStateOf(false) }
  val nudgeOffset = remember { Animatable(0f) }

  val scale by
    animateFloatAsState(
      targetValue = if (isPressed) 0.85f else 1f,
      animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
      label = "transportButtonScale",
    )

  val gestureModifier =
    if (enabled) {
      Modifier.pointerInput(enabled) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false)
          isPressed = true
          var isScrubbing = false
          var scrubJob: Job? = null

          if (onLongPressSeek != null) {
            scrubJob =
              scope.launch {
                delay(400) // Hold-to-scrub threshold
                isScrubbing = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                while (isActive) {
                  onLongPressSeek()
                  delay(300) // Continuous seek interval
                }
              }
          }

          val up = waitForUpOrCancellation()
          scrubJob?.cancel()
          isPressed = false

          if (up != null && !isScrubbing) {
            // Single tap: perform haptic, nudge, and skip
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (nudgeDirection != 0f) {
              scope.launch {
                nudgeOffset.animateTo(
                  nudgeDirection * 4f,
                  tween(70, easing = LinearOutSlowInEasing),
                )
                nudgeOffset.animateTo(
                  0f,
                  spring(dampingRatio = 0.6f, stiffness = 400f),
                )
              }
            }
            onClick()
          }
        }
      }
    } else {
      Modifier
    }

  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .sizeIn(minWidth = touchTargetSize, minHeight = touchTargetSize)
        .alpha(if (enabled) 1f else 0.4f)
        .offset(x = nudgeOffset.value.dp)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .semantics {
          this.role = Role.Button
          this.contentDescription = contentDescription
        }
        .then(gestureModifier),
  ) {
    Image(
      painter = painterResource(iconRes),
      contentDescription = contentDescription,
      colorFilter = ColorFilter.tint(tint),
      modifier = Modifier.size(iconSize),
    )
  }
}

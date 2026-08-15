package com.bunny.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bunny.data.remote.socket.ConnectionState
import com.bunny.ui.theme.AppTheme
import com.bunny.ui.theme.BunnyAccent
import com.bunny.ui.theme.WordmarkStyle

// Official Bunny releases (APK) URL
const val BUNNY_RELEASES_URL = "https://github.com/Pankeque/Bunny/releases"

fun brandGradientColors(theme: AppTheme): List<Color> = when (theme) {
    AppTheme.DARK -> listOf(BunnyAccent, Color(0xFFB8541A))
    AppTheme.LIGHT -> listOf(Color(0xFFC75A1A), Color(0xFFE8702A))
    AppTheme.YELLOW -> listOf(Color(0xFFFFB24D), Color(0xFFE8702A))
}

fun brandGradientBrush(theme: AppTheme): Brush =
    Brush.linearGradient(brandGradientColors(theme))

// Subtle press-scale haptic-style feedback (visual touch feedback)
@Composable
fun Modifier.pressScale(pressedScale: Float = 0.97f): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "pressScale"
    )
    return this.scale(scale)
}

// Geometric bunny logomark drawn on Canvas (no external assets)
@Composable
fun BunnyLogoMark(
    size: Dp = 28.dp,
    background: Color = BunnyAccent,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(size * 0.72f)
        ) {
            val w = this.size.width
            val cx = w / 2f
            val cy = w / 2f + w * 0.04f
            val faceR = w * 0.30f
            val earW = w * 0.17f
            val earH = w * 0.40f
            val earTop = cy - faceR - earH + w * 0.10f

            // orelhas
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx - w * 0.21f, earTop),
                size = Size(earW, earH),
                cornerRadius = CornerRadius(earW / 2f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx + w * 0.04f, earTop),
                size = Size(earW, earH),
                cornerRadius = CornerRadius(earW / 2f)
            )
            // rosto
            drawCircle(color = Color.White, radius = faceR, center = Offset(cx, cy))
            // olhos
            drawCircle(color = background, radius = w * 0.038f, center = Offset(cx - w * 0.105f, cy - w * 0.02f))
            drawCircle(color = background, radius = w * 0.038f, center = Offset(cx + w * 0.105f, cy - w * 0.02f))
            // focinho
            drawCircle(color = background, radius = w * 0.024f, center = Offset(cx, cy + w * 0.10f))
        }
    }
}

// "Bunny" wordmark in Playfair Display italic
@Composable
fun BunnyWordmark(
    text: String = "Bunny",
    color: Color = Color.White,
    fontSize: androidx.compose.ui.unit.TextUnit = 26.sp,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = WordmarkStyle.copy(fontSize = fontSize),
        color = color,
        modifier = modifier
    )
}

// Opens the APK releases page
fun openBunnyReleases(context: android.content.Context) {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(BUNNY_RELEASES_URL))
    )
}

// "Download APK" button — main CTA of the platform
@Composable
fun DownloadApkButton(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "downloadScale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(BunnyAccent)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(
                    color = Color.White.copy(alpha = 0.25f)
                )
            ) { openBunnyReleases(context) }
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = "Download APK",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            if (!compact) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Download APK",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun GradientLogo(theme: AppTheme, size: Dp, icon: ImageVector, modifier: Modifier = Modifier) {
    BunnyLogoMark(size = size, background = brandGradientColors(theme).first(), modifier = modifier)
}

@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    text: String,
    theme: AppTheme = AppTheme.DARK
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "accentButtonScale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(BunnyAccent)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(
                    color = Color.White.copy(alpha = 0.35f)
                ),
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun UserAvatar(
    imageUrl: String?,
    username: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = username.take(1).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun DialogTitleText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun ConnectionDot(state: ConnectionState, modifier: Modifier = Modifier) {
    val color = when (state) {
        is ConnectionState.Connected -> Color(0xFF3FB950)
        is ConnectionState.Reconnecting -> Color(0xFFFFB24D)
        else -> Color(0xFFFF6B6B)
    }
    val alpha by animateFloatAsState(
        targetValue = if (state is ConnectionState.Connected) 1f else 0.45f,
        animationSpec = tween(durationMillis = 400),
        label = "connectionDotAlpha"
    )
    Box(
        modifier = modifier
            .size(10.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}

// Online/offline indicator for members
@Composable
fun PresenceDot(online: Boolean, modifier: Modifier = Modifier) {
    val color = if (online) Color(0xFF3FB950) else Color(0xFF55555C)
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun UnreadDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(BunnyAccent)
    )
}

// Subtle system message (member join/leave, notices)
@Composable
fun SystemMessage(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )
    }
}

enum class MessageStatus { Sending, Delivered, Failed }

@Composable
fun MessageStatusIcon(status: MessageStatus, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (status) {
        MessageStatus.Sending -> {
            Icon(
                imageVector = Icons.Outlined.HourglassEmpty,
                contentDescription = "Enviando",
                modifier = modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        MessageStatus.Delivered -> {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Enviado",
                modifier = modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        MessageStatus.Failed -> {
            Box(
                modifier = modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRetry),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = "Failed, tap to resend",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val transition = rememberInfiniteTransition(label = "typingDot$index")
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 700),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 200)
                ),
                label = "typingDotAlpha$index"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            if (index < 2) {
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

@Composable
fun BreathingGradientBackground(
    theme: AppTheme,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "breathing")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breathingProgress"
    )
    val accent = brandGradientColors(theme).first()
    val base = MaterialTheme.colorScheme.background
    val endOffset = 1000f * (0.4f + progress * 0.6f)
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    accent.copy(alpha = 0.06f),
                    base,
                    accent.copy(alpha = 0.03f)
                ),
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(endOffset, endOffset * 0.55f)
            )
        )
    ) {
        content()
    }
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, cornerRadius: Dp = 12.dp) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerX"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    start = androidx.compose.ui.geometry.Offset(translateX - 200f, 0f),
                    end = androidx.compose.ui.geometry.Offset(translateX, 200f)
                )
            )
    )
}

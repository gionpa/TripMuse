package com.tripmuse.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripmuse.data.model.ChatMessage
import com.tripmuse.data.model.ChatUser
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** 캐릭터 색 — 멤버 index로 순환 (채팅 액센트 계열 + 파스텔 확장) */
private val CHARACTER_COLORS = listOf(
    Color(0xFFF0A02E), Color(0xFF26B49C), Color(0xFF5B7FFF), Color(0xFFF26D5F),
    Color(0xFF8E6FF7), Color(0xFFE86AA6), Color(0xFF6BBF59), Color(0xFF00A9C7),
)
private val HAIR_COLORS = listOf(
    Color(0xFF3B2C24), Color(0xFF5A3A22), Color(0xFF1F1B18), Color(0xFF6E4B2A),
)
private val SKIN = Color(0xFFFFD9B0)

/**
 * 채팅방 상단의 2D 메타버스 스테이지. 커피샵 안에 참여자 캐릭터를 세우고,
 * 최근 발화자의 캐릭터가 점프하며 말풍선을 띄운다.
 */
@Composable
fun MetaverseStage(
    members: List<ChatUser>,
    currentUserId: Long,
    latestMessage: ChatMessage?,
    stageHeight: Dp,
    memberEmotions: Map<Long, Emotion> = emptyMap(),
    onMyCharacterTap: (Offset) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()

    // idle: 캐릭터가 숨쉬듯 아주 느리게 위아래로. 배경은 이 값을 읽지 않아 다시 그려지지 않는다.
    val infinite = rememberInfiniteTransition(label = "idle")
    val clock by infinite.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "clock"
    )

    // 발화 트리거: 최신 메시지가 바뀌면 그 발화자를 점프시키고 말풍선을 잠깐 띄운다
    var speakerId by remember { mutableStateOf<Long?>(null) }
    var speech by remember { mutableStateOf<String?>(null) }
    val jump = remember { Animatable(0f) }
    LaunchedEffect(latestMessage?.id) {
        val m = latestMessage ?: return@LaunchedEffect
        if (m.isSystem) return@LaunchedEffect
        speakerId = m.senderId
        speech = m.content.trim().ifBlank { null }?.let { if (it.length > 18) it.take(17) + "…" else it }
            ?: when {
                m.isImage -> "사진 📷"
                m.isVideo -> "동영상 🎬"
                else -> "…"
            }
        jump.snapTo(0f)
        jump.animateTo(1f, tween(520))
        delay(2600)
        speech = null
    }

    // 캐릭터 배치 계산 (draw와 터치 hit-test가 공유)
    fun layout(width: Float, height: Float): Triple<Float, Float, Float> {
        val n = members.size.coerceAtLeast(1)
        val marginX = width * 0.16f
        val usable = width - marginX * 2
        val groundY = height * 0.78f
        val charH = (height * 0.5f).coerceAtMost(usable / n * 1.5f)
        return Triple(marginX, groundY, charH)
    }
    fun centerX(index: Int, width: Float, marginX: Float): Float {
        val n = members.size
        val usable = width - marginX * 2
        return if (n <= 1) width / 2f else marginX + usable * index / (n - 1)
    }

    Box(modifier.clipToBounds()) {
        val canvasMod = Modifier.fillMaxWidth().height(stageHeight + 14.dp)

        // 배경(커피샵)은 정적이므로 레이어로 캐시한다 → idle/발화마다 재드로우되지 않아 CPU를 아낀다.
        Spacer(canvasMod.drawWithCache { onDrawBehind { drawCafe(measurer) } })

        // 캐릭터 레이어만 애니메이션 값을 읽어 재드로우된다.
        Canvas(
            canvasMod.pointerInput(members, currentUserId) {
                detectTapGestures { tap ->
                    val myIndex = members.indexOfFirst { it.id == currentUserId }
                    if (myIndex < 0) return@detectTapGestures
                    val (marginX, groundY, charH) = layout(size.width.toFloat(), size.height.toFloat())
                    val cx = centerX(myIndex, size.width.toFloat(), marginX)
                    val bodyW = charH * 0.42f
                    if (tap.x in (cx - bodyW)..(cx + bodyW) && tap.y in (groundY - charH)..(groundY + charH * 0.15f)) {
                        onMyCharacterTap(Offset(cx, groundY - charH))
                    }
                }
            }
        ) {
            if (members.isEmpty()) return@Canvas
            val (marginX, groundY, charH) = layout(size.width, size.height)
            members.forEachIndexed { i, member ->
                val cx = centerX(i, size.width, marginX)
                val isMe = member.id == currentUserId
                val emotion = memberEmotions[member.id]
                val bob = sin(clock + i * 1.3f) * (charH * 0.02f)
                val speaking = member.id == speakerId && speech != null
                val jumpY = if (speaking) sin(jump.value * PI.toFloat()) * (charH * 0.16f) else 0f
                drawStageCharacter(
                    cx0 = cx, groundY0 = groundY - bob - jumpY, height = charH,
                    style = styleForKey(member.characterStyle, i),
                    speaking = speaking, jump = if (speaking) jump.value else 0f,
                    emotion = emotion, emoT = clock / (2 * PI.toFloat()),
                    isMe = isMe, name = member.nickname, measurer = measurer
                )
                if (speaking) {
                    drawSpeechBubble(measurer, speech!!, cx, groundY - charH - jumpY - size.height * 0.02f)
                }
            }
        }
    }
}

// ---------------- 배경: 커피샵 ----------------

private fun DrawScope.drawCafe(measurer: TextMeasurer) {
    val w = size.width
    val h = size.height

    // 경계 픽셀 갭으로 초기화 안 된 GPU 메모리가 비치지 않게, 전체를 먼저 불투명하게 채운다
    drawRect(color = Color(0xFFB98A5E), size = size)
    // 벽 (따뜻한 크림 그라데이션)
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFF6E7D2), Color(0xFFEAD3B4)), endY = h * 0.72f),
        size = Size(w, h * 0.72f)
    )
    // 바닥 (나무 톤) — 하단으로 살짝 넘치게 그려 경계 갭을 없앤다 (Canvas가 clip)
    drawRect(color = Color(0xFFB98A5E), topLeft = Offset(0f, h * 0.72f), size = Size(w, h * 0.30f))
    drawRect(color = Color(0x22000000), topLeft = Offset(0f, h * 0.72f), size = Size(w, h * 0.02f))
    // 바닥 널 이음선
    var fx = w * 0.06f
    while (fx < w) {
        drawLine(Color(0x22704A2E), Offset(fx, h * 0.74f), Offset(fx - w * 0.03f, h), strokeWidth = 1.5f)
        fx += w * 0.12f
    }

    // 창문 (하늘 + 창틀) — 왼쪽
    val winL = Rect(w * 0.05f, h * 0.12f, w * 0.34f, h * 0.52f)
    drawRoundRectC(Color(0xFFCDEBF7), winL, 8f)
    drawRoundRectC(Color(0xFFBFE3F2), Rect(winL.left, winL.top, winL.right, winL.center.y), 8f)
    drawRectStroke(Color(0xFF8A6A4A), winL, 5f)
    drawLine(Color(0xFF8A6A4A), Offset(winL.center.x, winL.top), Offset(winL.center.x, winL.bottom), 4f)
    drawLine(Color(0xFF8A6A4A), Offset(winL.left, winL.center.y), Offset(winL.right, winL.center.y), 4f)
    // 창밖 나무 실루엣
    drawCircle(Color(0x559CC98A), winL.height * 0.16f, Offset(winL.left + winL.width * 0.3f, winL.top + winL.height * 0.35f))
    drawCircle(Color(0x559CC98A), winL.height * 0.20f, Offset(winL.left + winL.width * 0.68f, winL.top + winL.height * 0.4f))

    // 메뉴판 (오른쪽 벽)
    val menu = Rect(w * 0.62f, h * 0.10f, w * 0.90f, h * 0.40f)
    drawRoundRectC(Color(0xFF3A2E28), menu, 6f)
    for (r in 0..3) {
        val ly = menu.top + menu.height * (0.22f + r * 0.2f)
        drawLine(Color(0x88E8D7B0), Offset(menu.left + menu.width * 0.12f, ly), Offset(menu.right - menu.width * (0.2f + r * 0.08f), ly), 3f)
    }
    // COFFEE 글자
    measurer.let {
        val ts = TextStyle(color = Color(0xFFF3E4C4), fontSize = (h * 0.05f).toSpFallback(), fontWeight = FontWeight.Bold)
        val res = it.measure("COFFEE", ts)
        drawText(res, topLeft = Offset(menu.center.x - res.size.width / 2, menu.top + menu.height * 0.04f))
    }

    // 펜던트 조명 3개
    for (k in 0..2) {
        val lx = w * (0.28f + k * 0.22f)
        drawLine(Color(0xFF5B4636), Offset(lx, 0f), Offset(lx, h * 0.14f), 2.5f)
        val shade = Path().apply {
            moveTo(lx - w * 0.028f, h * 0.14f); lineTo(lx + w * 0.028f, h * 0.14f)
            lineTo(lx + w * 0.016f, h * 0.10f); lineTo(lx - w * 0.016f, h * 0.10f); close()
        }
        drawPath(shade, Color(0xFF2E2622))
        drawCircle(Color(0x66FFE9A8), w * 0.02f, Offset(lx, h * 0.155f))
        drawCircle(Color(0xFFFFF3C4), w * 0.009f, Offset(lx, h * 0.15f))
    }

    // 카운터 (오른쪽 하단) + 에스프레소 머신
    val counter = Rect(w * 0.60f, h * 0.56f, w * 1.0f, h * 0.74f)
    drawRoundRectC(Color(0xFF7C4A2A), counter, 6f)
    drawRect(Color(0xFF6A3E22), topLeft = Offset(counter.left, counter.top), size = Size(counter.width, h * 0.02f))
    val machine = Rect(w * 0.66f, h * 0.44f, w * 0.80f, h * 0.56f)
    drawRoundRectC(Color(0xFFB0B6BD), machine, 5f)
    drawRoundRectC(Color(0xFF7E868E), Rect(machine.left, machine.top, machine.right, machine.top + machine.height * 0.35f), 5f)
    drawCircle(Color(0xFF3A2E28), machine.width * 0.12f, Offset(machine.center.x, machine.bottom - machine.height * 0.2f))
    // 컵 몇 개
    for (c in 0..2) {
        val cupX = w * (0.84f + c * 0.045f)
        drawRoundRectC(Color(0xFFFFFFFF), Rect(cupX, h * 0.50f, cupX + w * 0.03f, h * 0.55f), 3f)
    }

    // 화분 (왼쪽 하단)
    val potX = w * 0.06f
    drawRoundRectC(Color(0xFFC4703E), Rect(potX, h * 0.66f, potX + w * 0.09f, h * 0.74f), 4f)
    drawCircle(Color(0xFF5FA05A), w * 0.05f, Offset(potX + w * 0.045f, h * 0.60f))
    drawCircle(Color(0xFF6FB566), w * 0.035f, Offset(potX + w * 0.02f, h * 0.62f))
    drawCircle(Color(0xFF6FB566), w * 0.032f, Offset(potX + w * 0.075f, h * 0.63f))
}

// ---------------- 감정 ----------------

enum class Emotion(val label: String, val emoji: String) {
    ANGRY("화남", "💢"),
    EXCITED("신남", "✨"),
    LAUGH("박장대소", "😂"),
    SAD("슬픔", "💧"),
    DUMBFOUNDED("어이없음", "💦")
}

// ---------------- 캐릭터 ----------------

private fun DrawScope.drawStageCharacter(
    cx0: Float, groundY0: Float, height: Float,
    style: CharacterStyle,
    speaking: Boolean, jump: Float,
    emotion: Emotion?, emoT: Float,
    isMe: Boolean, name: String, measurer: TextMeasurer,
    showName: Boolean = true
) {
    val headR = height * 0.20f
    val bodyW = height * 0.42f
    val bodyH = height * 0.42f
    val bodyColor = style.bodyColor

    // 감정별 몸 움직임 (좌우 부들·폴짝·들썩·처짐)
    var cx = cx0
    var groundY = groundY0
    when (emotion) {
        Emotion.ANGRY -> cx += sin(emoT * 46f) * height * 0.018f
        Emotion.EXCITED -> groundY -= kotlin.math.abs(sin(emoT * PI.toFloat() * 5)) * height * 0.14f
        Emotion.LAUGH -> groundY -= kotlin.math.abs(sin(emoT * PI.toFloat() * 8)) * height * 0.05f
        Emotion.SAD -> groundY += height * 0.045f
        Emotion.DUMBFOUNDED -> cx += sin(emoT * 5f) * height * 0.012f
        null -> {}
    }
    val headCy = groundY - bodyH - headR * 0.9f

    // 바닥 그림자 (원래 발 위치 기준)
    drawOval(Color(0x33000000), topLeft = Offset(cx - bodyW * 0.55f, groundY0 - height * 0.03f), size = Size(bodyW * 1.1f, height * 0.07f))

    // 발화/감정 강조 링
    if (speaking || emotion != null) {
        val ring = (if (emotion == Emotion.ANGRY) Color(0xFFF24236) else bodyColor).copy(alpha = 0.16f)
        drawCircle(ring, height * 0.34f, Offset(cx, groundY - bodyH * 0.5f))
    }

    // 몸통
    drawRoundRectC(bodyColor, Rect(cx - bodyW / 2, groundY - bodyH, cx + bodyW / 2, groundY), bodyW * 0.32f)
    drawRoundRectC(bodyColor.lighten(0.12f), Rect(cx - bodyW / 2, groundY - bodyH, cx - bodyW * 0.15f, groundY), bodyW * 0.3f)

    // 팔 — 감정/발화별 포즈
    val armY = groundY - bodyH * 0.72f
    val armColor = bodyColor.darken(0.1f)
    val armW = height * 0.09f
    val cap = androidx.compose.ui.graphics.StrokeCap.Round
    val armsUp = emotion == Emotion.EXCITED || emotion == Emotion.LAUGH
    if (armsUp) {
        // 양팔 번쩍
        drawLine(armColor, Offset(cx - bodyW * 0.5f, armY), Offset(cx - bodyW * 0.72f, armY - bodyH * 0.34f), armW, cap = cap)
        drawLine(armColor, Offset(cx + bodyW * 0.5f, armY), Offset(cx + bodyW * 0.72f, armY - bodyH * 0.34f), armW, cap = cap)
    } else if (emotion == Emotion.SAD) {
        // 양팔 축 처짐
        drawLine(armColor, Offset(cx - bodyW * 0.5f, armY), Offset(cx - bodyW * 0.56f, armY + bodyH * 0.4f), armW, cap = cap)
        drawLine(armColor, Offset(cx + bodyW * 0.5f, armY), Offset(cx + bodyW * 0.56f, armY + bodyH * 0.4f), armW, cap = cap)
    } else {
        val raise = if (speaking) jump * bodyH * 0.5f else 0f
        drawLine(armColor, Offset(cx - bodyW * 0.5f, armY), Offset(cx - bodyW * 0.66f, armY + bodyH * 0.28f), armW, cap = cap)
        drawLine(armColor, Offset(cx + bodyW * 0.5f, armY), Offset(cx + bodyW * 0.62f, armY + bodyH * 0.24f - raise), armW, cap = cap)
    }

    // 머리 + 헤어
    drawCircle(SKIN, headR, Offset(cx, headCy))
    drawHair(style.hair, cx, headCy, headR, style.hairColor)

    // 표정
    val eyeY = headCy + headR * 0.05f
    val eyeDx = headR * 0.38f
    val ink = Color(0xFF2A2320)
    val mouthColor = Color(0xFF7A3B34)
    fun eyeDot(r: Float = 0.12f) {
        drawCircle(ink, headR * r, Offset(cx - eyeDx, eyeY)); drawCircle(ink, headR * r, Offset(cx + eyeDx, eyeY))
    }
    when (emotion) {
        Emotion.ANGRY -> {
            drawCircle(Color(0x33F24236), headR, Offset(cx, headCy))            // 붉으락
            eyeDot(0.13f)
            // 찡그린 눈썹 \  /
            drawLine(ink, Offset(cx - eyeDx - headR * 0.2f, eyeY - headR * 0.42f), Offset(cx - eyeDx + headR * 0.18f, eyeY - headR * 0.22f), 3f, cap = cap)
            drawLine(ink, Offset(cx + eyeDx + headR * 0.2f, eyeY - headR * 0.42f), Offset(cx + eyeDx - headR * 0.18f, eyeY - headR * 0.22f), 3f, cap = cap)
            drawArcC(mouthColor, Rect(cx - headR * 0.24f, eyeY + headR * 0.5f, cx + headR * 0.24f, eyeY + headR * 0.8f), 200f, 140f, stroke = 3f) // 찡그린 입
        }
        Emotion.EXCITED -> {
            drawCircle(ink, headR * 0.15f, Offset(cx - eyeDx, eyeY)); drawCircle(ink, headR * 0.15f, Offset(cx + eyeDx, eyeY))
            drawCircle(Color.White, headR * 0.05f, Offset(cx - eyeDx + headR * 0.05f, eyeY - headR * 0.05f))
            drawCircle(Color.White, headR * 0.05f, Offset(cx + eyeDx + headR * 0.05f, eyeY - headR * 0.05f))
            drawArcC(mouthColor, Rect(cx - headR * 0.3f, eyeY + headR * 0.1f, cx + headR * 0.3f, eyeY + headR * 0.7f), 0f, 180f) // 활짝
        }
        Emotion.LAUGH -> {
            // 감은 눈 ^ ^
            drawArcC(ink, Rect(cx - eyeDx - headR * 0.16f, eyeY - headR * 0.1f, cx - eyeDx + headR * 0.16f, eyeY + headR * 0.16f), 180f, 180f, stroke = 3f)
            drawArcC(ink, Rect(cx + eyeDx - headR * 0.16f, eyeY - headR * 0.1f, cx + eyeDx + headR * 0.16f, eyeY + headR * 0.16f), 180f, 180f, stroke = 3f)
            drawArcC(mouthColor, Rect(cx - headR * 0.34f, eyeY + headR * 0.05f, cx + headR * 0.34f, eyeY + headR * 0.85f), 0f, 180f) // 크게 웃음
        }
        Emotion.SAD -> {
            drawCircle(Color(0x224A90D9), headR, Offset(cx, headCy))
            eyeDot(0.12f)
            // 처진 눈썹
            drawLine(ink, Offset(cx - eyeDx - headR * 0.16f, eyeY - headR * 0.28f), Offset(cx - eyeDx + headR * 0.18f, eyeY - headR * 0.42f), 3f, cap = cap)
            drawLine(ink, Offset(cx + eyeDx + headR * 0.16f, eyeY - headR * 0.28f), Offset(cx + eyeDx - headR * 0.18f, eyeY - headR * 0.42f), 3f, cap = cap)
            // 눈물
            drawCircle(Color(0xCC5AB4E5), headR * 0.1f, Offset(cx - eyeDx, eyeY + headR * 0.35f + (emoT * headR)))
            drawArcC(mouthColor, Rect(cx - headR * 0.2f, eyeY + headR * 0.55f, cx + headR * 0.2f, eyeY + headR * 0.85f), 200f, 140f, stroke = 2.5f)
        }
        Emotion.DUMBFOUNDED -> {
            // 반쯤 뜬 눈 (가로선)
            drawLine(ink, Offset(cx - eyeDx - headR * 0.14f, eyeY), Offset(cx - eyeDx + headR * 0.14f, eyeY), 3f, cap = cap)
            drawLine(ink, Offset(cx + eyeDx - headR * 0.14f, eyeY), Offset(cx + eyeDx + headR * 0.14f, eyeY), 3f, cap = cap)
            drawLine(mouthColor, Offset(cx - headR * 0.18f, eyeY + headR * 0.5f), Offset(cx + headR * 0.18f, eyeY + headR * 0.5f), 2.5f, cap = cap) // 일자 입
        }
        null -> {
            eyeDot()
            drawCircle(Color(0x33F26D5F), headR * 0.13f, Offset(cx - headR * 0.55f, eyeY + headR * 0.28f))
            drawCircle(Color(0x33F26D5F), headR * 0.13f, Offset(cx + headR * 0.55f, eyeY + headR * 0.28f))
            if (speaking) drawCircle(mouthColor, headR * 0.16f, Offset(cx, eyeY + headR * 0.45f))
            else drawArcC(mouthColor, Rect(cx - headR * 0.22f, eyeY + headR * 0.22f, cx + headR * 0.22f, eyeY + headR * 0.55f), 20f, 140f, stroke = 2.5f)
        }
    }

    // 안경
    if (style.glasses) {
        val gr = headR * 0.26f
        drawArcC(ink, Rect(cx - eyeDx - gr, eyeY - gr, cx - eyeDx + gr, eyeY + gr), 0f, 360f, stroke = 3f)
        drawArcC(ink, Rect(cx + eyeDx - gr, eyeY - gr, cx + eyeDx + gr, eyeY + gr), 0f, 360f, stroke = 3f)
        drawLine(ink, Offset(cx - eyeDx + gr, eyeY), Offset(cx + eyeDx - gr, eyeY), 2.5f)
    }

    // 감정 이모지 이펙트 (머리 옆 위)
    if (emotion != null) {
        val es = TextStyle(fontSize = (headR * 0.9f).toSpFallback())
        val er = measurer.measure(emotion.emoji, es)
        val ex = cx + headR * 0.9f
        val ey = headCy - headR * 1.1f - (if (emotion == Emotion.EXCITED) kotlin.math.abs(sin(emoT * PI.toFloat() * 5)) * headR * 0.4f else 0f)
        drawText(er, topLeft = Offset(ex, ey))
    }

    // 이름표
    if (showName) {
        val label = if (isMe) "나" else name.take(6)
        val ts = TextStyle(color = Color(0xFF3A2E28), fontSize = (height * 0.11f).toSpFallback(), fontWeight = FontWeight.SemiBold)
        val res = measurer.measure(label, ts)
        val tagW = res.size.width + height * 0.12f
        val tagRect = Rect(cx - tagW / 2, groundY0 + height * 0.02f, cx + tagW / 2, groundY0 + height * 0.02f + res.size.height + height * 0.04f)
        drawRoundRectC(if (isMe) Color(0xFFFCF1DC) else Color(0xCCFFFFFF), tagRect, tagRect.height / 2)
        if (isMe) drawRoundRectStroke(Color(0xFFF0A02E), tagRect, tagRect.height / 2, 2f)
        drawText(res, topLeft = Offset(cx - res.size.width / 2, tagRect.top + height * 0.02f))
    }
}

/** 캐릭터 하나만 그리는 미리보기 (캐릭터 선택 모달용) */
@Composable
fun CharacterPreview(style: CharacterStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        drawStageCharacter(
            cx0 = size.width / 2f, groundY0 = size.height * 0.9f, height = size.height * 0.82f,
            style = style, speaking = false, jump = 0f, emotion = null, emoT = 0f,
            isMe = false, name = "", measurer = measurer, showName = false
        )
    }
}

/** 헤어 스타일별 그리기 (머리 원 위에 얹는다) */
private fun DrawScope.drawHair(hair: HairType, cx: Float, headCy: Float, headR: Float, color: Color) {
    fun topCap(extend: Float = 0.5f) {
        // 정수리를 덮는 반원 + 이마 라인
        val p = Path().apply {
            addArc(Rect(cx - headR, headCy - headR, cx + headR, headCy + headR * extend), 180f, 180f)
        }
        drawPath(p, color)
        drawArcC(color, Rect(cx - headR, headCy - headR, cx + headR, headCy + headR), 182f, 176f)
    }
    when (hair) {
        HairType.SHORT -> topCap(0.35f)
        HairType.BOB -> {
            topCap(0.5f)
            // 귀 옆으로 턱선까지 내려오는 단발
            drawRoundRectC(color, Rect(cx - headR * 1.02f, headCy - headR * 0.2f, cx - headR * 0.72f, headCy + headR * 0.95f), headR * 0.2f)
            drawRoundRectC(color, Rect(cx + headR * 0.72f, headCy - headR * 0.2f, cx + headR * 1.02f, headCy + headR * 0.95f), headR * 0.2f)
        }
        HairType.LONG -> {
            topCap(0.55f)
            // 어깨까지 흘러내리는 긴 머리
            drawRoundRectC(color, Rect(cx - headR * 1.05f, headCy - headR * 0.1f, cx - headR * 0.68f, headCy + headR * 1.9f), headR * 0.25f)
            drawRoundRectC(color, Rect(cx + headR * 0.68f, headCy - headR * 0.1f, cx + headR * 1.05f, headCy + headR * 1.9f), headR * 0.25f)
        }
        HairType.CURLY -> {
            // 곱슬 — 정수리에 작은 원 여러 개
            for (a in 0..6) {
                val ang = PI.toFloat() + a * (PI.toFloat() / 6)
                val bx = cx + kotlin.math.cos(ang) * headR * 0.92f
                val by = headCy + sin(ang) * headR * 0.92f
                drawCircle(color, headR * 0.3f, Offset(bx, by))
            }
            drawCircle(color, headR * 0.34f, Offset(cx, headCy - headR * 0.85f))
        }
        HairType.PONYTAIL -> {
            topCap(0.45f)
            // 뒤로 묶은 꼬리 (오른쪽 옆)
            drawCircle(color, headR * 0.18f, Offset(cx + headR * 0.95f, headCy - headR * 0.55f))
            val tail = Path().apply {
                moveTo(cx + headR * 0.95f, headCy - headR * 0.6f)
                quadraticBezierTo(cx + headR * 1.7f, headCy, cx + headR * 1.2f, headCy + headR * 1.1f)
                quadraticBezierTo(cx + headR * 1.1f, headCy + headR * 0.4f, cx + headR * 0.8f, headCy - headR * 0.2f)
                close()
            }
            drawPath(tail, color)
        }
        HairType.CAP -> {
            // 야구모자 — 캡 + 챙
            val p = Path().apply { addArc(Rect(cx - headR, headCy - headR, cx + headR, headCy + headR * 0.3f), 180f, 180f) }
            drawPath(p, color)
            drawRoundRectC(color, Rect(cx - headR * 0.1f, headCy - headR * 0.15f, cx + headR * 1.25f, headCy + headR * 0.12f), headR * 0.1f) // 챙
            drawCircle(color.lighten(0.25f), headR * 0.1f, Offset(cx, headCy - headR * 0.6f)) // 버튼
        }
        HairType.BUN -> {
            topCap(0.35f)
            drawCircle(color, headR * 0.34f, Offset(cx, headCy - headR * 1.05f)) // 정수리 번
        }
        HairType.BALD -> { /* 민머리 */ }
    }
}

// ---------------- 말풍선 ----------------

private fun DrawScope.drawSpeechBubble(measurer: TextMeasurer, text: String, cx: Float, bottomY: Float) {
    val ts = TextStyle(color = Color(0xFF2A2320), fontSize = (size.height * 0.058f).toSpFallback(), fontWeight = FontWeight.Medium)
    val res = measurer.measure(text, ts)
    val padH = size.height * 0.05f
    val padV = size.height * 0.03f
    val bw = res.size.width + padH * 2
    val bh = res.size.height + padV * 2
    val left = (cx - bw / 2).coerceIn(4f, size.width - bw - 4f)
    val top = (bottomY - bh).coerceAtLeast(2f)
    val rect = Rect(left, top, left + bw, top + bh)
    // 그림자 + 본체
    drawRoundRectC(Color(0x22000000), rect.translate(0f, 2f), bh / 2)
    drawRoundRectC(Color(0xFFFFFFFF), rect, bh / 2)
    // 꼬리
    val tail = Path().apply {
        moveTo(cx - bw * 0.06f, rect.bottom - 1f)
        lineTo(cx, rect.bottom + size.height * 0.05f)
        lineTo(cx + bw * 0.06f, rect.bottom - 1f); close()
    }
    drawPath(tail, Color(0xFFFFFFFF))
    drawText(res, topLeft = Offset(rect.left + padH, rect.top + padV))
}

// ---------------- draw 헬퍼 ----------------

private fun DrawScope.drawRoundRectC(color: Color, r: Rect, radius: Float) =
    drawRoundRect(color, topLeft = Offset(r.left, r.top), size = Size(r.width, r.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius))

private fun DrawScope.drawRoundRectStroke(color: Color, r: Rect, radius: Float, stroke: Float) =
    drawRoundRect(color, topLeft = Offset(r.left, r.top), size = Size(r.width, r.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))

private fun DrawScope.drawRectStroke(color: Color, r: Rect, stroke: Float) =
    drawRect(color, topLeft = Offset(r.left, r.top), size = Size(r.width, r.height),
        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))

private fun DrawScope.drawArcC(color: Color, r: Rect, start: Float, sweep: Float, stroke: Float? = null) =
    drawArc(color, start, sweep, useCenter = stroke == null,
        topLeft = Offset(r.left, r.top), size = Size(r.width, r.height),
        style = stroke?.let { androidx.compose.ui.graphics.drawscope.Stroke(it) } ?: Fill)

private fun Rect.translate(dx: Float, dy: Float) = Rect(left + dx, top + dy, right + dx, bottom + dy)

private fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
private fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)

// DrawScope에서 px→sp: 폰트 크기를 픽셀 기준으로 쓰기 위해 density로 나눈다
private fun Float.toSpFallback() = (this / 2.6f).coerceIn(9f, 22f).sp

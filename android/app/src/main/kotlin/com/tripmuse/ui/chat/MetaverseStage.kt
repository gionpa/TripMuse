package com.tripmuse.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
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
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()

    // 상시 idle 애니메이션 (캐릭터가 숨쉬듯 위아래로)
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

    // Canvas를 스테이지보다 살짝 크게 그리고 Box로 clip한다.
    // Compose Canvas 하단 경계 픽셀에 나타나는 GPU 아티팩트(무지개 띠)를 잘라내기 위함.
    Box(modifier.clipToBounds()) {
      Canvas(Modifier.fillMaxWidth().height(stageHeight + 14.dp)) {
        drawCafe(measurer)
        if (members.isEmpty()) return@Canvas

        // 캐릭터를 바닥 앞쪽에 x축으로 고르게 분산 (발 아래 이름표까지 스테이지 안에 들어오게)
        val n = members.size
        val marginX = size.width * 0.16f
        val usable = size.width - marginX * 2
        val groundY = size.height * 0.78f
        val charH = (size.height * 0.5f).coerceAtMost(usable / n * 1.5f)

        members.forEachIndexed { i, member ->
            val cx = if (n == 1) size.width / 2 else marginX + usable * i / (n - 1)
            val phase = i * 1.3f
            val bob = sin(clock + phase) * (charH * 0.02f)
            // 말풍선이 떠 있는 동안에만 발화 연출 (방에 들어오자마자 마지막 발화자가 계속 말하는 것처럼 보이지 않게)
            val speaking = member.id == speakerId && speech != null
            val jumpY = if (speaking) sin(jump.value * PI.toFloat()) * (charH * 0.16f) else 0f
            drawCharacter(
                cx = cx, groundY = groundY - bob - jumpY, height = charH,
                bodyColor = CHARACTER_COLORS[i % CHARACTER_COLORS.size],
                hairColor = HAIR_COLORS[i % HAIR_COLORS.size],
                speaking = speaking, jump = if (speaking) jump.value else 0f,
                isMe = member.id == currentUserId,
                name = member.nickname, measurer = measurer
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

// ---------------- 캐릭터 ----------------

private fun DrawScope.drawCharacter(
    cx: Float, groundY: Float, height: Float,
    bodyColor: Color, hairColor: Color,
    speaking: Boolean, jump: Float, isMe: Boolean,
    name: String, measurer: TextMeasurer
) {
    val headR = height * 0.20f
    val bodyW = height * 0.42f
    val bodyH = height * 0.42f
    val headCy = groundY - bodyH - headR * 0.9f

    // 바닥 그림자
    drawOval(Color(0x33000000), topLeft = Offset(cx - bodyW * 0.55f, groundY - height * 0.03f), size = Size(bodyW * 1.1f, height * 0.07f))

    // 발화 강조 링
    if (speaking) {
        drawCircle(bodyColor.copy(alpha = 0.18f), height * 0.34f, Offset(cx, groundY - bodyH * 0.5f))
    }

    // 몸통 (둥근 사다리꼴 느낌의 라운드 사각형)
    drawRoundRectC(bodyColor, Rect(cx - bodyW / 2, groundY - bodyH, cx + bodyW / 2, groundY), bodyW * 0.32f)
    // 옷 밝은 하이라이트
    drawRoundRectC(bodyColor.lighten(0.12f), Rect(cx - bodyW / 2, groundY - bodyH, cx - bodyW * 0.15f, groundY), bodyW * 0.3f)

    // 팔 — 발화 시 한쪽을 위로 드는 제스처
    val armY = groundY - bodyH * 0.72f
    val raise = if (speaking) jump * bodyH * 0.5f else 0f
    drawLine(bodyColor.darken(0.1f), Offset(cx - bodyW * 0.5f, armY), Offset(cx - bodyW * 0.66f, armY + bodyH * 0.28f), height * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(bodyColor.darken(0.1f), Offset(cx + bodyW * 0.5f, armY), Offset(cx + bodyW * 0.62f, armY + bodyH * 0.24f - raise), height * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)

    // 머리
    drawCircle(SKIN, headR, Offset(cx, headCy))
    // 헤어 (윗머리 반원 느낌)
    val hair = Path().apply {
        addArc(Rect(cx - headR, headCy - headR, cx + headR, headCy + headR * 0.5f), 180f, 180f)
        lineTo(cx + headR, headCy)
        arcTo(Rect(cx - headR, headCy - headR, cx + headR, headCy + headR), 0f, -180f, false)
        close()
    }
    drawPath(hair, hairColor)
    drawArcC(hairColor, Rect(cx - headR, headCy - headR, cx + headR, headCy + headR), 180f, 180f)

    // 눈
    val eyeY = headCy + headR * 0.05f
    val eyeDx = headR * 0.38f
    drawCircle(Color(0xFF2A2320), headR * 0.11f, Offset(cx - eyeDx, eyeY))
    drawCircle(Color(0xFF2A2320), headR * 0.11f, Offset(cx + eyeDx, eyeY))
    // 볼터치
    drawCircle(Color(0x33F26D5F), headR * 0.13f, Offset(cx - headR * 0.55f, eyeY + headR * 0.28f))
    drawCircle(Color(0x33F26D5F), headR * 0.13f, Offset(cx + headR * 0.55f, eyeY + headR * 0.28f))
    // 입 — 발화 시 벌어짐
    if (speaking) {
        drawCircle(Color(0xFF7A3B34), headR * 0.16f, Offset(cx, eyeY + headR * 0.45f))
    } else {
        drawArcC(Color(0xFF7A3B34), Rect(cx - headR * 0.22f, eyeY + headR * 0.22f, cx + headR * 0.22f, eyeY + headR * 0.55f), 20f, 140f, stroke = 2.5f)
    }

    // 이름표
    val label = if (isMe) "나" else name.take(6)
    val ts = TextStyle(color = Color(0xFF3A2E28), fontSize = (height * 0.11f).toSpFallback(), fontWeight = FontWeight.SemiBold)
    val res = measurer.measure(label, ts)
    val tagW = res.size.width + height * 0.12f
    val tagRect = Rect(cx - tagW / 2, groundY + height * 0.02f, cx + tagW / 2, groundY + height * 0.02f + res.size.height + height * 0.04f)
    drawRoundRectC(if (isMe) Color(0xFFFCF1DC) else Color(0xCCFFFFFF), tagRect, tagRect.height / 2)
    if (isMe) drawRoundRectStroke(Color(0xFFF0A02E), tagRect, tagRect.height / 2, 2f)
    drawText(res, topLeft = Offset(cx - res.size.width / 2, tagRect.top + height * 0.02f))
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

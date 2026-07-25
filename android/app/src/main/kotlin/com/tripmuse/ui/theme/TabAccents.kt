package com.tripmuse.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "여행의 시간대" 팔레트.
 * 하루의 하늘에서 따온 탭별 액센트 — 색상(hue)은 다르지만 채도/명도 대역을 맞춰
 * 탭마다 개성을 주면서도 전체가 한 시스템으로 읽히게 한다.
 *
 *  앨범   = 한낮의 하늘 (브랜드 블루)
 *  친구   = 여행지의 바다 (청록)
 *  채팅   = 노을 아래의 대화 (앰버)
 *  추천   = 석양의 영감 (코랄)
 *  프로필 = 밤하늘 (바이올렛)
 *
 * accent    : 아이콘/텍스트/채워진 버튼
 * container : 선택 인디케이터/소프트 버튼 배경 (액센트의 ~92% 밝기 틴트)
 * deep      : container 위에 얹는 텍스트/아이콘 (대비 확보용 진한 톤)
 */
data class TabAccent(
    val accent: Color,
    val container: Color,
    val deep: Color
)

object TripMuseAccents {
    val Album = TabAccent(
        accent = Color(0xFF5B7FFF),
        container = Color(0xFFE9EEFF),
        deep = Color(0xFF3554D1)
    )
    val Friend = TabAccent(
        accent = Color(0xFF26B49C),
        container = Color(0xFFE0F5F0),
        deep = Color(0xFF0E7A66)
    )
    val Chat = TabAccent(
        accent = Color(0xFFF0A02E),
        container = Color(0xFFFCF1DC),
        deep = Color(0xFF9A6200)
    )
    val Recommend = TabAccent(
        accent = Color(0xFFF26D5F),
        container = Color(0xFFFDEAE7),
        deep = Color(0xFFB23E31)
    )
    val Profile = TabAccent(
        accent = Color(0xFF8E6FF7),
        container = Color(0xFFEFEAFE),
        deep = Color(0xFF5F3DC4)
    )

    /** 하단 바에서 선택되지 않은 탭 (뉴트럴) */
    val Unselected = Color(0xFF9AA3AF)
}

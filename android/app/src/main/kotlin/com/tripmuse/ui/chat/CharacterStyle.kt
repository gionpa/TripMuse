package com.tripmuse.ui.chat

import androidx.compose.ui.graphics.Color

enum class CharacterGender { MALE, FEMALE, NEUTRAL }

enum class HairType { SHORT, BOB, LONG, CURLY, PONYTAIL, CAP, BUN, BALD }

/** 스테이지 캐릭터의 외형. key가 서버에 저장되는 값이다. */
data class CharacterStyle(
    val key: String,
    val label: String,
    val gender: CharacterGender,
    val bodyColor: Color,
    val hairColor: Color,
    val hair: HairType,
    val glasses: Boolean = false
)

/** 성별·스타일이 서로 구별되는 8종 */
val CHARACTER_STYLES = listOf(
    CharacterStyle("f_bob", "단발 소녀", CharacterGender.FEMALE, Color(0xFFE86AA6), Color(0xFF4A2E22), HairType.BOB),
    CharacterStyle("m_short", "짧은 머리", CharacterGender.MALE, Color(0xFF5B7FFF), Color(0xFF1F1B18), HairType.SHORT),
    CharacterStyle("f_long", "긴 머리", CharacterGender.FEMALE, Color(0xFF8E6FF7), Color(0xFF3B2C24), HairType.LONG),
    CharacterStyle("m_curly", "곱슬 머리", CharacterGender.MALE, Color(0xFF6BBF59), Color(0xFF2A1E16), HairType.CURLY),
    CharacterStyle("f_pony", "포니테일", CharacterGender.FEMALE, Color(0xFFF26D5F), Color(0xFF5A3A22), HairType.PONYTAIL),
    CharacterStyle("m_glasses", "안경 청년", CharacterGender.MALE, Color(0xFF26B49C), Color(0xFF3B2C24), HairType.SHORT, glasses = true),
    CharacterStyle("n_cap", "모자", CharacterGender.NEUTRAL, Color(0xFFF0A02E), Color(0xFF1F1B18), HairType.CAP),
    CharacterStyle("f_bun", "번 헤어", CharacterGender.FEMALE, Color(0xFF00A9C7), Color(0xFF4A2E22), HairType.BUN),
)

/** 저장된 key로 스타일을 찾고, 없으면 index 기반 기본값 (기존 사용자/신규 대비) */
fun styleForKey(key: String?, fallbackIndex: Int): CharacterStyle =
    CHARACTER_STYLES.firstOrNull { it.key == key }
        ?: CHARACTER_STYLES[((fallbackIndex % CHARACTER_STYLES.size) + CHARACTER_STYLES.size) % CHARACTER_STYLES.size]

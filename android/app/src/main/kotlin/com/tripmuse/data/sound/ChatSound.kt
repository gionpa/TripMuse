package com.tripmuse.data.sound

import androidx.annotation.RawRes
import com.tripmuse.R

/**
 * 채팅 수신음 목록.
 *
 * key는 설정에 저장되는 값이라, 바꾸면 사용자가 골라둔 소리가 기본값으로 되돌아간다.
 */
enum class ChatSound(
    val key: String,
    val label: String,
    val description: String,
    @RawRes val resId: Int
) {
    BBOROONG("bboroong", "뽀로롱", "칼림바 세 음이 도르륵 올라가요", R.raw.chat_bboroong),
    WATER("water", "물방울", "동그랗게 튀어오르는 물방울", R.raw.chat_water),
    TWINKLE("twinkle", "반짝", "맑은 종소리 두 번", R.raw.chat_twinkle),
    CHIRP("chirp", "짹짹", "작은 새가 부르는 소리", R.raw.chat_chirp),
    POP("pop", "뿅", "옛날 게임기 같은 팝", R.raw.chat_pop),
    KNOCK("knock", "톡톡", "나무를 가볍게 두드려요", R.raw.chat_knock);

    companion object {
        val DEFAULT = BBOROONG

        fun fromKey(key: String?): ChatSound = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

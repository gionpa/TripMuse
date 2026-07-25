package com.tripmuse.domain

import jakarta.persistence.*

/**
 * 친구 간 위치 공유 상태. 한 쌍(pair)당 한 행만 존재한다.
 * REQUESTED: requester가 요청, recipient의 승인 대기
 * APPROVED: 양방향 위치 공유 승인 완료
 */
@Entity
@Table(
    name = "location_shares",
    uniqueConstraints = [UniqueConstraint(name = "uk_location_share_pair", columnNames = ["requester_id", "recipient_id"])],
    indexes = [
        Index(name = "idx_location_shares_requester", columnList = "requester_id"),
        Index(name = "idx_location_shares_recipient", columnList = "recipient_id")
    ]
)
class LocationShare(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    val requester: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    val recipient: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: LocationShareStatus = LocationShareStatus.REQUESTED
) : BaseEntity()

enum class LocationShareStatus {
    REQUESTED,
    APPROVED
}

/** 친구 목록에서 각 친구별로 보여줄 위치 공유 UI 상태 */
enum class LocationShareUiStatus {
    NONE,                // 위치 공유 요청 버튼 노출
    REQUESTED_BY_ME,     // 내가 요청함 - 상대 승인 대기
    PENDING_MY_APPROVAL, // 상대가 요청함 - 승인 버튼 노출
    APPROVED             // 현재 위치보기 버튼 노출
}

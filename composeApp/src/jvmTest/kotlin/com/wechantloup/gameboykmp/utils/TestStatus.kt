package com.wechantloup.gameboykmp.utils

import kotlinx.serialization.Serializable

@Serializable
enum class TestStatus {
    PASS,
    FAIL,
    UNKNOWN,
}

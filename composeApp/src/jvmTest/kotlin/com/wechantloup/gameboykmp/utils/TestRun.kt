package com.wechantloup.gameboykmp.utils

import kotlinx.serialization.Serializable

@Serializable
data class TestRun(
    val timeStamp: String,
    val tests: MutableMap<String, TestStatus>,
)

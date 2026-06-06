package com.wechantloup.gameboykmp.utils

import kotlinx.serialization.Serializable

@Serializable
data class AllTestRun(
    val testRuns: MutableList<TestRun>,
)

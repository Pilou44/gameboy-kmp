package com.wechantloup.gameboykmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
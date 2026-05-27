package com.wechantloup.gameboykmp.ui

enum class Palette(
    val displayName: String,
    val colors: List<Int>,
) {
    DMG(
        displayName = "DMG",
        colors = listOf(
            0xFF9BBC0F.toInt(),
            0xFF7B8F00.toInt(),
            0xFF3E5C00.toInt(),
            0xFF1F3A00.toInt(),
        )
    ),
    TRUE_DMG(
        displayName = "True DMG",
        colors = listOf(
            0xFF9BBC0F.toInt(),
            0xFF8BAC0F.toInt(),
            0xFF306230.toInt(),
            0xFF0F380F.toInt(),
        )
    ),
    POCKET(
        displayName = "Pocket",
        colors = listOf(
            0xFFC8C8B8.toInt(),
            0xFF8C8C7C.toInt(),
            0xFF4A4A3E.toInt(),
            0xFF1A1A14.toInt(),
        )
    ),
    TRUE_POCKET(
        displayName = "True pocket",
        colors = listOf(
            0xFFD0D0D0.toInt(),
            0xFF909090.toInt(),
            0xFF484848.toInt(),
            0xFF181818.toInt(),
        )
    ),
    DOC_BOY_TEST(
        displayName = "True pocket",
        colors = listOf(
            0xFFFFFEFF.toInt(),
            0xFFACA9AC.toInt(),
            0xFF525452.toInt(),
            0xFF000000.toInt(),
        )
    );
}

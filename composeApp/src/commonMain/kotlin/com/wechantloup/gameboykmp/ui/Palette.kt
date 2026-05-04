package com.wechantloup.gameboykmp.ui

sealed interface Palette {
    val name: String
    val colors: List<Int>

    object Dmg: Palette {
        override val name = "DMG"
        override val colors = listOf(
            0xFF9BBC0F.toInt(),
            0xFF7B8F00.toInt(),
            0xFF3E5C00.toInt(),
            0xFF1F3A00.toInt(),
        )
    }

    object TrueDmg: Palette {
        override val name = "True DMG"
        override val colors = listOf(
            0xFF9BBC0F.toInt(),
            0xFF8BAC0F.toInt(),
            0xFF306230.toInt(),
            0xFF0F380F.toInt(),
        )
    }

    object Pocket: Palette {
        override val name = "Pocket"
        override val colors = listOf(
            0xFFC8C8B8.toInt(),
            0xFF8C8C7C.toInt(),
            0xFF4A4A3E.toInt(),
            0xFF1A1A14.toInt(),
        )
    }

    object TruePocket: Palette {
        override val name = "True pocket"
        override val colors = listOf(
            0xFFD0D0D0.toInt(),
            0xFF909090.toInt(),
            0xFF484848.toInt(),
            0xFF181818.toInt(),
        )
    }

    companion object {
        val all = listOf(Dmg, TrueDmg, Pocket, TruePocket)
    }
}

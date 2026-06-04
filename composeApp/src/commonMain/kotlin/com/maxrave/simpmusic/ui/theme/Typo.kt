package com.maxrave.simpmusic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.maxrave.domain.manager.DesktopFontFamily
import org.jetbrains.compose.resources.Font
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.noto_sans_bold
import simpmusic.composeapp.generated.resources.noto_sans_medium
import simpmusic.composeapp.generated.resources.noto_sans_regular
import simpmusic.composeapp.generated.resources.noto_sans_sc_bold
import simpmusic.composeapp.generated.resources.noto_sans_sc_medium
import simpmusic.composeapp.generated.resources.noto_sans_sc_regular
import simpmusic.composeapp.generated.resources.poppins_bold
import simpmusic.composeapp.generated.resources.poppins_medium
import simpmusic.composeapp.generated.resources.poppins_regular

val LocalAppFontFamily = staticCompositionLocalOf { DesktopFontFamily.DEFAULT }

@Composable
fun fontFamily(): FontFamily =
    when (DesktopFontFamily.normalize(LocalAppFontFamily.current)) {
        DesktopFontFamily.SYSTEM -> FontFamily.Default
        DesktopFontFamily.NOTO_SANS -> notoSansFontFamily()
        DesktopFontFamily.POPPINS -> poppinsFontFamily()
        else -> notoSansScFontFamily()
    }

@Composable
private fun poppinsFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.poppins_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.poppins_medium, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.poppins_medium, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.poppins_bold, FontWeight.Bold, FontStyle.Normal),
    )

@Composable
private fun notoSansFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.noto_sans_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.noto_sans_medium, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.noto_sans_medium, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.noto_sans_bold, FontWeight.Bold, FontStyle.Normal),
    )

@Composable
private fun notoSansScFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.noto_sans_sc_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.noto_sans_sc_medium, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.noto_sans_sc_medium, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.noto_sans_sc_bold, FontWeight.Bold, FontStyle.Normal),
    )

@Composable
fun typo(): Typography {
    val fontFamily = fontFamily()

    val typo =
        Typography(
            /***
             * This typo().is use for the title of the Playlist, Artist, Song, Album, etc. in Home, Mood, Genre, Playlist, etc.
             */
            titleSmall =
                TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = fontFamily,
                    color = Color.White,
                ),
            titleMedium =
                TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = fontFamily,
                    color = Color.White,
                ),
            titleLarge =
                TextStyle(
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    color = Color.White,
                ),
            bodySmall =
                TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = fontFamily,
                    color = Color(0xFFA8A8A8),
                ),
            bodyMedium =
                TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = fontFamily,
                    color = Color(0xFFA8A8A8),
                ),
            bodyLarge =
                TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = fontFamily,
                    color = Color(0xFFA8A8A8),
                ),
            displayLarge =
                TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = fontFamily,
                    color = Color(0xFFA8A8A8),
                ),
            headlineMedium =
                TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    color = Color(0xFFA8A8A8),
                ),
            headlineLarge =
                TextStyle(
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    color = Color(0xFFA8A8A8),
                ),
            labelMedium =
                TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    color = Color(0xFFA8A8A8),
                ),
            labelSmall =
                TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = fontFamily,
                    color = Color(0xFFA8A8A8),
                ),
            // ...
        )
    return typo
}

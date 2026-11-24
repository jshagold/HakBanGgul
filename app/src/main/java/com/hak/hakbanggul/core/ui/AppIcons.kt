package com.hak.hakbanggul.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// 앱의 아이콘. 아이콘 라이브러리를 들이지 않고 화면 시안의 모양을 24×24 좌표로 그린다.
// 색은 Icon의 tint가 정한다.
object AppIcons {

    // 새로 고침: 선 굵기 2, 끝과 꺾이는 곳은 둥글게
    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            strokePath("M20 12a8 8 0 1 1-2.6-5.9")
            strokePath("M20 4v5h-5")
        }
    }

    // 메뉴: 채운 원 셋
    val Menu: ImageVector by lazy {
        icon("Menu") {
            fillPath(circle(12f, 5f, 1.8f))
            fillPath(circle(12f, 12f, 1.8f))
            fillPath(circle(12f, 19f, 1.8f))
        }
    }

    // 알림: 선 굵기 2, 끝은 둥글게
    val Alert: ImageVector by lazy {
        icon("Alert") {
            strokePath(circle(12f, 12f, 9f))
            strokePath("M12 7v6")
            strokePath("M12 16.5v.5")
        }
    }

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private fun ImageVector.Builder.strokePath(pathData: String) {
        addPath(
            pathData = addPathNodes(pathData),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun ImageVector.Builder.fillPath(pathData: String) {
        addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
    }

    // 중심 (cx, cy), 반지름 r인 원을 두 반원으로 그리는 경로
    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"
}

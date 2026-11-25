package com.hak.hakbanggul.core.ui

import android.content.Context
import android.content.Intent

// 위젯이 앱을 열 때 쓰는 Intent를 만들고 읽는 곳.
// 위젯(기능)과 MainActivity(앱 맨 위)가 서로를 가리키지 않고 이것만 본다.
object AppLaunchIntents {

    // "시장 분위기 화면을 보이라"는 표시
    private const val EXTRA_SHOW_MARKET_MOOD = "com.hak.hakbanggul.extra.SHOW_MARKET_MOOD"

    // 이 앱을 여는 Intent에 시장 분위기 화면을 보이라는 표시를 단다. MainActivity를 이름으로 가리키지 않고 패키지의 실행 Intent를 쓴다.
    // 앱이 이미 열려 있으면 새로 만들지 않고 있는 것이 이 Intent를 받게 한다(CLEAR_TOP과 SINGLE_TOP을 함께 달아야 받는다).
    fun marketMood(context: Context): Intent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        return launch.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_SHOW_MARKET_MOOD, true)
        }
    }

    // 받은 Intent에 시장 분위기 화면을 보이라는 표시가 있는지
    fun showsMarketMood(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_SHOW_MARKET_MOOD, false) == true
}

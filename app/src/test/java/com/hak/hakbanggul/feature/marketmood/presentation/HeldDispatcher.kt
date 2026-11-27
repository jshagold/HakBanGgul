package com.hak.hakbanggul.feature.marketmood.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

// 한 벌을 계산하는 디스패처. 붙잡아 둔 동안(held)은 넘겨받은 일을 쌓아 두기만 해 "계산 중"을 만든다.
// release하면 쌓아 둔 일과 그 뒤의 일을 target으로 넘긴다(테스트는 그 뒤 runCurrent로 돌린다). hold하면 다시 붙잡는다.
class HeldDispatcher(private val target: CoroutineDispatcher) : CoroutineDispatcher() {
    private val queued = mutableListOf<Pair<CoroutineContext, Runnable>>()
    private var held = true

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (held) queued += context to block else target.dispatch(context, block)
    }

    fun release() {
        held = false
        val blocks = queued.toList()
        queued.clear()
        blocks.forEach { (context, block) -> target.dispatch(context, block) }
    }

    fun hold() {
        held = true
    }
}

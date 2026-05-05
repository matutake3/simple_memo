package jp.simplist.memo.ui.adapters

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText

/**
 * 空状態でバックスペースが押されたタイミングで onBackspaceWhenEmpty を呼ぶ EditText。
 *
 * ソフトキーボード (Gboard / Samsung / iWnn 等) では「空 EditText に対する KEYCODE_DEL」が
 * 必ずしも setOnKeyListener に飛んでこない。代わりに IME は InputConnection 経由で
 * deleteSurroundingText / sendKeyEvent を呼ぶため、両方を InputConnectionWrapper で
 * 監視することで主要 IME 全てで動作する。ハードウェアキーボードは onKeyDown 経由で
 * 検知する (super のロジックを通る)。
 */
class BackspaceAwareEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {

    /**
     * 空状態で Backspace 検知時に呼ばれる。
     * 戻り値 true なら IME へのイベントを消費 (super への伝播を止める)。
     */
    var onBackspaceWhenEmpty: (() -> Boolean)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return BackspaceWrapper(base)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL && text?.isEmpty() == true) {
            if (onBackspaceWhenEmpty?.invoke() == true) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private inner class BackspaceWrapper(target: InputConnection) :
        InputConnectionWrapper(target, true) {

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength == 1 && afterLength == 0 && text?.isEmpty() == true) {
                if (onBackspaceWhenEmpty?.invoke() == true) return false
            }
            return super.deleteSurroundingText(beforeLength, afterLength)
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_DEL &&
                text?.isEmpty() == true
            ) {
                if (onBackspaceWhenEmpty?.invoke() == true) return false
            }
            return super.sendKeyEvent(event)
        }
    }
}

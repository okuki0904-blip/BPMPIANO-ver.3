package com.example.bpmpiano

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var bpmEditText: EditText
    private val handler = Handler(Looper.getMainLooper())

    // 五个按钮对应的贝斯音高，从左到右：F#1 E1 A1 D2 E2
    private val noteFrequencies = floatArrayOf(46.25f, 41.20f, 55.00f, 73.42f, 82.41f) // F#1 E1 A1 D2 E2
    private val buttonCount = noteFrequencies.size

    private lateinit var voices: Array<BassVoice>
    private val runningState = BooleanArray(buttonCount)
    private val runnables = arrayOfNulls<Runnable>(buttonCount)

    // 音符时值：false = 八分音符，true = 十六分音符
    private var isSixteenthMode = false
    private lateinit var buttonEighthNote: ImageButton
    private lateinit var buttonSixteenthNote: ImageButton

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bpmEditText = findViewById(R.id.editTextBpm)
        bpmEditText.setText("120")

        // 八分音符 / 十六分音符 切换按钮
        buttonEighthNote = findViewById(R.id.buttonEighthNote)
        buttonSixteenthNote = findViewById(R.id.buttonSixteenthNote)
        buttonEighthNote.setOnClickListener {
            isSixteenthMode = false
            updateNoteValueButtons()
        }
        buttonSixteenthNote.setOnClickListener {
            isSixteenthMode = true
            updateNoteValueButtons()
        }
        updateNoteValueButtons()

        voices = Array(buttonCount) { i -> BassVoice(noteFrequencies[i]) }

        val buttonIds = intArrayOf(R.id.button1, R.id.button2, R.id.button3, R.id.button4, R.id.button5)
        for (i in 0 until buttonCount) {
            val btn = findViewById<Button>(buttonIds[i])
            // 按钮不显示文字
            btn.text = ""
            btn.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startPlaying(i, view as Button)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stopPlaying(i, view as Button)
                        view.performClick()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    /** 根据当前选择的音符时值，刷新两个切换按钮的高亮状态 */
    private fun updateNoteValueButtons() {
        buttonEighthNote.setBackgroundResource(
            if (!isSixteenthMode) R.drawable.bg_note_toggle_selected else R.drawable.bg_note_toggle_unselected
        )
        buttonSixteenthNote.setBackgroundResource(
            if (isSixteenthMode) R.drawable.bg_note_toggle_selected else R.drawable.bg_note_toggle_unselected
        )
    }

    /** 读取当前 BPM 输入框的值，非法输入时回退到 120，并限制在合理范围 */
    private fun currentBpm(): Double {
        val text = bpmEditText.text.toString()
        val v = text.toDoubleOrNull() ?: 120.0
        return v.coerceIn(20.0, 300.0)
    }

    /** 按下：开始连续播放对应音符 */
    private fun startPlaying(index: Int, btn: Button) {
        if (runningState[index]) return
        runningState[index] = true
        btn.alpha = 0.55f
        val runnable = object : Runnable {
            override fun run() {
                if (!runningState[index]) return
                voices[index].trigger()
                val bpm = currentBpm()
                // 四分音符时值 = 60000 / bpm
                // 八分音符 = 四分音符 / 2 ；十六分音符 = 四分音符 / 4
                val divisor = if (isSixteenthMode) 4.0 else 2.0
                val intervalMs = ((60000.0 / bpm) / divisor).toLong()
                handler.postDelayed(this, intervalMs)
            }
        }
        runnables[index] = runnable
        handler.post(runnable)
    }

    /** 松开：停止播放 */
    private fun stopPlaying(index: Int, btn: Button) {
        runningState[index] = false
        runnables[index]?.let { handler.removeCallbacks(it) }
        btn.alpha = 1.0f
    }

    override fun onDestroy() {
        super.onDestroy()
        for (r in runnables) r?.let { handler.removeCallbacks(it) }
        for (v in voices) v.release()
    }
}

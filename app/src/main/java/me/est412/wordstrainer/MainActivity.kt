package me.est412.wordstrainer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.textview.MaterialTextView
import me.est412.wordstrainer.MainActivity.OnScale
import me.est412.wordstrainer.databinding.ActivityMainBinding
import me.est412.wordstrainer.model.Dictionary
import me.est412.wordstrainer.model.DictionaryIterator
import me.est412.wordstrainer.model.XLSXPoiDictionary
import java.io.IOException

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {
    companion object {
        const val MIN_SCALE_FACTOR = 0.5f
        const val MAX_SCALE_FACTOR = 3f

        const val EDIT_REQUEST_CODE = 44

        const val PREF_LAST_FILE = "lastFile"
        const val PREF_SIZE_FOREIGN = "sizeForeign"
        const val PREF_SIZE_NATIVE = "sizeNative"
    }

    private lateinit var tvLang: Array<TextView>
    private lateinit var menu: Menu

    private lateinit var b: ActivityMainBinding
    private lateinit var dictIterator: DictionaryIterator
    private var dict: Dictionary? = null
    private var uri: Uri? = null
    private var showTranslation = false

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    private lateinit var scalingTV: TextView
    private val textSizesSP = mutableMapOf<TextView, Float>()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLInputFactory",
                "com.fasterxml.aalto.stax.InputFactoryImpl"
        )
        System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLOutputFactory",
                "com.fasterxml.aalto.stax.OutputFactoryImpl"
        )
        System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLEventFactory",
                "com.fasterxml.aalto.stax.EventFactoryImpl"
        )
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        dictIterator = DictionaryIterator()
        scaleGestureDetector = ScaleGestureDetector(this, OnScale { scale(it) })
        b.tvForeign.setOnTouchListener { v, e -> onTouch(v, e) }
        b.tvNative.setOnTouchListener { v, e -> onTouch(v, e) }
        tvLang = arrayOf(b.tvForeign, b.tvNative)

        val sPref = getPreferences(MODE_PRIVATE)
        val lastFile = sPref.getString(PREF_LAST_FILE, null)
        if (lastFile != null) {
            b.tvUri.text = getString(R.string.tv_uri_last, lastFile)
        }

        textSizesSP[b.tvForeign] = sPref.getFloat(PREF_SIZE_FOREIGN, b.tvForeign.textSizeSP())
        textSizesSP[b.tvNative] = sPref.getFloat(PREF_SIZE_NATIVE, b.tvNative.textSizeSP())
        b.tvForeign.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizesSP[b.tvForeign]!!)
        b.tvNative.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizesSP[b.tvNative]!!)
    }

    private fun MaterialTextView.textSizeSP() = textSize / resources.displayMetrics.scaledDensity

    override fun onDestroy() {
        if (uri != null) {
            try {
                dict!!.close(contentResolver.openOutputStream(uri!!))
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        super.onDestroy()
    }

    override fun onBackPressed() {
        moveTaskToBack(true);
    }

    override fun onPause() {
        if (uri != null) {
            try {
                dict!!.save(contentResolver.openOutputStream(uri!!))
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        val sPref = getPreferences(MODE_PRIVATE)
        val ed = sPref.edit()
        ed.putFloat(PREF_SIZE_FOREIGN, b.tvForeign.textSizeSP())
        ed.putFloat(PREF_SIZE_NATIVE, b.tvNative.textSizeSP())
        ed.apply()
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != EDIT_REQUEST_CODE || resultCode != RESULT_OK || data == null) {
            return
        }
        val oldUri = uri
        val oldDict = dict
        uri = data.data
        try {
            dict = XLSXPoiDictionary(contentResolver.openInputStream(uri!!)!!)
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            return
        }
        if (oldUri != null) {
            try {
                oldDict!!.close(contentResolver.openOutputStream(oldUri))
            } catch (e: IOException) {
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        }
        var path: String? = "File name unknown"
        try {
            path = getFileName(uri!!)
        } catch (ignored: NullPointerException) {
            // do nothing
        }
        b.tvUri.text = path
        dictIterator.setDictionary(dict)
        restart()
        val sPref = getPreferences(MODE_PRIVATE)
        val ed = sPref.edit()
        ed.putString(PREF_LAST_FILE, path)
        ed.apply()
        menu.findItem(R.id.restart).isEnabled = true
    }

    private fun getFileName(uri: Uri): String? {
        var result: String?
        // https://stackoverflow.com/questions/44735310/get-filename-from-google-drive-uri
        result = DocumentFile.fromSingleUri(this, uri)!!.name
        if (result != null) return result

        // https://stackoverflow.com/questions/5568874/how-to-extract-the-file-name-from-uri-returned-from-intent-action-get-content/25005243#25005243
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result!!.lastIndexOf('/')
            if (cut != -1) {
                result = result!!.substring(cut + 1)
            }
        }
        return result
    }

    private fun restart() {
        dictIterator.clearCurWord()
        showTranslation = false
        b.btnNext.isEnabled = true
        b.cbNativeFirst.isEnabled = true
        b.btnNext.text = getString(R.string.btn_next_go)
        b.tvForeign.hint = getString(R.string.tv_foreign)
        b.tvNative.hint = getString(R.string.tv_native)
        b.tvForeign.text = ""
        b.tvNative.text = ""
        b.tvCount.text = "${dictIterator.getIdxWordsCounder(dictIterator.curLang)} / " +
                "${dictIterator.getIdxWordsNumber(dictIterator.curLang)}"
        b.cbRepeat.isChecked = false
        b.cbRepeat.isEnabled = false
        b.cbRepetition.isEnabled = true
    }

    private fun scale(detector: ScaleGestureDetector): Boolean {
        scaleFactor *= detector.scaleFactor
        scaleFactor =
                MIN_SCALE_FACTOR.coerceAtLeast(scaleFactor.coerceAtMost(MAX_SCALE_FACTOR))
        val size = textSizesSP[scalingTV]!! * scaleFactor
        scalingTV.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        return true
    }

    private fun onTouch(v: View, event: MotionEvent): Boolean {
        scalingTV = v as TextView
        scaleGestureDetector.onTouchEvent(event)
        return true
    }

    fun interface OnScale : ScaleGestureDetector.OnScaleGestureListener {
        override fun onScaleBegin(p0: ScaleGestureDetector?): Boolean = true
        override fun onScaleEnd(p0: ScaleGestureDetector?) {}
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        this.menu = menu
        return true
    }

    fun onMenuFile(item: MenuItem?) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        startActivityForResult(intent, EDIT_REQUEST_CODE)
    }

    fun onMenuRestart(item: MenuItem?) {
        dictIterator.setDictionary(dict)
        restart()
    }

    fun onCbNativeFirst(view: View?) {
        b.cvTop.removeAllViews()
        b.cvBottom.removeAllViews()
        if (b.cbNativeFirst.isChecked) {
            b.cvTop.addView(b.tvNative)
            b.cvBottom.addView(b.tvForeign)
        } else {
            b.cvTop.addView(b.tvForeign)
            b.cvBottom.addView(b.tvNative)
        }
        dictIterator.setActiveLangs(if (b.cbNativeFirst.isChecked) 1 else 0)
        b.tvCount.text = "${dictIterator.getIdxWordsCounder(dictIterator.curLang)} / " +
                "${dictIterator.getIdxWordsNumber(dictIterator.curLang)}"
    }

    fun onCbRepeat(view: View?) {
        dictIterator.setToRepeat(b.cbRepeat.isChecked)
    }

    fun onCbRepetition(view: View?) {
        dictIterator.setRepetition(b.cbRepetition.isChecked)
        b.tvCount.text = "${dictIterator.getIdxWordsCounder(dictIterator.curLang)} / " +
                "${dictIterator.getIdxWordsNumber(dictIterator.curLang)}"
    }

    fun onBtnNext(view: View?) {
        b.cbNativeFirst.isEnabled = false
        b.cbRepeat.isEnabled = true
        b.cbRepetition.isEnabled = false
        b.tvForeign.hint = ""
        b.tvNative.hint = ""
        if (!showTranslation) {
            if (!dictIterator.isWordsRemain) {
                b.btnNext.isEnabled = false
                return
            }
            dictIterator.nextWord()
            if (b.cbNativeFirst.isChecked) {
                b.tvNative.text = dictIterator.curWord[1]
                b.tvForeign.text = ""
            } else {
                b.tvNative.text = ""
                b.tvForeign.text = dictIterator.curWord[0]
            }
            b.tvCount.text = "${dictIterator.getIdxWordsCounder(dictIterator.curLang)} / " +
                    "${dictIterator.getIdxWordsNumber(dictIterator.curLang)}"
            b.cbRepeat.isChecked = dictIterator.isToRepeat()
            showTranslation = true
        } else { // show translation
            dictIterator.translateCurWord()
            if (b.cbNativeFirst.isChecked) {
                b.tvForeign.text = dictIterator.curWord[0]
            } else {
                b.tvNative.text = dictIterator.curWord[1]
            }
            if (!dictIterator.isWordsRemain) {
                b.btnNext.isEnabled = false
                return
            }
            showTranslation = false
        }
        b.btnNext.text = getString(R.string.btn_next_next)
    }
}
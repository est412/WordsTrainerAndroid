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
import me.est412.wordstrainer.MainActivity.OnScale
import me.est412.wordstrainer.databinding.ActivityMainBinding
import me.est412.wordstrainer.model.Dictionary
import me.est412.wordstrainer.model.DictionaryIterator
import me.est412.wordstrainer.model.XLSXPoiDictionary
import java.io.IOException

class MainActivity : AppCompatActivity() {
    companion object {
        const val MIN_SCALE_FACTOR = 0.5f
        const val MAX_SCALE_FACTOR = 3f

        const val EDIT_REQUEST_CODE = 44

        const val PREF_LAST_FILE = "lastFile"
        const val PREF_SIZE_LANG_0 = "sizeLang0"
        const val PREF_SIZE_LANG_1 = "sizeLang1"
    }

    private lateinit var tvLang: Array<TextView>
    private lateinit var menu: Menu

    private lateinit var bnd: ActivityMainBinding
    private lateinit var dictIterator: DictionaryIterator
    private var dict: Dictionary? = null
    private var uri: Uri? = null
    private var toShow = 0

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    private lateinit var scalingTV: TextView
    private val initialSize = mutableMapOf<TextView, Float>()


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
        bnd = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bnd.root)

        dictIterator = DictionaryIterator()
        scaleGestureDetector = ScaleGestureDetector(this, OnScale { scale(it) })
        bnd.tvTop.setOnTouchListener { v, e -> onTouch(v, e) }
        bnd.tvBottom.setOnTouchListener { v, e -> onTouch(v, e) }
        tvLang = arrayOf(bnd.tvTop, bnd.tvBottom)

        val sPref = getPreferences(MODE_PRIVATE)
        val lastFile = sPref.getString(PREF_LAST_FILE, null)
        if (lastFile != null) {
            bnd.tvUri.text = "Last: $lastFile"
        }

        initialSize[tvLang[0]] = sPref.getFloat(PREF_SIZE_LANG_0, tvLang[0].textSize)
        initialSize[tvLang[1]] = sPref.getFloat(PREF_SIZE_LANG_1, tvLang[1].textSize)
        tvLang[0].setTextSize(TypedValue.COMPLEX_UNIT_PX, initialSize[tvLang[0]]!!)
        tvLang[1].setTextSize(TypedValue.COMPLEX_UNIT_PX, initialSize[tvLang[1]]!!)
    }

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
        ed.putFloat(PREF_SIZE_LANG_0, tvLang[0].textSize)
        ed.putFloat(PREF_SIZE_LANG_1, tvLang[1].textSize)
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
        bnd.tvUri.text = path
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
        toShow = 1
        bnd.btnNext.isEnabled = true
        bnd.cbNativeFirst.isEnabled = true
        bnd.btnNext.text = getString(R.string.btn_next_go)
        tvLang[0].text = ""
        tvLang[1].text = ""
        bnd.tvCount.text = "${dictIterator.getIdxWordsCounder(dictIterator.curLang)} / " +
                "${dictIterator.getIdxWordsNumber(dictIterator.curLang)}"
        bnd.cbRepeat.isChecked = false
        bnd.cbRepeat.isEnabled = false
        bnd.cbRepetition.isEnabled = true
        //dictIterator.setCurLang(cbNativeFirst.isChecked() ? 1 : 0);
    }

    private fun scale(detector: ScaleGestureDetector): Boolean {
        scaleFactor *= detector.scaleFactor
        scaleFactor =
            MIN_SCALE_FACTOR.coerceAtLeast(scaleFactor.coerceAtMost(MAX_SCALE_FACTOR))
        val size = initialSize[scalingTV]!! * scaleFactor
        scalingTV.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        return true
    }

    private fun onTouch(v: View, event: MotionEvent) : Boolean {
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
        dictIterator.setActiveLangs(if (bnd.cbNativeFirst.isChecked) 1 else 0)
        bnd.tvCount.text = "${dictIterator.getIdxWordsCounder(dictIterator.curLang)} / " +
                "${dictIterator.getIdxWordsNumber(dictIterator.curLang)}"
        val tmpSize = tvLang[0].textSize
        tvLang[0].setTextSize(TypedValue.COMPLEX_UNIT_PX, tvLang[1].textSize)
        tvLang[1].setTextSize(TypedValue.COMPLEX_UNIT_PX, tmpSize)
        val tmpText = tvLang[0].text.toString()
        tvLang[0].text = tvLang[1].text
        tvLang[1].text = tmpText
    }

    fun onCbRepeat(view: View?) {
        dictIterator.setToRepeat(bnd.cbRepeat.isChecked)
    }

    fun onCbRepetition(view: View?) {
        dictIterator.setRepetition(bnd.cbRepetition.isChecked)
        bnd.tvCount.text = "${dictIterator.getIdxWordsCounder(dictIterator.curLang)} / " +
                "${dictIterator.getIdxWordsNumber(dictIterator.curLang)}"
    }

    fun onBtnNext(view: View?) {
        bnd.cbNativeFirst.isEnabled = false
        bnd.cbRepeat.isEnabled = true
        bnd.cbRepetition.isEnabled = false
        bnd.tvTop.hint = ""
        bnd.tvBottom.hint = ""
        if (toShow == 1) {
            //buttonNext.disableProperty().unbind();
            if (!dictIterator.isWordsRemain) {
                bnd.btnNext.isEnabled = false
                return
            }
            dictIterator.nextWord()
            if (!bnd.cbNativeFirst.isChecked) {
                tvLang[0].text = dictIterator.curWord[0]
                tvLang[1].text = dictIterator.curWord[1]
            } else {
                tvLang[0].text = dictIterator.curWord[1]
                tvLang[1].text = dictIterator.curWord[0]
            }
            bnd.tvCount.text = "${dictIterator.getIdxWordsCounder(dictIterator.curLang)} / " +
                    "${dictIterator.getIdxWordsNumber(dictIterator.curLang)}"
            bnd.cbRepeat.isChecked = dictIterator.isToRepeat()
            //hboxLang.setDisable(true);
//            if (checkboxExample.isSelected()) toShow = 2;
//            else toShow = 3;
            toShow = 3
        } else if (toShow == 3) {
            dictIterator.translateCurWord()
            if (!bnd.cbNativeFirst.isChecked) {
                tvLang[0].text = dictIterator.curWord[0]
                tvLang[1].text = dictIterator.curWord[1]
            } else {
                tvLang[0].text = dictIterator.curWord[1]
                tvLang[1].text = dictIterator.curWord[0]
            }
            if (!dictIterator.isWordsRemain) {
                bnd.btnNext.isEnabled = false
                return
            }
            //hboxLang.setDisable(false);
//            if (checkboxExample.isSelected()) toShow = 4;
//            else {
//                toShow = 1;
//                buttonNext.disableProperty().bind(dictIterator.showEmpty);
//            }
            toShow = 1
        }
        //        else if (toShow == 4 && checkboxExample.isSelected()) {
//            dictIterator.showTrExample();
//            toShow = 1;
//            shown = 4;
//            buttonNext.disableProperty().bind(dictIterator.showEmpty);
//        }
        bnd.btnNext.text = getString(R.string.btn_next_next)
    }
}
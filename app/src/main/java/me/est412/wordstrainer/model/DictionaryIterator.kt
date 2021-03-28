package me.est412.wordstrainer.model

import java.util.*

class DictionaryIterator {
    private var dict: Dictionary? = null
    var curLang // 0 = foreing, 1 = russian
            = 0
    private var activeLangs // 0 = foreing, 1 = russian; 2 = both
            = 0

    //индекс для хранения двух списков - по языкам
    private val wordsIndex: MutableList<MutableList<Int>> = ArrayList()
    private var curWordIdx = 0
    private var curWordPos = 0
    private val idxEmpty = BooleanArray(3)
    val isLangRnd = false
    val curWord = arrayOfNulls<String>(2)
    private val curExample = arrayOfNulls<String>(2)
    private val idxWordsNumber = IntArray(3)
    private val idxWordsCounter = IntArray(3)
    private val showExample = false
    var repeat = false
    private val buffer = arrayOfNulls<String>(4)
    private val repBuffer = BooleanArray(2)
    var doRepetition = false

    fun isIdxEmpty(lang: Int): Boolean {
        return idxEmpty[lang]
    }

    fun getCurWord(lang: Int): String? {
        return curWord[lang]
    }

    fun getCurExample(lang: Int): String? {
        return curExample[lang]
    }

    fun getIdxWordsNumber(lang: Int): Int {
        return idxWordsNumber[lang]
    }

    fun getIdxWordsCounder(lang: Int): Int {
        return idxWordsCounter[lang]
    }

    fun showExampleProperty(): Boolean {
        return showExample
    }

    fun setDictionary(dict: Dictionary?) {
        this.dict = dict
        initIndex()
    }

    fun initIndex() {
        if (dict == null) return
        idxWordsNumber[0] = dict!!.getWordsNumber()
        wordsIndex[0].clear()
        wordsIndex[1].clear()
        //wordsIndex.clear();

        //System.out.println("initIndex " + repetition.get() + " " + curLang);
        if (!doRepetition) {
            for (i in 0 until dict!!.getWordsNumber()) {
                wordsIndex[0].add(i)
                wordsIndex[1].add(i)
            }
        } else {
            for (i in 0 until dict!!.getWordsNumber()) {
                if (dict!!.isToRepeat(0, i)) wordsIndex[0].add(i)
                if (dict!!.isToRepeat(1, i)) wordsIndex[1].add(i)
            }
        }
        idxWordsNumber[0] = wordsIndex[0].size
        idxWordsNumber[1] = wordsIndex[1].size
        idxWordsCounter[0] = 0
        idxWordsCounter[1] = 0
    } // initIndex()

    fun isRepetition(): Boolean {
        return doRepetition
    }

    fun setRepetition(repetition: Boolean) {
        this.doRepetition = repetition
        initIndex()
    }

    fun clearCurWord() {
        curWord[0] = ""
        curWord[1] = ""
        hideExamples()
    }

    fun hideExamples() {
        curExample[0] = ""
        curExample[1] = ""
    }

    private fun nextLang() {
        if (activeLangs == 2) switchCurLang() else curLang = activeLangs
    }

    fun nextWord() {
        clearCurWord()
        nextLang()
        curWordIdx = (Math.random() * wordsIndex[curLang].size).toInt()
        curWordPos = wordsIndex[curLang][curWordIdx]
        buffer[0] = dict!!.getWord(0, curWordPos)
        buffer[1] = dict!!.getWord(1, curWordPos)
        buffer[2] = dict!!.getExample(0, curWordPos)
        buffer[3] = dict!!.getExample(1, curWordPos)
        repBuffer[0] = dict!!.isToRepeat(0, curWordPos)
        repBuffer[1] = dict!!.isToRepeat(1, curWordPos)
        curWord[curLang] = buffer[curLang]
        repeat = repBuffer[curLang]
        wordsIndex[curLang].removeAt(curWordIdx)
        idxWordsCounter[0] = idxWordsNumber[0] - wordsIndex[0].size
        idxWordsCounter[1] = idxWordsNumber[1] - wordsIndex[1].size
    }

    fun isToRepeat(): Boolean {
        return repeat
    }

    fun setToRepeat(toRepeat: Boolean) {
        this.repeat = toRepeat
        dict!!.setToRepeat(curLang, curWordPos, toRepeat)
    }

    fun translateCurWord() {
        val lang = if (curLang == 0) 1 else 0
        curWord[lang] = buffer[lang]
    }

    fun showExample() {
        if (buffer[curLang + 2] == "") buffer[curLang + 2] = "---"
        curExample[curLang] = buffer[curLang + 2]
    }

    fun showTrExample() {
        val lang = if (curLang == 0) 1 else 0
        if (buffer[lang + 2] == "") buffer[lang + 2] = "---"
        curExample[lang] = buffer[lang + 2]
    }

    fun setActiveLangs(langs: Int) {
        activeLangs = langs
    }

    fun switchCurLang() {
        if (isLangRnd) do {
            curLang = (Math.random() * 2).toInt()
        } while (isIdxEmpty(curLang)) else {
            curLang = if (curLang == 0) 1 else 0
            if (isIdxEmpty(curLang)) curLang = if (curLang == 0) 1 else 0
        }
    }

    val isWordsRemain: Boolean
        get() = wordsIndex[curLang].size > 0

    init {
        wordsIndex.add(ArrayList())
        wordsIndex.add(ArrayList())
    }
} // class

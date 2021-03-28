package me.est412.wordstrainer.model

import java.io.InputStream
import java.io.OutputStream

interface Dictionary {
    fun open(inputStream: InputStream)
    fun save(outputStream: OutputStream?)
    fun close(outputStream: OutputStream?)
    fun getWordsNumber(): Int
    fun getWord(lang: Int, count: Int): String?
    fun isToRepeat(lang: Int, count: Int): Boolean
    fun setToRepeat(lang: Int, count: Int, inputStream: Boolean)
    fun getExample(lang: Int, count: Int): String?
}
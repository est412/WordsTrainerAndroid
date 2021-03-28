package me.est412.wordstrainer.model

import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.*

class XLSXPoiDictionary(inputStream: InputStream) : Dictionary {
    // количество слов в словаре = к-во строк в файле
    private var wordsNum = 0
    private lateinit var wb //таблица
            : XSSFWorkbook
    private lateinit var ws //вкладка словаря
            : XSSFSheet
    private lateinit var ws1 //вкладка с метками повторения
            : XSSFSheet

    init {
        open(inputStream)
    }

    override fun open(inputStream: InputStream) {
        inputStream.use { fis ->
            wb = XSSFWorkbook(fis)
            if (wb.numberOfSheets < 1) {
                wb.createSheet()
            }
            ws = wb.getSheetAt(0)
            setWordsNum()
            if (wb.numberOfSheets < 2) {
                wb.createSheet()
            }
            ws1 = wb.getSheetAt(1)
        }
    }

    private fun setWordsNum() {
        val lastRowCandidate = ws.lastRowNum
        var cell = ws.getRow(lastRowCandidate).getCell(0)
        if (cell != null && cell.toString().trim { it <= ' ' }.isNotEmpty()) {
            wordsNum = lastRowCandidate + 1
            return
        }
        wordsNum = 0
        while (wordsNum <= lastRowCandidate) {
            cell = ws.getRow(wordsNum).getCell(0)
            if (cell == null || cell.toString().trim { it <= ' ' }.isEmpty()) {
                break
            }
            wordsNum++
        }
    }

    override fun save(outputStream: OutputStream?) {
        outputStream.use { fos -> wb.write(fos) }
    }

    override fun close(outputStream: OutputStream?) {
        save(outputStream)
        wb.close()
    }

    override fun getWordsNumber(): Int = wordsNum

    //выдает нужное слово нужного языка
    override fun getWord(lang: Int, count: Int): String {
        val row = ws.getRow(count) ?: return ""
        val cell = row.getCell(lang) ?: return ""
        return cell.toString()
    }

    //проверяет наличие метки повтора
    override fun isToRepeat(lang: Int, count: Int): Boolean {
        val row = ws1.getRow(count) ?: return false
        val cell = row.getCell(lang) ?: return false
        return "" != cell.toString().trim { it <= ' ' }
    }

    //устанавливает/или очищает метку повтора и сохраняет файл
    override fun setToRepeat(lang: Int, count: Int, inputStream: Boolean) {
        var row = ws1.getRow(count)
        if (row == null) {
            row = ws1.createRow(count)
        }
        // некрасиво, но уж как есть
        var cell = row!!.getCell(lang)
        if (cell == null) {
            cell = row.createCell(lang)
        }
        cell!!.setCellValue(if (inputStream) "1" else "")
    }

    //парсит ячейку с примерами и выдает пример нужного языка
    override fun getExample(lang: Int, count: Int): String {
        val row = ws.getRow(count) ?: return ""
        val cell = row.getCell(2) ?: return ""
        var str = cell.toString()
        if ("" == str) return ""
        val str1 = str.split("\n".toRegex()).toTypedArray() // разделитель между примерами
        var str2: Array<String>
        str = ""
        for (i in str1.indices) {
            str2 = str1[i].split(" — ".toRegex()).toTypedArray() // разделитель между языками
            str = """
                $str${i + 1}: ${str2[lang]}
                
                """.trimIndent()
        }
        return str
    }
}
package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookDao
import com.example.data.local.ChapterDao
import com.example.data.local.LorebookDao
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.Lorebook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import com.example.worker.WorldBuildingWorker
import com.example.worker.OutlineWorker
import com.example.worker.DraftingWorker
import com.example.worker.PolishingWorker
import com.example.worker.CompilationWorker

class StoryEditorViewModel(
    application: Application,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val lorebookDao: LorebookDao
) : AndroidViewModel(application) {

    private val _currentBook = MutableStateFlow<Book?>(null)
    val currentBook: StateFlow<Book?> = _currentBook.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()
    
    private val _lorebooks = MutableStateFlow<List<Lorebook>>(emptyList())
    val lorebooks: StateFlow<List<Lorebook>> = _lorebooks.asStateFlow()

    fun loadBook(bookId: Int) {
        viewModelScope.launch {
            bookDao.getBookByIdFlow(bookId).collect { book ->
                _currentBook.value = book
            }
        }
        viewModelScope.launch {
            chapterDao.getChaptersForBook(bookId).collect { ch ->
                _chapters.value = ch
            }
        }
        viewModelScope.launch {
            lorebookDao.getLorebookForBook(bookId).collect { lore ->
                _lorebooks.value = lore
            }
        }
    }

    fun updateBook(book: Book) {
        viewModelScope.launch {
            bookDao.updateBook(book)
        }
    }
    
    fun updateChapterContent(chapter: Chapter, newContent: String) {
        viewModelScope.launch {
            chapterDao.updateChapter(chapter.copy(content = newContent))
        }
    }

    fun startGenerationPipeline(bookId: Int) {
        viewModelScope.launch {
            val book = bookDao.getBookById(bookId) ?: return@launch
            bookDao.updateBook(book.copy(currentPhase = "Initializing Pipeline"))

            val data = Data.Builder().putInt("BOOK_ID", bookId).build()

            val worldReq = OneTimeWorkRequestBuilder<WorldBuildingWorker>().setInputData(data).addTag("drafting_work_$bookId").build()
            val outlineReq = OneTimeWorkRequestBuilder<OutlineWorker>().setInputData(data).addTag("drafting_work_$bookId").build()
            val draftReq = OneTimeWorkRequestBuilder<DraftingWorker>().setInputData(data).addTag("drafting_work_$bookId").build()
            val polishReq = OneTimeWorkRequestBuilder<PolishingWorker>().setInputData(data).addTag("drafting_work_$bookId").build()
            val compileReq = OneTimeWorkRequestBuilder<CompilationWorker>().setInputData(data).addTag("drafting_work_$bookId").build()

            WorkManager.getInstance(getApplication())
                .beginWith(worldReq)
                .then(outlineReq)
                .then(draftReq)
                .then(polishReq)
                .then(compileReq)
                .enqueue()
        }
    }
}

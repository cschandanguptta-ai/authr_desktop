package com.example.domain.usecase

import com.example.data.model.Chapter
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.Flow

class GetChaptersUseCase(private val repository: AppRepository) {
    operator fun invoke(bookId: Int): Flow<List<Chapter>> {
        return repository.getChaptersForBook(bookId)
    }
}

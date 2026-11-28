package com.example.problemlog.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.problemlog.data.Problem
import com.example.problemlog.data.ProblemRepository
import com.example.problemlog.data.Verdict
import com.example.problemlog.data.local.AppDatabase
import com.example.problemlog.logic.FilterState
import com.example.problemlog.logic.ProblemInput
import com.example.problemlog.logic.ReviewItem
import com.example.problemlog.logic.SaveResult
import com.example.problemlog.logic.Stats
import com.example.problemlog.logic.applyFilterAndSort
import com.example.problemlog.logic.buildReviewList
import com.example.problemlog.logic.computeStats
import com.example.problemlog.logic.toProblem
import com.example.problemlog.logic.validateInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_QUERY = "query"

class ProblemsViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = ProblemRepository(AppDatabase.get(application).problemDao())

    // null until room sends the first list
    private val loadedProblems: StateFlow<List<Problem>?> =
        repository.problems.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isLoading: StateFlow<Boolean> = loadedProblems
        .map { it == null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val allProblems: StateFlow<List<Problem>> = loadedProblems
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // search text lives in saved state so it survives even process death
    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    private val _filter = MutableStateFlow(FilterState())
    val filter: StateFlow<FilterState> = _filter.asStateFlow()

    // one short message for the snackbar, null when nothing to show
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // problems + query + filter -> one list for the screen
    val visibleProblems: StateFlow<List<Problem>> =
        combine(allProblems, query, _filter) { list, q, f ->
            applyFilterAndSort(list, q, f, System.currentTimeMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTags: StateFlow<List<String>> = allProblems
        .map { list -> list.flatMap { it.tags }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<Stats> = allProblems
        .map { computeStats(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), computeStats(emptyList()))

    val reviewItems: StateFlow<List<ReviewItem>> = allProblems
        .map { buildReviewList(it, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        savedStateHandle[KEY_QUERY] = value
    }

    fun onFilterChange(newFilter: FilterState) {
        _filter.value = newFilter
    }

    // clear filters but keep the chosen sort
    fun clearFilters() {
        val old = _filter.value
        _filter.value = FilterState(primarySort = old.primarySort, secondarySort = old.secondarySort)
    }

    fun messageShown() {
        _message.value = null
    }

    fun logAttempt(problemId: String, verdict: Verdict, minutes: Int) {
        viewModelScope.launch {
            repository.logAttempt(problemId, verdict, minutes)
        }
    }

    fun undoLastAttempt(problemId: String) {
        viewModelScope.launch {
            repository.undoLastAttempt(problemId)
            _message.value = "Last attempt removed"
        }
    }

    fun deleteProblem(problemId: String) {
        viewModelScope.launch {
            repository.delete(problemId)
            _message.value = "$problemId deleted"
        }
    }

    fun loadSamples() {
        viewModelScope.launch {
            repository.loadSamples()
            _message.value = "Sample problems loaded"
        }
    }

    fun saveProblem(input: ProblemInput, editingId: String?): SaveResult {
        val existingIds = allProblems.value.map { it.id }.toSet()
        val error = validateInput(input, existingIds, editingId)
        if (error != null) return SaveResult.Invalid(error)

        val problem = input.toProblem()
        viewModelScope.launch {
            repository.upsert(problem)
            _message.value = if (editingId == null) "${problem.id} added" else "${problem.id} saved"
        }
        return SaveResult.Saved
    }
}

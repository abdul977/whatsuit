package com.example.whatsuit.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whatsuit.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Example usage of SearchUtil in a ViewModel
 */
class SearchViewModel(database: AppDatabase) : ViewModel() {
    
    private val _searchResults = MutableStateFlow<Map<SearchUtil.ResultType, List<SearchUtil.SearchResult>>>(emptyMap())
    val searchResults: StateFlow<Map<SearchUtil.ResultType, List<SearchUtil.SearchResult>>> = _searchResults
    
    init {
        // Initialize SearchUtil with database
        SearchUtil.initialize(database)
    }
    
    /**
     * Perform a search with optional filters
     */
    fun search(
        query: String,
        types: List<SearchUtil.ResultType>? = null,
        limit: Int = 20,
        offset: Int = 0
    ) {
        viewModelScope.launch {
            try {
                val params = SearchUtil.SearchParams(
                    query = query,
                    types = types,
                    limit = limit,
                    offset = offset
                )
                
                val results = SearchUtil.search(params)
                _searchResults.value = results
                
            } catch (e: Exception) {
                // Handle errors appropriately
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Get search suggestions based on prefix
     */
    fun getSuggestions(prefix: String) {
        viewModelScope.launch {
            try {
                val suggestions = SearchUtil.getSuggestions(prefix)
                // Handle suggestions (e.g. update UI)
            } catch (e: Exception) {
                // Handle errors appropriately
                e.printStackTrace()
            }
        }
    }
}

/* Example Activity/Fragment usage:

class SearchActivity : AppCompatActivity() {
    private lateinit var viewModel: SearchViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(applicationContext)
        viewModel = SearchViewModel(database)
        
        // Collect search results
        lifecycleScope.launch {
            viewModel.searchResults.collect { results ->
                // Update UI with results
                results.forEach { (type, items) ->
                    when (type) {
                        SearchUtil.ResultType.NOTIFICATION -> {
                            // Handle notification results
                            items.forEach { result ->
                                val notification = result as SearchUtil.NotificationResult
                                // Display notification
                            }
                        }
                        SearchUtil.ResultType.CONVERSATION -> {
                            // Handle conversation results
                            items.forEach { result ->
                                val conversation = result as SearchUtil.ConversationResult
                                // Display conversation
                            }
                        }
                        SearchUtil.ResultType.TEMPLATE -> {
                            // Handle template results
                            items.forEach { result ->
                                val template = result as SearchUtil.TemplateResult
                                // Display template
                            }
                        }
                    }
                }
            }
        }
        
        // Perform search
        viewModel.search("query")
        
        // Search with filters
        viewModel.search(
            query = "query",
            types = listOf(SearchUtil.ResultType.NOTIFICATION, SearchUtil.ResultType.CONVERSATION),
            limit = 10
        )
        
        // Get suggestions
        viewModel.getSuggestions("search")
    }
}
*/
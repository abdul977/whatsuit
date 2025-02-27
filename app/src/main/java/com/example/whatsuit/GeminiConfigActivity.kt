package com.example.whatsuit

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsuit.adapter.PromptTemplateAdapter
import com.example.whatsuit.data.AppDatabase
import com.example.whatsuit.data.GeminiConfig
import com.example.whatsuit.data.PromptTemplate
import com.example.whatsuit.service.GeminiService
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class GeminiConfigActivity : AppCompatActivity() {
    private companion object {
        private const val TAG = "GeminiConfigActivity"
    }

    private lateinit var apiKeyLayout: TextInputLayout
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var testButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var maxHistoryInput: TextInputEditText
    private lateinit var promptTemplateInput: TextInputEditText
    private lateinit var templateNameInput: TextInputEditText
    private lateinit var templatesRecyclerView: RecyclerView
    
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val geminiDao by lazy { database.geminiDao() }
    private val geminiService by lazy { GeminiService(this) }
    
    private lateinit var templateAdapter: PromptTemplateAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gemini_config)
        
        // Setup toolbar
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Gemini API Configuration"
        }
        
        // Initialize views
        initializeViews()
        setupListeners()
        setupTemplatesRecyclerView()
        
        // Load current config
        loadCurrentConfig()
    }

    private fun initializeViews() {
        apiKeyLayout = findViewById(R.id.apiKeyLayout)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        testButton = findViewById(R.id.testButton)
        saveButton = findViewById(R.id.saveButton)
        maxHistoryInput = findViewById(R.id.maxHistoryInput)
        promptTemplateInput = findViewById(R.id.promptTemplateInput)
        templateNameInput = findViewById(R.id.templateNameInput)
        templatesRecyclerView = findViewById(R.id.templatesRecyclerView)
    }

    private fun setupListeners() {
        apiKeyInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                apiKeyLayout.error = if (s.isNullOrBlank()) "API key is required" else null
                updateButtonStates()
            }
        })
        
        testButton.setOnClickListener { testApiKey() }
        saveButton.setOnClickListener { saveConfig() }
    }

    private fun setupTemplatesRecyclerView() {
        templateAdapter = PromptTemplateAdapter(
            onTemplateSelected = { template ->
                lifecycleScope.launch {
                    geminiDao.setActiveTemplate(template.id)
                    templateAdapter.setActiveTemplate(template.id)
                    promptTemplateInput.setText(template.template)
                    templateNameInput.setText(template.name)
                }
            },
            onTemplateEdit = { template ->
                promptTemplateInput.setText(template.template)
                templateNameInput.setText(template.name)
            },
            onTemplateDelete = { template ->
                showDeleteTemplateConfirmation(template)
            }
        )

        templatesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GeminiConfigActivity)
            adapter = templateAdapter
        }

        // Observe templates
        geminiDao.getAllTemplates().observe(this) { templates ->
            templateAdapter.submitList(templates)
            
            // Get active template
            lifecycleScope.launch {
                val activeTemplate = geminiDao.getActiveTemplate()
                activeTemplate?.let { templateAdapter.setActiveTemplate(it.id) }
            }
        }
    }

    private fun showDeleteTemplateConfirmation(template: PromptTemplate) {
        AlertDialog.Builder(this)
            .setTitle("Delete Template")
            .setMessage("Are you sure you want to delete '${template.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    geminiDao.deleteTemplate(template)
                    Toast.makeText(this@GeminiConfigActivity, "Template deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadCurrentConfig() {
        lifecycleScope.launch {
            try {
                val config = geminiDao.getConfig()
                if (config != null) {
                    apiKeyInput.setText(config.apiKey)
                    maxHistoryInput.setText(config.maxHistoryPerThread.toString())
                }

                val template = geminiDao.getActiveTemplate()
                if (template != null) {
                    promptTemplateInput.setText(template.template)
                    templateNameInput.setText(template.name)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading config", e)
                Toast.makeText(this@GeminiConfigActivity, "Error loading configuration", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun testApiKey() {
        val apiKey = apiKeyInput.text?.toString()
        if (apiKey.isNullOrBlank()) {
            apiKeyLayout.error = "API key is required"
            return
        }

        testButton.isEnabled = false
        testButton.text = "Testing..."

        lifecycleScope.launch {
            try {
                val testConfig = GeminiConfig.createDefault(apiKey)
                geminiDao.insertConfig(testConfig)
                geminiService.initialize()
                
                var success = false
                geminiService.generateReply(
                    notificationId = -1,
                    message = "Test message",
                    object : GeminiService.ResponseCallback {
                        override fun onPartialResponse(text: String) {}
                        override fun onComplete(fullResponse: String) {
                            success = true
                        }
                        override fun onError(error: Throwable) {
                            throw error
                        }
                    }
                )

                if (success) {
                    Toast.makeText(this@GeminiConfigActivity, "API key is valid!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "API key test failed", e)
                Toast.makeText(this@GeminiConfigActivity, "Invalid API key: ${e.message}", Toast.LENGTH_SHORT).show()
                apiKeyLayout.error = "Invalid API key"
            } finally {
                testButton.isEnabled = true
                testButton.text = "Test API Key"
            }
        }
    }

    private fun saveConfig() {
        val apiKey = apiKeyInput.text?.toString()
        if (apiKey.isNullOrBlank()) {
            apiKeyLayout.error = "API key is required"
            return
        }

        val maxHistory = maxHistoryInput.text?.toString()?.toIntOrNull() ?: 10
        val templateName = templateNameInput.text?.toString()
        val templateContent = promptTemplateInput.text?.toString()

        lifecycleScope.launch {
            try {
                // Save config
                val config = GeminiConfig(
                    apiKey = apiKey,
                    maxHistoryPerThread = maxHistory
                )
                geminiDao.insertConfig(config)

                // Save template if provided
                if (!templateName.isNullOrBlank() && !templateContent.isNullOrBlank()) {
                    val template = PromptTemplate(
                        name = templateName,
                        template = templateContent
                    )
                    val templateId = geminiDao.insertTemplate(template)
                    geminiDao.setActiveTemplate(templateId)
                }

                // Initialize service with new config
                geminiService.initialize()

                Toast.makeText(this@GeminiConfigActivity, "Configuration saved", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving config", e)
                Toast.makeText(this@GeminiConfigActivity, "Error saving configuration", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateButtonStates() {
        val apiKey = apiKeyInput.text?.toString()
        val isValid = !apiKey.isNullOrBlank() && apiKeyLayout.error == null
        
        testButton.isEnabled = isValid
        saveButton.isEnabled = isValid
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
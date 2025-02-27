package com.example.whatsuit.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsuit.R
import com.example.whatsuit.data.PromptTemplate

class PromptTemplateAdapter(
    private val onTemplateSelected: (PromptTemplate) -> Unit,
    private val onTemplateEdit: (PromptTemplate) -> Unit,
    private val onTemplateDelete: (PromptTemplate) -> Unit
) : ListAdapter<PromptTemplate, PromptTemplateAdapter.ViewHolder>(TemplateDiffCallback()) {

    private var activeTemplateId: Long? = null

    class ViewHolder(
        view: View,
        private val onTemplateSelected: (PromptTemplate) -> Unit,
        private val onTemplateEdit: (PromptTemplate) -> Unit,
        private val onTemplateDelete: (PromptTemplate) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val nameText: TextView = view.findViewById(R.id.templateNameText)
        private val previewText: TextView = view.findViewById(R.id.templatePreviewText)
        private val radioButton: RadioButton = view.findViewById(R.id.templateRadioButton)
        private val editButton: ImageButton = view.findViewById(R.id.editTemplateButton)
        private val deleteButton: ImageButton = view.findViewById(R.id.deleteTemplateButton)
        
        private var currentTemplate: PromptTemplate? = null

        init {
            view.setOnClickListener {
                currentTemplate?.let { template ->
                    radioButton.isChecked = true
                    onTemplateSelected(template)
                }
            }

            radioButton.setOnClickListener {
                currentTemplate?.let { template ->
                    onTemplateSelected(template)
                }
            }

            editButton.setOnClickListener {
                currentTemplate?.let { template ->
                    onTemplateEdit(template)
                }
            }

            deleteButton.setOnClickListener {
                currentTemplate?.let { template ->
                    onTemplateDelete(template)
                }
            }
        }

        fun bind(template: PromptTemplate, isActive: Boolean) {
            currentTemplate = template
            nameText.text = template.name
            
            // Show preview of template (first 100 chars)
            val preview = template.template.take(100).let {
                if (template.template.length > 100) "$it..." else it
            }
            previewText.text = preview
            
            radioButton.isChecked = isActive
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prompt_template, parent, false)
        return ViewHolder(view, onTemplateSelected, onTemplateEdit, onTemplateDelete)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val template = getItem(position)
        holder.bind(template, template.id == activeTemplateId)
    }

    fun setActiveTemplate(templateId: Long) {
        val oldActiveId = activeTemplateId
        activeTemplateId = templateId
        
        if (oldActiveId != null) {
            notifyItemChanged(currentList.indexOfFirst { it.id == oldActiveId })
        }
        notifyItemChanged(currentList.indexOfFirst { it.id == templateId })
    }

    private class TemplateDiffCallback : DiffUtil.ItemCallback<PromptTemplate>() {
        override fun areItemsTheSame(oldItem: PromptTemplate, newItem: PromptTemplate): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PromptTemplate, newItem: PromptTemplate): Boolean {
            return oldItem == newItem
        }
    }
}
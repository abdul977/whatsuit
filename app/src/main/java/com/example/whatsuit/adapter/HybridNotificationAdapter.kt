package com.example.whatsuit.adapter

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsuit.MainActivity
import com.example.whatsuit.NotificationDetailActivity
import com.example.whatsuit.R
import com.example.whatsuit.data.NotificationEntity
import com.example.whatsuit.util.AutoReplyManager
import com.google.android.material.chip.Chip

class HybridNotificationAdapter(
    private val packageManager: PackageManager,
    private val autoReplyManager: AutoReplyManager
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Any>()
    private var activePopupMenu: PopupMenu? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_NOTIFICATION = 1
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_app_header, parent, false)
                AppHeaderViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_notification, parent, false)
                NotificationViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is AppHeader -> {
                holder as AppHeaderViewHolder
                holder.bind(item)
                try {
                    val icon = packageManager.getApplicationIcon(item.packageName)
                    holder.appIcon.setImageDrawable(icon)
                } catch (e: PackageManager.NameNotFoundException) {
                    holder.appIcon.setImageResource(R.drawable.ic_app_placeholder)
                }
            }
            is NotificationEntity -> {
                holder as NotificationViewHolder
                holder.bind(item)
                holder.menuButton.setOnClickListener { v -> showPopupMenu(v, item) }
                updateAutoReplyStatusAsync(holder, item)
                
                holder.itemView.setOnClickListener { v ->
                    val intent = Intent(v.context, NotificationDetailActivity::class.java).apply {
                        putExtra("notification_id", item.id)
                    }
                    v.context.startActivity(intent)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
        if (items[position] is AppHeader) VIEW_TYPE_HEADER else VIEW_TYPE_NOTIFICATION

    override fun getItemId(position: Int): Long {
        return when (val item = items[position]) {
            is AppHeader -> item.hashCode().toLong()
            is NotificationEntity -> item.id
            else -> position.toLong()
        }
    }

    fun updateNotifications(notifications: Map<String, List<NotificationEntity>>) {
        val newItems = mutableListOf<Any>()
        
        notifications.forEach { (appName, appNotifications) ->
            // Add app header
            val packageName = appNotifications.firstOrNull()?.packageName ?: ""
            newItems.add(AppHeader(appName, packageName, appNotifications.size))
            
            // Add all notifications for this app
            newItems.addAll(appNotifications)
        }

        // Calculate diff and update
        val diffCallback = NotificationDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    private fun showPopupMenu(view: View, notification: NotificationEntity) {
        activePopupMenu?.dismiss()
        
        PopupMenu(view.context, view).apply {
            activePopupMenu = this
            MenuInflater(view.context).inflate(R.menu.notification_item_menu, menu)
            
            val autoReplyItem = menu.findItem(R.id.action_toggle_auto_reply)
            autoReplyItem.isEnabled = false
            
            setOnDismissListener { activePopupMenu = null }
            
            val info = extractIdentifierInfo(notification)
            
            autoReplyManager.isAutoReplyDisabled(
                notification.packageName,
                info.phoneNumber,
                info.titlePrefix
            ) { isDisabled ->
                mainHandler.post {
                    autoReplyItem.title = if (isDisabled) "Enable Auto-Reply" else "Disable Auto-Reply"
                    autoReplyItem.isEnabled = true
                }
            }

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_toggle_auto_reply -> {
                        toggleAutoReply(notification)
                        true
                    }
                    R.id.action_view_details -> {
                        val intent = Intent(view.context, NotificationDetailActivity::class.java).apply {
                            putExtra("notification_id", notification.id)
                            putExtra("show_conversations_tab", true)
                        }
                        view.context.startActivity(intent)
                        true
                    }
                    R.id.action_view_history -> {
                        (view.context as? MainActivity)?.showConversationHistory(notification)
                        true
                    }
                    else -> false
                }
            }

            show()
        }
    }

    private fun toggleAutoReply(notification: NotificationEntity) {
        val info = extractIdentifierInfo(notification)
        autoReplyManager.toggleAutoReply(
            notification.packageName,
            info.phoneNumber,
            info.titlePrefix
        ) { _ ->
            mainHandler.post { notifyDataSetChanged() }
        }
    }

    private fun updateAutoReplyStatusAsync(holder: NotificationViewHolder, notification: NotificationEntity) {
        holder.autoReplyStatusChip.apply {
            isEnabled = false
            text = "Loading..."
            visibility = View.VISIBLE
        }

        val info = extractIdentifierInfo(notification)
        
        autoReplyManager.isAutoReplyDisabled(
            notification.packageName,
            info.phoneNumber,
            info.titlePrefix
        ) { isDisabled ->
            mainHandler.post {
                holder.autoReplyStatusChip.apply {
                    text = if (isDisabled) "Auto-reply disabled" else "Auto-reply enabled"
                    isEnabled = true
                    setChipBackgroundColorResource(
                        if (isDisabled) R.color.md_theme_errorContainer
                        else R.color.md_theme_primaryContainer
                    )
                    setTextColor(
                        context.getColor(
                            if (isDisabled) R.color.md_theme_onErrorContainer
                            else R.color.md_theme_onPrimaryContainer
                        )
                    )
                }
            }
        }
    }

    private fun extractIdentifierInfo(notification: NotificationEntity): IdentifierInfo {
        var phoneNumber = ""
        var titlePrefix = ""
        
        if (notification.packageName.contains("whatsapp") && 
            notification.title?.matches(Regex(".*[0-9+].*")) == true) {
            try {
                val content = notification.title
                val extracted = content.replace(Regex("[^0-9+\\-]"), "")
                val fullNumber = extracted.replace(Regex("[^0-9]"), "")
                // Take first 11 digits if available
                phoneNumber = if (fullNumber.length >= 11) {
                    fullNumber.substring(0, 11)
                } else {
                    fullNumber
                }
            } catch (e: Exception) {
                // Fallback to title prefix
                titlePrefix = notification.title?.let { 
                    if (it.length >= 5) it.substring(0, 5) else it 
                } ?: ""
            }
        } else {
            titlePrefix = notification.title?.let { 
                if (it.length >= 5) it.substring(0, 5) else it 
            } ?: ""
        }
        
        return IdentifierInfo(phoneNumber, titlePrefix)
    }
    
    fun cleanup() {
        activePopupMenu?.dismiss()
        activePopupMenu = null
    }

    private data class IdentifierInfo(
        val phoneNumber: String,
        val titlePrefix: String
    )

    internal data class AppHeader(
        val appName: String,
        val packageName: String,
        val count: Int
    )

    class AppHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appNameText: TextView = itemView.findViewById(R.id.appName)
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val notificationCount: Chip = itemView.findViewById(R.id.notificationCount)

        internal fun bind(header: AppHeader) {
            appNameText.text = header.appName
            notificationCount.text = header.count.toString()
        }
    }

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.notificationTitle)
        private val contentView: TextView = itemView.findViewById(R.id.notificationContent)
        val menuButton: ImageButton = itemView.findViewById(R.id.menuButton)
        val autoReplyStatusChip: Chip = itemView.findViewById(R.id.autoReplyStatusChip)

        fun bind(notification: NotificationEntity) {
            titleView.text = notification.title
            contentView.text = notification.content
        }
    }

    private class NotificationDiffCallback(
        private val oldItems: List<Any>,
        private val newItems: List<Any>
    ) : DiffUtil.Callback() {
        
        override fun getOldListSize(): Int = oldItems.size
        
        override fun getNewListSize(): Int = newItems.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldItems[oldItemPosition]
            val newItem = newItems[newItemPosition]

            if (oldItem::class != newItem::class) return false

            return when {
                oldItem is AppHeader && newItem is AppHeader ->
                    oldItem.packageName == newItem.packageName
                oldItem is NotificationEntity && newItem is NotificationEntity ->
                    oldItem.id == newItem.id
                else -> false
            }
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldItems[oldItemPosition]
            val newItem = newItems[newItemPosition]

            return when {
                oldItem is AppHeader && newItem is AppHeader ->
                    oldItem == newItem
                oldItem is NotificationEntity && newItem is NotificationEntity ->
                    oldItem.title == newItem.title &&
                    oldItem.content == newItem.content
                else -> false
            }
        }
    }
}

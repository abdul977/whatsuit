package com.example.whatsuit.adapter

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsuit.R
import com.example.whatsuit.data.NotificationEntity

class CategorizedNotificationAdapter(private val packageManager: PackageManager) : 
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    private val categorizedNotifications = mutableMapOf<String, List<NotificationEntity>>()
    private val appNames = mutableListOf<String>()

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_NOTIFICATIONS = 1
    }

    fun setCategorizedNotifications(notifications: Map<String, List<NotificationEntity>>) {
        categorizedNotifications.clear()
        categorizedNotifications.putAll(notifications)
        appNames.clear()
        appNames.addAll(notifications.keys)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_app_header, parent, false)
                AppHeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_notification_group, parent, false)
                NotificationGroupViewHolder(view, packageManager)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val appName = appNames[position / 2]
        val notifications = categorizedNotifications[appName] ?: return

        when (holder) {
            is AppHeaderViewHolder -> {
                holder.bind(appName, notifications.size)
                try {
                    val packageName = notifications.firstOrNull()?.packageName
                    if (packageName != null) {
                        val icon = packageManager.getApplicationIcon(packageName)
                        holder.appIcon.setImageDrawable(icon)
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    holder.appIcon.setImageResource(R.drawable.ic_app_placeholder)
                }
            }
            is NotificationGroupViewHolder -> {
                holder.bind(notifications)
            }
        }
    }

    override fun getItemCount(): Int = appNames.size * 2

    override fun getItemViewType(position: Int): Int {
        return if (position % 2 == 0) VIEW_TYPE_HEADER else VIEW_TYPE_NOTIFICATIONS
    }

    class AppHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appNameText: TextView = itemView.findViewById(R.id.appName)
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val notificationCount: TextView = itemView.findViewById(R.id.notificationCount)

        fun bind(appName: String, count: Int) {
            appNameText.text = appName
            notificationCount.text = count.toString()
        }
    }

    class NotificationGroupViewHolder(
        itemView: View,
        packageManager: PackageManager
    ) : RecyclerView.ViewHolder(itemView) {
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.notificationsRecyclerView)
        private val adapter = RelatedNotificationsAdapter()

        init {
            recyclerView.layoutManager = LinearLayoutManager(itemView.context)
            recyclerView.adapter = adapter
        }

        fun bind(notifications: List<NotificationEntity>) {
            adapter.setNotifications(notifications)
        }
    }
}

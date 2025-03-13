package com.example.whatsuit.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.whatsuit.fragment.ConversationsFragment
import com.example.whatsuit.fragment.NotificationsFragment

class DetailPagerAdapter(
    activity: FragmentActivity
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> NotificationsFragment.newInstance()
            1 -> ConversationsFragment.newInstance("default")
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }

    companion object {
        const val POSITION_NOTIFICATIONS = 0
        const val POSITION_CONVERSATIONS = 1
    }
}

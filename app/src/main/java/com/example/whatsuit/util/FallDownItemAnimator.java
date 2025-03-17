package com.example.whatsuit.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

public class FallDownItemAnimator extends DefaultItemAnimator {
    
    private static final float INITIAL_Y_TRANSLATION = -100f;
    private static final float ALPHA_START = 0f;
    private static final long ANIMATION_DURATION = 400;
    
    @Override
    public boolean animateAdd(RecyclerView.ViewHolder holder) {
        // Reset view properties
        holder.itemView.setAlpha(ALPHA_START);
        holder.itemView.setTranslationY(INITIAL_Y_TRANSLATION);
        
        // Create and start animation
        ObjectAnimator translateY = ObjectAnimator.ofFloat(holder.itemView, View.TRANSLATION_Y, INITIAL_Y_TRANSLATION, 0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(holder.itemView, View.ALPHA, ALPHA_START, 1f);
        
        translateY.setDuration(ANIMATION_DURATION);
        alpha.setDuration(ANIMATION_DURATION);
        
        translateY.setInterpolator(new DecelerateInterpolator(2.5f));
        
        translateY.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                dispatchAddStarting(holder);
            }
            
            @Override
            public void onAnimationEnd(Animator animation) {
                dispatchAddFinished(holder);
            }
        });
        
        translateY.start();
        alpha.start();
        
        return true;
    }
    
    @Override
    public boolean animateChange(@NonNull RecyclerView.ViewHolder oldHolder, @NonNull RecyclerView.ViewHolder newHolder, 
                               int fromX, int fromY, int toX, int toY) {
        // For item changes, use a subtle bounce effect
        if (oldHolder == newHolder) {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(newHolder.itemView, View.SCALE_X, 0.9f, 1.0f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(newHolder.itemView, View.SCALE_Y, 0.9f, 1.0f);
            
            scaleX.setDuration(300);
            scaleY.setDuration(300);
            
            scaleX.start();
            scaleY.start();
        }
        
        return super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY);
    }

    /**
     * Sets up the RecyclerView with staggered animation delays
     * @param recyclerView The RecyclerView to animate
     */
    public static void setAnimation(RecyclerView recyclerView) {
        FallDownItemAnimator animator = new FallDownItemAnimator();
        animator.setChangeDuration(300);
        animator.setMoveDuration(300);
        animator.setRemoveDuration(300);
        recyclerView.setItemAnimator(animator);
    }

    @Override
    public long getAddDuration() {
        return ANIMATION_DURATION;
    }
}

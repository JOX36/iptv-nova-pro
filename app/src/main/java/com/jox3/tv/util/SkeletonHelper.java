package com.jox3.tv.util;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import com.jox3.tv.R;

public class SkeletonHelper {

    /**
     * Muestra N skeletons animados en un LinearLayout horizontal
     */
    public static void showSkeletons(LinearLayout row, int count) {
        row.removeAllViews();
        for (int i = 0; i < count; i++) {
            View skeleton = LayoutInflater.from(row.getContext())
                .inflate(R.layout.skeleton_item, row, false);
            row.addView(skeleton);
            animateSkeleton(skeleton, i * 100L);
        }
    }

    /**
     * Aplica animación pulse a un skeleton
     */
    private static void animateSkeleton(View view, long delay) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, "alpha", 0.3f, 1.0f);
        anim.setDuration(900);
        anim.setStartDelay(delay);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.start();
    }

    /**
     * Detiene animaciones y limpia el layout
     */
    public static void clearSkeletons(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            v.clearAnimation();
        }
        row.removeAllViews();
    }
}

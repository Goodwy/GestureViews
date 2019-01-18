package com.alexvasilkov.gestures.sample.demo.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.alexvasilkov.android.commons.ui.Views;

@SuppressWarnings({"SameParameterValue", "unused"}) // Public API
public class DecorUtils {

    private DecorUtils() {
    }

    public static void paddingForStatusBar(View view, boolean isFixedSize) {
        int height = getStatusBarHeight(view.getContext());

        view.setPadding(view.getPaddingLeft(), view.getPaddingTop() + height,
                view.getPaddingRight(), view.getPaddingBottom());

        if (isFixedSize) {
            Views.getParams(view).height += height;
        }
    }

    public static void marginForStatusBar(View view) {
        Views.getMarginParams(view).topMargin += getStatusBarHeight(view.getContext());
    }

    public static void paddingForNavBar(View view) {
        int height = getNavBarHeight(view.getContext());
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                view.getPaddingRight(), view.getPaddingBottom() + height);
    }

    public static void marginForNavBar(View view) {
        Views.getMarginParams(view).bottomMargin += getNavBarHeight(view.getContext());
    }

    public static int getStatusBarHeight(Context context) {
        return getDimenSize(context, "status_bar_height");
    }

    private static int getNavBarHeight(Context context) {
        boolean hasMenuKey = ViewConfiguration.get(context).hasPermanentMenuKey();
        boolean hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);
        boolean hasNavBar = !hasMenuKey && !hasBackKey;

        if (hasNavBar) {
            boolean isPortrait = context.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT;

            boolean isTablet = (context.getResources().getConfiguration().screenLayout
                    & Configuration.SCREENLAYOUT_SIZE_MASK)
                    >= Configuration.SCREENLAYOUT_SIZE_LARGE;

            String key = isPortrait ? "navigation_bar_height"
                    : (isTablet ? "navigation_bar_height_landscape" : null);

            return key == null ? 0 : getDimenSize(context, key);
        } else {
            return 0;
        }
    }

    private static int getDimenSize(Context context, String key) {
        int resourceId = context.getResources().getIdentifier(key, "dimen", "android");
        return resourceId > 0 ? context.getResources().getDimensionPixelSize(resourceId) : 0;
    }

}

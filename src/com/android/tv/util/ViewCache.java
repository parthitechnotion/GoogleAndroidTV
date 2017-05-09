package com.android.tv.util;

import android.util.SparseArray;
import android.view.View;

import java.util.ArrayList;

/**
 * A cache for the views.
 */
public class ViewCache {
    private final static SparseArray<ArrayList<View>> mViews = new SparseArray();

    private static ViewCache sViewCache;

    private ViewCache() { }

    /**
     * Returns an instance of the view cache.
     */
    public static ViewCache getInstance() {
        if (sViewCache == null) {
            return new ViewCache();
        } else {
            return sViewCache;
        }
    }

    /**
     * Returns if the view cache is empty.
     */
    public boolean isEmpty() {
        return mViews.size() == 0;
    }

    /**
     * Stores a view into this view cache.
     */
    public void putView(int resId, View view) {
        ArrayList<View> views = mViews.get(resId);
        if (views == null) {
            views = new ArrayList();
            mViews.put(resId, views);
        }
        views.add(view);
    }

    /**
     * Returns the view for specific resource id.
     */
    public View getView(int resId) {
        ArrayList<View> views = mViews.get(resId);
        if (views != null && !views.isEmpty()) {
            View view = views.remove(views.size() - 1);
            if (views.isEmpty()) {
                mViews.remove(resId);
            }
            return view;
        } else {
            return null;
        }
    }

    /**
     * Clears the view cache.
     */
    public void clear() {
        mViews.clear();
    }
}

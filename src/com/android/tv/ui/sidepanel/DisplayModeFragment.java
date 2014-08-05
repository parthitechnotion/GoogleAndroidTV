package com.android.tv.ui.sidepanel;

import com.android.tv.R;
import com.android.tv.data.DisplayMode;

import java.util.ArrayList;
import java.util.List;

public class DisplayModeFragment extends SideFragment {
    @Override
    protected String getTitle() {
        return getString(R.string.display_mode_option_title);
    }

    @Override
    protected List<Item> getItemList() {
        ArrayList<Item> items = new ArrayList<>();
        for (int i = 0; i < DisplayMode.SIZE_OF_RATIO_TYPES; ++i) {
            final int displayMode = i;
            items.add(new RadioButtonItem(DisplayMode.getLabel(i, getActivity())) {
                @Override
                protected void onSelected() {
                    super.onSelected();
                    getTvActivity().setDisplayMode(displayMode, true);
                }
            });
        }
        return items;
    }
}
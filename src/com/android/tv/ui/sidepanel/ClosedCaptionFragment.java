package com.android.tv.ui.sidepanel;

import android.widget.Toast;

import com.android.tv.R;

import java.util.ArrayList;
import java.util.List;

public class ClosedCaptionFragment extends SideFragment {
    @Override
    protected String getTitle() {
        return getString(R.string.closed_caption_option_title);
    }

    @Override
    protected List<Item> getItemList() {
        ArrayList<Item> items = new ArrayList<>();
        items.add(new RadioButtonItem(getString(R.string.option_item_on)) {
            @Override
            protected void onSelected() {
                super.onSelected();
                getTvActivity().setClosedCaptionEnabled(false, true);
                setClosedCaptionEnabled(true);
            }
        });
        items.add(new RadioButtonItem(getString(R.string.option_item_off)) {
            @Override
            protected void onSelected() {
                super.onSelected();
                setClosedCaptionEnabled(false);
            }
        });
        return items;
    }

    private void setClosedCaptionEnabled(boolean enabled) {
        getTvActivity().setClosedCaptionEnabled(enabled, true);
        Toast.makeText(getActivity(), R.string.not_implemented_yet, Toast.LENGTH_SHORT).show();
    }
}
package com.android.tv.ui.sidepanel;

import android.app.Fragment;
import android.os.Bundle;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.TvActivity;

import java.util.List;

public abstract class SideFragment extends Fragment {
    public SideFragment() { }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.option_fragment, container, false);

        TextView textView = (TextView) view.findViewById(R.id.side_panel_title);
        textView.setText(getTitle());

        VerticalGridView listView = (VerticalGridView) view.findViewById(R.id.side_panel_list);
        listView.setAdapter(new ItemAdapter(inflater, getItemList()));
        listView.requestFocus();

        // TODO find a better way to do this
        if (getFragmentManager().getBackStackEntryCount() != 0) {
            view.findViewById(R.id.side_panel_shadow).setVisibility(View.GONE);
        }

        return view;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // TODO find a better way to do this
        if (getFragmentManager().getBackStackEntryCount() == 0) {
            TvActivity tvActivity = getTvActivity();
            tvActivity.onSideFragmentCanceled(BaseSideFragment.INITIATOR_UNKNOWN);
            tvActivity.hideOverlays(false, false, true);
        }
    }

    protected TvActivity getTvActivity() {
        return (TvActivity) getActivity();
    }

    protected void notifyDataSetChanged() {
        VerticalGridView listView = (VerticalGridView) getView().findViewById(R.id.side_panel_list);
        listView.getAdapter().notifyDataSetChanged();
    }

    protected abstract String getTitle();
    protected abstract List<Item> getItemList();

    private static class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {
        private final LayoutInflater mLayoutInflater;
        private final List<Item> mItems;

        private ItemAdapter(LayoutInflater layoutInflater, List<Item> items) {
            mLayoutInflater = layoutInflater;
            mItems = items;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = mLayoutInflater.inflate(viewType, parent, false);
            final ViewHolder holder = new ViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (holder.item instanceof RadioButtonItem) {
                        clearRadioGroup(holder.item);
                    }
                    holder.item.onSelected();
                }
            });
            view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean focusGained) {
                    if (focusGained) {
                        holder.item.onFocused();
                    }
                }
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.item = getItem(position);
            holder.item.bind(holder.itemView);
        }

        @Override
        public void onViewRecycled(ViewHolder holder) {
            holder.item.unbind();
            holder.item = null;
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).getResourceId();
        }

        @Override
        public int getItemCount() {
            return mItems == null ? 0 : mItems.size();
        }

        private Item getItem(int position) {
            return mItems.get(position);
        }

        private void clearRadioGroup(Item item) {
            int position = mItems.indexOf(item);
            for (int i = position - 1; i >= 0; --i) {
                if ((item = mItems.get(i)) instanceof RadioButtonItem) {
                    ((RadioButtonItem) item).setChecked(false);
                } else {
                    break;
                }
            }
            for (int i = position + 1; i < mItems.size(); ++i) {
                if ((item = mItems.get(i)) instanceof RadioButtonItem) {
                    ((RadioButtonItem) item).setChecked(false);
                } else {
                    break;
                }
            }
        }

        private static class ViewHolder extends RecyclerView.ViewHolder {
            public Item item;

            private ViewHolder(View view) {
                super(view);
            }
        }
    }
}
package com.jox3.tv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jox3.tv.R;
import com.jox3.tv.model.Category;

import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

    public interface OnClickListener { void onClick(Category cat); }

    private List<Category> items = new ArrayList<>();
    private int selectedPos = -1;
    private final OnClickListener listener;

    public CategoryAdapter(OnClickListener l) { listener = l; }

    public void setItems(List<Category> list) {
        items = list;
        notifyDataSetChanged();
    }

    public void setSelected(int pos) {
        int old = selectedPos;
        selectedPos = pos;
        if (old >= 0) notifyItemChanged(old);
        if (pos >= 0) notifyItemChanged(pos);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext())
            .inflate(R.layout.item_category, p, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Category cat = items.get(pos);
        h.name.setText(cat.name);
        h.count.setText(cat.loaded ? String.valueOf(cat.items.size()) : "·");
        boolean sel = pos == selectedPos;
        h.name.setTextColor(h.itemView.getContext().getColor(
            sel ? R.color.accent : R.color.muted2));
        h.itemView.setBackgroundColor(h.itemView.getContext().getColor(
            sel ? R.color.bg3 : android.R.color.transparent));
        h.itemView.setOnClickListener(v -> {
            int p2 = h.getAdapterPosition();
            setSelected(p2);
            listener.onClick(items.get(p2));
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, count;
        VH(View v) {
            super(v);
            name  = v.findViewById(R.id.tv_name);
            count = v.findViewById(R.id.tv_count);
        }
    }
}

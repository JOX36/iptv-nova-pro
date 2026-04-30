package com.jox3.tv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jox3.tv.R;
import com.jox3.tv.model.EpgProgram;

import java.util.List;

public class EpgAdapter extends RecyclerView.Adapter<EpgAdapter.VH> {

    private final List<EpgProgram> programs;

    public EpgAdapter(List<EpgProgram> programs) {
        this.programs = programs;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext())
            .inflate(R.layout.item_epg, p, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        EpgProgram ep = programs.get(pos);
        h.tvTime.setText(ep.timeRange());
        h.tvTitle.setText(ep.title);
        if (ep.description != null && !ep.description.isEmpty()) {
            h.tvDesc.setText(ep.description);
            h.tvDesc.setVisibility(View.VISIBLE);
        } else {
            h.tvDesc.setVisibility(View.GONE);
        }

        // Resaltar programa actual
        if (ep.isNow()) {
            h.itemView.setBackgroundColor(0x2200D4FF);
            h.tvTitle.setTextColor(0xFF00D4FF);
            h.tvTime.setTextColor(0xFF00D4FF);
        } else {
            h.itemView.setBackgroundColor(0x00000000);
            h.tvTitle.setTextColor(0xFFE0F4FF);
            h.tvTime.setTextColor(0xFF7AB8CC);
        }
    }

    @Override public int getItemCount() { return programs.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTime, tvTitle, tvDesc;
        VH(View v) {
            super(v);
            tvTime  = v.findViewById(R.id.tv_epg_time);
            tvTitle = v.findViewById(R.id.tv_epg_program);
            tvDesc  = v.findViewById(R.id.tv_epg_desc);
        }
    }
}


package com.jox3.tv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jox3.tv.R;
import com.jox3.tv.model.Account;

import java.util.List;

public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {

    public interface OnClick  { void onClick(Account acc); }
    public interface OnDelete { void onDelete(Account acc); }

    private final List<Account> items;
    private final OnClick click;
    private final OnDelete delete;

    public AccountAdapter(List<Account> items, OnClick c, OnDelete d) {
        this.items = items; click = c; delete = d;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext())
            .inflate(R.layout.item_account, p, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Account acc = items.get(pos);
        h.tvHost.setText(acc.displayHost());
        h.tvUser.setText(acc.user);
        h.itemView.setOnClickListener(v -> click.onClick(acc));
        h.ivDelete.setOnClickListener(v -> delete.onDelete(acc));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvHost, tvUser;
        ImageView ivDelete;
        VH(View v) {
            super(v);
            tvHost   = v.findViewById(R.id.tv_host);
            tvUser   = v.findViewById(R.id.tv_user);
            ivDelete = v.findViewById(R.id.iv_delete);
        }
    }
}

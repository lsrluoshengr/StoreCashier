package com.example.storecashier;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.ViewHolder> {

    private List<Order> orders;
    private final ProductViewModel viewModel;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public OrderAdapter(List<Order> orders, ProductViewModel viewModel) {
        this.orders = orders;
        this.viewModel = viewModel;
    }

    public void setOrders(List<Order> orders) {
        this.orders = orders;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order order = orders.get(position);
        holder.tvOrderId.setText("订单号: " + order.getOrderId());
        holder.tvOrderTime.setText(dateFormat.format(new Date(order.getTimestamp())));
        holder.tvOrderTotal.setText(String.format("¥ %.2f", order.getTotalAmount()));

        holder.itemView.setOnClickListener(v -> {
            viewModel.getOrderItems(order.getOrderId(), items -> {
                // 回调在后台线程，需切回主线程显示 Dialog
                if (v.getContext() instanceof android.app.Activity) {
                    ((android.app.Activity) v.getContext()).runOnUiThread(() -> showOrderDetailsDialog(v.getContext(), items));
                }
            });
        });
    }

    private void showOrderDetailsDialog(android.content.Context context, List<OrderItem> items) {
        if (items == null || items.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for (OrderItem item : items) {
            sb.append(item.getProductName())
              .append(" x ")
              .append(item.getQuantity())
              .append("  (¥ ")
              .append(String.format("%.2f", item.getPrice() * item.getQuantity()))
              .append(")\n");
        }

        new AlertDialog.Builder(context)
                .setTitle("订单明细")
                .setMessage(sb.toString())
                .setPositiveButton("确定", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvOrderTime, tvOrderTotal;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tv_order_id);
            tvOrderTime = itemView.findViewById(R.id.tv_order_time);
            tvOrderTotal = itemView.findViewById(R.id.tv_order_total);
        }
    }
}

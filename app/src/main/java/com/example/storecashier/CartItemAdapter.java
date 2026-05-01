package com.example.storecashier;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

public class CartItemAdapter extends RecyclerView.Adapter<CartItemAdapter.ViewHolder> {

    private final List<CartItem> cartItems;
    private final OnCartItemChangeListener listener;

    public interface OnCartItemChangeListener {
        void onCartUpdated();
    }

    public CartItemAdapter(List<CartItem> cartItems, OnCartItemChangeListener listener) {
        this.cartItems = cartItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_settlement, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CartItem cartItem = cartItems.get(position);
        Product product = cartItem.getProduct();

        holder.tvName.setText(product.getName());
        holder.tvPrice.setText(String.format("单价：%.2f元", product.getPrice()));
        holder.tvQuantity.setText(String.valueOf(cartItem.getQuantity()));
        holder.tvItemTotal.setText(String.format("小计：%.2f元", cartItem.getItemTotal()));

        // 加载图片
        Glide.with(holder.itemView.getContext())
                .load(product.getImagePath() != null ? new File(product.getImagePath()) : null)
                .placeholder(R.drawable.default_img)
                .error(R.drawable.default_img)
                .into(holder.ivImage);

        holder.btnPlus.setOnClickListener(v -> {
            cartItem.incrementQuantity();
            notifyItemChanged(holder.getAdapterPosition());
            if (listener != null) listener.onCartUpdated();
        });

        holder.btnMinus.setOnClickListener(v -> {
            int currentQuantity = cartItem.getQuantity();
            if (currentQuantity > 1) {
                cartItem.setQuantity(currentQuantity - 1);
                notifyItemChanged(holder.getAdapterPosition());
                if (listener != null) listener.onCartUpdated();
            } else {
                showRemoveDialog(holder.itemView, holder.getAdapterPosition());
            }
        });

        holder.tvQuantity.setOnClickListener(v -> showManualInputDialog(holder.itemView, holder.getAdapterPosition()));
    }

    private void showManualInputDialog(View view, int position) {
        CartItem cartItem = cartItems.get(position);
        EditText etInput = new EditText(view.getContext());
        etInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        etInput.setText(String.valueOf(cartItem.getQuantity()));
        etInput.setSelection(etInput.getText().length());

        new AlertDialog.Builder(view.getContext())
                .setTitle("修改数量")
                .setView(etInput)
                .setPositiveButton("确定", (dialog, which) -> {
                    String input = etInput.getText().toString().trim();
                    if (input.isEmpty()) {
                        Toast.makeText(view.getContext(), "输入不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        int newQty = Integer.parseInt(input);
                        if (newQty > 0) {
                            cartItem.setQuantity(newQty);
                            notifyItemChanged(position);
                            if (listener != null) listener.onCartUpdated();
                        } else if (newQty == 0) {
                            showRemoveDialog(view, position);
                        } else {
                            Toast.makeText(view.getContext(), "请输入有效的数量", Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(view.getContext(), "输入格式错误", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRemoveDialog(View view, int position) {
        new AlertDialog.Builder(view.getContext())
                .setTitle("确认移除")
                .setMessage("确定要从购物车中移除此商品吗？")
                .setPositiveButton("移除", (dialog, which) -> {
                    cartItems.remove(position);
                    notifyItemRemoved(position);
                    notifyItemRangeChanged(position, cartItems.size());
                    if (listener != null) listener.onCartUpdated();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return cartItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvPrice, tvQuantity, tvItemTotal;
        Button btnPlus, btnMinus;
        ImageView ivImage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_settlement_name);
            tvPrice = itemView.findViewById(R.id.tv_settlement_price);
            tvQuantity = itemView.findViewById(R.id.tv_settlement_quantity);
            tvItemTotal = itemView.findViewById(R.id.tv_settlement_item_total);
            btnPlus = itemView.findViewById(R.id.btn_plus);
            btnMinus = itemView.findViewById(R.id.btn_minus);
            ivImage = itemView.findViewById(R.id.iv_cart_item_image);
        }
    }
}

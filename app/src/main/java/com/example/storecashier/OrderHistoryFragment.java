package com.example.storecashier;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class OrderHistoryFragment extends Fragment {

    private ProductViewModel productViewModel;
    private OrderAdapter orderAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_order_history, container, false);

        Toolbar toolbar = view.findViewById(R.id.toolbar_order_history);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());

        RecyclerView rvOrderHistory = view.findViewById(R.id.rv_order_history);
        productViewModel = new ViewModelProvider(requireActivity()).get(ProductViewModel.class);

        orderAdapter = new OrderAdapter(new ArrayList<>(), productViewModel);
        rvOrderHistory.setAdapter(orderAdapter);

        productViewModel.getAllOrders().observe(getViewLifecycleOwner(), orders -> {
            if (orders != null) {
                orderAdapter.setOrders(orders);
            }
        });

        return view;
    }
}

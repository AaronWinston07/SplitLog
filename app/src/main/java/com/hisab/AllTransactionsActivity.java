package com.hisab;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import adapters.TransactionAdapter;
import database.DatabaseHelper;
import models.Transaction;

import java.util.List;

/** Shows every transaction of every person, newest first. */
public class AllTransactionsActivity extends AppCompatActivity {

    private DatabaseHelper db;
    private TransactionAdapter adapter;
    private RecyclerView rvTransactions;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_transactions);

        db = DatabaseHelper.getInstance(this);

        rvTransactions = findViewById(R.id.rvTransactions);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new TransactionAdapter(true); // true = show the person's name on each row
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvTransactions.setAdapter(adapter);

        findViewById(R.id.tvBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<Transaction> list = db.getAllTransactions();
        adapter.setTransactions(list);
        boolean empty = list.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvTransactions.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
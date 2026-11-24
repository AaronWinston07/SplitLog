package com.hisab;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import adapters.MonthAdapter;
import database.DatabaseHelper;
import models.MonthSummary;

import java.util.Calendar;
import java.util.List;

/** Lists every earlier month that has expenses. */
public class PreviousMonthsActivity extends AppCompatActivity {

    private DatabaseHelper db;
    private MonthAdapter adapter;
    private RecyclerView rvMonths;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_previous_months);

        db = DatabaseHelper.getInstance(this);
        rvMonths = findViewById(R.id.rvMonths);
        emptyView = findViewById(R.id.emptyView);

        adapter = new MonthAdapter(month -> {
            Intent intent = new Intent(this, MonthExpensesActivity.class);
            intent.putExtra(MonthExpensesActivity.EXTRA_MONTH_START, month.startMillis());
            startActivity(intent);
        });
        rvMonths.setLayoutManager(new LinearLayoutManager(this));
        rvMonths.setAdapter(adapter);

        findViewById(R.id.tvBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Everything before the 1st of the current month
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        List<MonthSummary> months = db.getMonthSummariesBefore(c.getTimeInMillis());
        adapter.setMonths(months);
        boolean empty = months.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvMonths.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
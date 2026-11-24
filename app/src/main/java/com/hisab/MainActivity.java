package com.hisab;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import adapters.PersonAdapter;
import database.DatabaseHelper;
import models.Person;
import utils.CurrencyUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import utils.ReminderHelper;
/** Dashboard: totals, searchable and sortable list of people. */
public class MainActivity extends AppCompatActivity {

    private static final int SORT_NAME = 0;
    private static final int SORT_HIGHEST = 1;
    private static final int SORT_LOWEST = 2;
    private static final int SORT_RECENT = 3;

    private DatabaseHelper db;
    private PersonAdapter adapter;

    private TextView tvNet, tvNetHint, tvReceive, tvPay, tvEmptyTitle, tvEmptyMessage, tvSort;
    private RecyclerView rvPeople;
    private View emptyView;
    private EditText etSearch;

    private int sortMode = SORT_NAME;
    private boolean searchVisible = false;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = DatabaseHelper.getInstance(this);

        tvNet = findViewById(R.id.tvNet);
        tvNetHint = findViewById(R.id.tvNetHint);
        tvReceive = findViewById(R.id.tvReceive);
        tvPay = findViewById(R.id.tvPay);
        rvPeople = findViewById(R.id.rvPeople);
        emptyView = findViewById(R.id.emptyView);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        etSearch = findViewById(R.id.etSearch);
        tvSort = findViewById(R.id.tvSort);

        adapter = new PersonAdapter(person -> {
            Intent intent = new Intent(this, PersonDetailsActivity.class);
            intent.putExtra(PersonDetailsActivity.EXTRA_PERSON_ID, person.getId());
            startActivity(intent);
        });
        rvPeople.setLayoutManager(new LinearLayoutManager(this));
        rvPeople.setAdapter(adapter);

        findViewById(R.id.tvAddPerson).setOnClickListener(v ->
                startActivity(new Intent(this, AddPersonActivity.class)));
        findViewById(R.id.ivSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.ivSearch).setOnClickListener(v -> toggleSearch());
        tvSort.setOnClickListener(v -> showSortDialog());
        findViewById(R.id.ivExpenses).setOnClickListener(v ->
                startActivity(new Intent(this, ExpensesActivity.class)));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                refreshList();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        // Reminders: prepare the channel, plan the next one, ask permission (Android 13+)
        ReminderHelper.createChannel(this);
        ReminderHelper.schedule(this);
        if (Build.VERSION.SDK_INT >= 33 && !ReminderHelper.hasPermission(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTotals();
        refreshList();
    }

    private void refreshTotals() {
        long receive = db.getTotalToReceive();
        long pay = db.getTotalToPay();
        long net = db.getNetBalance();

        tvReceive.setText(CurrencyUtils.format(receive));
        tvPay.setText(CurrencyUtils.format(pay));
        tvNet.setText(CurrencyUtils.formatSigned(net));

        if (net > 0) {
            tvNetHint.setText(R.string.overall_receive);
        } else if (net < 0) {
            tvNetHint.setText(R.string.overall_pay);
        } else {
            tvNetHint.setText(R.string.overall_settled);
        }
    }

    private void refreshList() {
        List<Person> all = db.getAllPeople();

        String query = etSearch.getText().toString().trim().toLowerCase(Locale.getDefault());
        List<Person> shown = new ArrayList<>();
        for (Person p : all) {
            if (query.isEmpty() || p.getName().toLowerCase(Locale.getDefault()).contains(query)) {
                shown.add(p);
            }
        }

        sortPeople(shown);
        adapter.setPeople(shown);

        boolean noPeopleAtAll = all.isEmpty();
        boolean noMatches = !noPeopleAtAll && shown.isEmpty();

        if (noPeopleAtAll) {
            tvEmptyTitle.setText(R.string.no_people_title);
            tvEmptyMessage.setText(R.string.no_people_message);
        } else if (noMatches) {
            tvEmptyTitle.setText(R.string.no_search_results);
            tvEmptyMessage.setText(R.string.no_search_results_message);
        }

        boolean showEmpty = noPeopleAtAll || noMatches;
        emptyView.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        rvPeople.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
    }

    private void sortPeople(List<Person> list) {
        switch (sortMode) {
            case SORT_HIGHEST:
                Collections.sort(list, (a, b) -> Long.compare(b.getBalance(), a.getBalance()));
                break;
            case SORT_LOWEST:
                Collections.sort(list, (a, b) -> Long.compare(a.getBalance(), b.getBalance()));
                break;
            case SORT_RECENT:
                final Map<Long, Long> last = db.getLastActivityTimes();
                Collections.sort(list, (a, b) -> {
                    long ta = last.containsKey(a.getId()) ? last.get(a.getId()) : 0L;
                    long tb = last.containsKey(b.getId()) ? last.get(b.getId()) : 0L;
                    return Long.compare(tb, ta);
                });
                break;
            case SORT_NAME:
            default:
                break;
        }
    }

    private void showSortDialog() {
        String[] options = {
                getString(R.string.sort_name),
                getString(R.string.sort_highest),
                getString(R.string.sort_lowest),
                getString(R.string.sort_recent)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sort_title)
                .setSingleChoiceItems(options, sortMode, (dialog, which) -> {
                    sortMode = which;
                    dialog.dismiss();
                    refreshList();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Search icon: shows or hides the search box and the Sort button. */
    private void toggleSearch() {
        searchVisible = !searchVisible;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        if (searchVisible) {
            etSearch.setVisibility(View.VISIBLE);
            tvSort.setVisibility(View.VISIBLE);
            etSearch.requestFocus();
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
        } else {
            etSearch.setText("");
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            etSearch.setVisibility(View.GONE);
            tvSort.setVisibility(View.GONE);
        }
        refreshList();
    }
}
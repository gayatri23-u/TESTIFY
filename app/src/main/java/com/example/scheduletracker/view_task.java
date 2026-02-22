package com.example.scheduletracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class view_task extends AppCompatActivity {

    MaterialToolbar toolbarAddTask;
    ImageView btnPrevMonth, btnNextMonth;
    TextView txtMonth;

    LinearLayout containerTaskRows;

    HorizontalScrollView headerScroll;
    LinearLayout layoutHeaderRow;

    List<HorizontalScrollView> rowScrolls = new ArrayList<>();

    MaterialButton btnAddTask, btnJumpToday, btnViewStats;

    FirebaseAuth mAuth;
    FirebaseFirestore db;
    FirebaseUser currentUser;

    Calendar currentCalendar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_task);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        toolbarAddTask = findViewById(R.id.toolbarAddTask);
        btnPrevMonth = findViewById(R.id.btnPrevMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);
        txtMonth = findViewById(R.id.txtMonth);
        containerTaskRows = findViewById(R.id.containerTaskRows);
        headerScroll = findViewById(R.id.headerScroll);
        layoutHeaderRow = findViewById(R.id.layoutHeaderRow);
        btnAddTask = findViewById(R.id.btnAddTask);
        btnJumpToday = findViewById(R.id.btnJumpToday);
        btnViewStats = findViewById(R.id.btnViewStats);

        toolbarAddTask.setNavigationOnClickListener(v -> finish());

        currentCalendar = Calendar.getInstance();
        updateMonthText();
        buildHeaderRow();
        loadTasksForMonth();

        btnPrevMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            updateMonthText();
            buildHeaderRow();
            loadTasksForMonth();
        });

        btnNextMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            updateMonthText();
            buildHeaderRow();
            loadTasksForMonth();
        });

        btnAddTask.setOnClickListener(v->{
            Intent intent = new Intent(this, add_task.class);
            startActivity(intent);
        });

        //jump to today when clicked on jump on today
        btnJumpToday.setOnClickListener(v -> autoScrollToTodayCentered());

        //view stats when clicked on view stats
        btnViewStats.setOnClickListener(v -> showStatsBottomSheet());


        //move to selected date when cicked on dashboard

        String selectedDate = getIntent().getStringExtra("selectedDate");

        if (selectedDate != null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date date = sdf.parse(selectedDate);

                Calendar cal = Calendar.getInstance();
                cal.setTime(date);

                currentCalendar.set(Calendar.YEAR, cal.get(Calendar.YEAR));
                currentCalendar.set(Calendar.MONTH, cal.get(Calendar.MONTH));

                updateMonthText();
                buildHeaderRow();
                loadTasksForMonth();

                // Scroll to that day
                int selectedDay = cal.get(Calendar.DAY_OF_MONTH);
                scrollToDay(selectedDay);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateMonthText() {
        txtMonth.setText(new SimpleDateFormat("MMM yyyy", Locale.getDefault())
                .format(currentCalendar.getTime()));
    }

    private void buildHeaderRow() {
        layoutHeaderRow.removeAllViews();
        int daysInMonth = currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        int size = dpToPx(40);
        int margin = dpToPx(6);

        Calendar today = Calendar.getInstance();

        for (int day = 1; day <= daysInMonth; day++) {
            TextView tv = new TextView(this);
            tv.setTextColor(getResources().getColor(android.R.color.black));
            tv.setText(String.valueOf(day));
            tv.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, margin, margin, margin);
            tv.setLayoutParams(lp);

            if (day == today.get(Calendar.DAY_OF_MONTH)
                    && currentCalendar.get(Calendar.MONTH) == today.get(Calendar.MONTH)
                    && currentCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
                tv.setBackgroundResource(R.drawable.bg_today_header);
            }

            layoutHeaderRow.addView(tv);
        }
    }

    private void loadTasksForMonth() {
        containerTaskRows.removeAllViews();
        rowScrolls.clear();

        String uid = currentUser.getUid();
        int month = currentCalendar.get(Calendar.MONTH) + 1;
        int year = currentCalendar.get(Calendar.YEAR);

        db.collection("Tasks")
                .document(uid)
                .collection("dates")
                .get()
                .addOnSuccessListener(dateSnapshots -> {

                    Map<String, Map<Integer, Boolean>> taskMonthMap = new HashMap<>();
                    int[] pending = {0};
                    boolean[] foundAny = {false};

                    for (QueryDocumentSnapshot dateDoc : dateSnapshots) {
                        String dateKey = dateDoc.getId();
                        String[] parts = dateKey.split("-");
                        int y = Integer.parseInt(parts[0]);
                        int m = Integer.parseInt(parts[1]);
                        int d = Integer.parseInt(parts[2]);

                        if (y == year && m == month) {
                            pending[0]++;
                            foundAny[0] = true;

                            db.collection("Tasks")
                                    .document(uid)
                                    .collection("dates")
                                    .document(dateKey)
                                    .collection("items")
                                    .get()
                                    .addOnSuccessListener(items -> {

                                        for (QueryDocumentSnapshot itemDoc : items) {
                                            String title = itemDoc.getString("title");
                                            Boolean completed = itemDoc.getBoolean("completed");
                                            if (title == null) continue;

                                            taskMonthMap
                                                    .computeIfAbsent(title, k -> new HashMap<>())
                                                    .put(d, completed != null && completed);
                                        }

                                        pending[0]--;
                                        if (pending[0] == 0) {
                                            rebuildTaskRows(taskMonthMap);
                                            syncScrolls();
                                            autoScrollToTodayCentered();
                                        }
                                    });
                        }
                    }

                    if (!foundAny[0]) {
                        Toast.makeText(this, "No tasks for this month", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void rebuildTaskRows(Map<String, Map<Integer, Boolean>> taskMonthMap) {
        containerTaskRows.removeAllViews();
        rowScrolls.clear();

        int daysInMonth = currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (String taskName : taskMonthMap.keySet()) {
            View rowView = LayoutInflater.from(this)
                    .inflate(R.layout.item_task_row, containerTaskRows, false);

            TextView txtTaskName = rowView.findViewById(R.id.txtTaskName);
            LinearLayout layoutDayCells = rowView.findViewById(R.id.layoutDayCells);
            HorizontalScrollView rowScroll = rowView.findViewById(R.id.scrollDates);

            txtTaskName.setText(taskName);

            Map<Integer, Boolean> dayStatusMap = taskMonthMap.get(taskName);
            if (dayStatusMap == null) dayStatusMap = new HashMap<>();

            for (int day = 1; day <= daysInMonth; day++) {
                FrameLayout cell = createDayCell(
                        day, dayStatusMap, taskName,
                        currentCalendar.get(Calendar.YEAR),
                        currentCalendar.get(Calendar.MONTH) + 1
                );
                layoutDayCells.addView(cell);
            }

            rowScroll.setEnabled(false);
            rowScroll.setHorizontalScrollBarEnabled(false);

            rowScrolls.add(rowScroll);
            containerTaskRows.addView(rowView);
        }
    }

    private FrameLayout createDayCell(int day, Map<Integer, Boolean> dayStatusMap,
                                      String taskName, int year, int month) {

        int size = dpToPx(40);
        int margin = dpToPx(6);

        FrameLayout cell = new FrameLayout(this);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(size, size);
        params.setMargins(margin, margin, margin, margin);
        cell.setLayoutParams(params);

        ImageView icon = new ImageView(this);
        FrameLayout.LayoutParams iconParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
        iconParams.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconParams);

        Boolean completed = (dayStatusMap != null) ? dayStatusMap.get(day) : null;

        if (completed == null) {
            cell.setBackgroundResource(R.drawable.bg_day_cell_empty);
            icon.setVisibility(View.GONE);
        } else if (completed) {
            cell.setBackgroundResource(R.drawable.bg_day_success);
            icon.setImageResource(R.drawable.ic_tick_green);
            icon.setVisibility(View.VISIBLE);
        } else {
            cell.setBackgroundResource(R.drawable.bg_day_failed);
            icon.setImageResource(R.drawable.ic_cross_red);
            icon.setVisibility(View.VISIBLE);
        }

        cell.addView(icon);

        //  Block future days
        Calendar cellDate = Calendar.getInstance();
        cellDate.set(Calendar.YEAR, year);
        cellDate.set(Calendar.MONTH, month - 1); // Calendar months are 0-based
        cellDate.set(Calendar.DAY_OF_MONTH, day);
        cellDate.set(Calendar.HOUR_OF_DAY, 0);
        cellDate.set(Calendar.MINUTE, 0);
        cellDate.set(Calendar.SECOND, 0);
        cellDate.set(Calendar.MILLISECOND, 0);

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        boolean isFuture = cellDate.after(today);

        if (isFuture) {
            //  Disable future days
            cell.setAlpha(0.4f); // faded look
            cell.setOnClickListener(v ->
                    Toast.makeText(this, "You can’t mark future days yet ", Toast.LENGTH_SHORT).show()
            );
        } else {
            // Allow marking today & past
            cell.setOnClickListener(v ->
                    showStatusPopup(taskName, day, year, month, cell, icon)
            );
        }

        return cell;
    }
    private void showStatusPopup(String taskName, int day, int year, int month,
                                 FrameLayout cell, ImageView icon) {

        String dateKey = String.format(Locale.getDefault(),
                "%04d-%02d-%02d", year, month, day);

        PopupMenu popup = new PopupMenu(this, cell);
        popup.getMenu().add("Done");
        popup.getMenu().add("Not Done");

        popup.setOnMenuItemClickListener(item -> {
            boolean isDone = item.getTitle().equals("Done");

            if (isDone) {
                cell.setBackgroundResource(R.drawable.bg_day_success);
                icon.setImageResource(R.drawable.ic_tick_green);
            } else {
                cell.setBackgroundResource(R.drawable.bg_day_failed);
                icon.setImageResource(R.drawable.ic_cross_red);
            }
            icon.setVisibility(View.VISIBLE);

            updateTaskStatusInFirebase(taskName, dateKey, isDone);
            return true;
        });

        popup.show();
    }

    private void updateTaskStatusInFirebase(String taskName, String dateKey, boolean completed) {

        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        //  Block future date updates at backend level too
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date selectedDate = sdf.parse(dateKey);

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            if (selectedDate != null && selectedDate.after(today.getTime())) {
                Toast.makeText(this, "Future dates cannot be updated", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String uid = currentUser.getUid();

        db.collection("Tasks")
                .document(uid)
                .collection("dates")
                .document(dateKey)
                .collection("items")
                .whereEqualTo("title", taskName)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (snapshot.isEmpty()) {
                        Toast.makeText(this, "Task not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    for (QueryDocumentSnapshot doc : snapshot) {
                        doc.getReference().update("completed", completed);
                    }

                    Toast.makeText(this, "Task updated", Toast.LENGTH_SHORT).show();

                    //Call these if this screen also shows stats
                    //loadTodayProgress();
                    //loadCurrentStreak();
                   // loadMonthlyCompletion();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void syncScrolls() {
        final List<HorizontalScrollView> syncedScrolls = new ArrayList<>(rowScrolls);

        headerScroll.getViewTreeObserver().addOnScrollChangedListener(
                new ViewTreeObserver.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged() {
                        int x = headerScroll.getScrollX();
                        for (HorizontalScrollView rowScroll : syncedScrolls) {
                            rowScroll.scrollTo(x, 0);
                        }
                    }
                }
        );
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void autoScrollToTodayCentered() {
        Calendar today = Calendar.getInstance();

        if (today.get(Calendar.YEAR) != currentCalendar.get(Calendar.YEAR) ||
                today.get(Calendar.MONTH) != currentCalendar.get(Calendar.MONTH)) return;

        int todayDay = today.get(Calendar.DAY_OF_MONTH);

        int cellSize = dpToPx(40);
        int margin = dpToPx(6);
        int fullCellWidth = cellSize + (margin * 2);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int calculatedScrollX =
                (todayDay - 1) * fullCellWidth - (screenWidth / 2) + (fullCellWidth / 2);

        final int targetScrollX = Math.max(0, calculatedScrollX);

        headerScroll.post(() -> headerScroll.smoothScrollTo(targetScrollX, 0));
    }

    private void showStatsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottomsheet_stats, null);

        TextView txtStreak = view.findViewById(R.id.txtStreak);
        TextView txtToday = view.findViewById(R.id.txtTodayProgress);
        TextView txtMonth = view.findViewById(R.id.txtMonthlyCompletion);

        dialog.setContentView(view);
        dialog.show();

        loadTodayProgressInto(txtToday);
        loadCurrentStreakInto(txtStreak);
        loadMonthlyCompletionInto(txtMonth);
    }

    private void loadTodayProgressInto(TextView target) {
        if (currentUser == null) return;

        String uid = currentUser.getUid();
        String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Calendar.getInstance().getTime());

        db.collection("Tasks")
                .document(uid)
                .collection("dates")
                .document(todayKey)
                .collection("items")
                .get()
                .addOnSuccessListener(snapshot -> {
                    int total = snapshot.size();
                    int done = 0;

                    for (QueryDocumentSnapshot doc : snapshot) {
                        Boolean completed = doc.getBoolean("completed");
                        if (completed != null && completed) done++;
                    }

                    if (total == 0) {
                        target.setText("No tasks today");
                    } else {
                        target.setText(done + " / " + total + " Done Today");
                    }
                })
                .addOnFailureListener(e -> target.setText("Error"));
    }

    private void loadCurrentStreakInto(TextView target) {
        if (currentUser == null) return;

        Calendar cal = Calendar.getInstance();
        checkStreakDay(cal, 0, target);
    }

    private void checkStreakDay(Calendar cal, int streak, TextView target) {
        String uid = currentUser.getUid();
        String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(cal.getTime());

        db.collection("Tasks")
                .document(uid)
                .collection("dates")
                .document(dateKey)
                .collection("items")
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (snapshot.isEmpty()) {
                        target.setText(streak + " Days");
                        return;
                    }

                    boolean allDone = true;
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Boolean completed = doc.getBoolean("completed");
                        if (completed == null || !completed) {
                            allDone = false;
                            break;
                        }
                    }

                    if (allDone) {
                        cal.add(Calendar.DAY_OF_MONTH, -1);
                        checkStreakDay(cal, streak + 1, target);
                    } else {
                        target.setText(streak + " Days");
                    }
                })
                .addOnFailureListener(e -> target.setText("0 Days"));
    }

    private void loadMonthlyCompletionInto(TextView target) {
        if (currentUser == null) return;

        String uid = currentUser.getUid();
        int month = currentCalendar.get(Calendar.MONTH) + 1;
        int year = currentCalendar.get(Calendar.YEAR);

        db.collection("Tasks")
                .document(uid)
                .collection("dates")
                .get()
                .addOnSuccessListener(dateSnapshots -> {

                    int[] total = {0};
                    int[] done = {0};
                    int[] pending = {0};

                    for (QueryDocumentSnapshot dateDoc : dateSnapshots) {
                        String dateKey = dateDoc.getId(); // yyyy-MM-dd
                        String[] parts = dateKey.split("-");

                        int y = Integer.parseInt(parts[0]);
                        int m = Integer.parseInt(parts[1]);

                        if (y == year && m == month) {
                            pending[0]++;

                            db.collection("Tasks")
                                    .document(uid)
                                    .collection("dates")
                                    .document(dateKey)
                                    .collection("items")
                                    .get()
                                    .addOnSuccessListener(items -> {
                                        for (QueryDocumentSnapshot doc : items) {
                                            total[0]++;
                                            Boolean completed = doc.getBoolean("completed");
                                            if (completed != null && completed) done[0]++;
                                        }

                                        pending[0]--;
                                        if (pending[0] == 0) {
                                            if (total[0] == 0) {
                                                target.setText("0%");
                                            } else {
                                                int percent = (done[0] * 100) / total[0];
                                                target.setText(percent + "%");
                                            }
                                        }
                                    });
                        }
                    }

                    if (pending[0] == 0) {
                        target.setText("0%");
                    }
                })
                .addOnFailureListener(e -> target.setText("0%"));
    }

    private void scrollToDay(int day) {
        int cellSize = dpToPx(40);
        int margin = dpToPx(6);
        int fullCellWidth = cellSize + (margin * 2);

        int targetScrollX = (day - 1) * fullCellWidth;

        headerScroll.post(() -> headerScroll.smoothScrollTo(targetScrollX, 0));
    }
}

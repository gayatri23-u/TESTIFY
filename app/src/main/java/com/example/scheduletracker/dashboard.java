package com.example.scheduletracker;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class dashboard extends AppCompatActivity {

    GridLayout gridCalendar;
    TextView txtMonthYear, txtStreakCount, txtTodayProgress, txtMonthlyCompletion;
    ImageView btnPrevMonth, btnNextMonth, btnMenu;
    CardView cardAddTasks, cardViewTasks;

    FirebaseAuth mAuth;
    FirebaseFirestore db;
    FirebaseUser currentUser;

    Calendar currentCalendar = Calendar.getInstance();

    // day -> [totalTasks, completedTasks]
    Map<Integer, int[]> dayStatusMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        MaterialToolbar toolbar = findViewById(R.id.toolbarDashboard);

        gridCalendar = findViewById(R.id.gridCalendar);
        txtMonthYear = findViewById(R.id.txtMonthYear);
        btnPrevMonth = findViewById(R.id.btnPrevMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);

        txtStreakCount = findViewById(R.id.txtStreakCount);
        txtTodayProgress = findViewById(R.id.txtTodayProgress);
        txtMonthlyCompletion = findViewById(R.id.txtMonthlyCompletion);

        cardAddTasks = findViewById(R.id.cardAddTasks);
        cardViewTasks = findViewById(R.id.cardViewTasks);
        btnMenu = findViewById(R.id.btnMenu);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        btnPrevMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            loadCalendarFromFirebase();
        });

        btnNextMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            loadCalendarFromFirebase();
        });

        cardAddTasks.setOnClickListener(v ->
                startActivity(new Intent(dashboard.this, add_task.class)));

        cardViewTasks.setOnClickListener(v ->
                startActivity(new Intent(dashboard.this, view_task.class)));

        btnMenu.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(dashboard.this, btnMenu);
            popupMenu.getMenuInflater().inflate(R.menu.toolbar_menu, popupMenu.getMenu());

            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();

                if (id == R.id.menu_settings) {
                    // Open Settings
                    startActivity(new Intent(dashboard.this, settings.class));
                    return true;

                } else if (id == R.id.menu_notifications) {
                    // Open Notifications page
                    startActivity(new Intent(dashboard.this, notifications.class));
                    return true;

                } else if (id == R.id.menu_logout) {
                    showLogoutDialog();   // show confirmation first
                    return true;
                }

                return false;
            });

            popupMenu.show();
        });

        loadCalendarFromFirebase();
        loadTodayProgress();
        loadCurrentStreak();
        loadMonthlyCompletion();
    }

    @Override
    protected void onResume() {
        super.onResume();

        loadCalendarFromFirebase();   // refresh calendar
        loadTodayProgress();          // refresh today progress
        loadCurrentStreak();          // refresh streak
        loadMonthlyCompletion();      // refresh monthly stats
    }

    //  Load calendar data from Firebase
    private void loadCalendarFromFirebase() {
        if (currentUser == null) return;

        dayStatusMap.clear();
        gridCalendar.removeAllViews();

        SimpleDateFormat sdfMonth = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        txtMonthYear.setText(sdfMonth.format(currentCalendar.getTime()));

        String uid = currentUser.getUid();
        int month = currentCalendar.get(Calendar.MONTH) + 1;
        int year = currentCalendar.get(Calendar.YEAR);

        db.collection("Tasks")
                .document(uid)
                .collection("dates")
                .get()
                .addOnSuccessListener(dateSnapshots -> {

                    int[] pending = {0};

                    for (QueryDocumentSnapshot dateDoc : dateSnapshots) {
                        String[] parts = dateDoc.getId().split("-");
                        int y = Integer.parseInt(parts[0]);
                        int m = Integer.parseInt(parts[1]);
                        int d = Integer.parseInt(parts[2]);

                        if (y == year && m == month) {
                            pending[0]++;

                            db.collection("Tasks")
                                    .document(uid)
                                    .collection("dates")
                                    .document(dateDoc.getId())
                                    .collection("items")
                                    .get()
                                    .addOnSuccessListener(items -> {

                                        int total = items.size();
                                        int done = 0;

                                        for (QueryDocumentSnapshot doc : items) {
                                            Boolean completed = doc.getBoolean("completed");
                                            if (completed != null && completed) done++;
                                        }

                                        dayStatusMap.put(d, new int[]{total, done});

                                        pending[0]--;
                                        if (pending[0] == 0) {
                                            renderCalendar();   // ✅ render ONLY after all data loaded
                                        }
                                    });
                        }
                    }

                    if (pending[0] == 0) {
                        renderCalendar(); // no tasks at all in this month
                    }
                });
    }

    private void renderCalendar() {
        gridCalendar.removeAllViews();

        Calendar tempCal = (Calendar) currentCalendar.clone();
        tempCal.set(Calendar.DAY_OF_MONTH, 1);

        int firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 0; i < firstDayOfWeek; i++) addEmptyCell();

        Calendar today = Calendar.getInstance();

        for (int day = 1; day <= daysInMonth; day++) {
            boolean isToday =
                    day == today.get(Calendar.DAY_OF_MONTH) &&
                            currentCalendar.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                            currentCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR);

            int total = dayStatusMap.containsKey(day) ? dayStatusMap.get(day)[0] : 0;
            int done = dayStatusMap.containsKey(day) ? dayStatusMap.get(day)[1] : 0;

            addDayCell(day, isToday, total, done);
        }
    }

    private void addEmptyCell() {
        View v = new View(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dpToPx(40);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        v.setLayoutParams(params);
        gridCalendar.addView(v);
    }

    private void addDayCell(int day, boolean isToday, int totalTasks, int completedTasks) {
        FrameLayout cell = new FrameLayout(this);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dpToPx(42);   // consistent height
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        cell.setLayoutParams(params);

        // Background logic
        if (totalTasks == 0) {
            cell.setBackgroundResource(R.drawable.bg_day_inactive);
        } else if (completedTasks == totalTasks) {
            cell.setBackgroundResource(R.drawable.bg_day_success); // 🟢 GREEN
        } else {
            cell.setBackgroundResource(R.drawable.bg_day_failed);  // 🔴 RED
        }

        //  Today highlight (border or overlay)
        if (isToday) {
            cell.setForeground(getDrawable(R.drawable.bg_day_today)); // optional border drawable
        }

        TextView tv = new TextView(this);
        tv.setText(String.valueOf(day));
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.DEFAULT_BOLD);

        //  Today date in BLACK
        if (isToday) tv.setTextColor(Color.BLACK);
        else tv.setTextColor(Color.WHITE);

        FrameLayout.LayoutParams tvParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        tv.setLayoutParams(tvParams);

        cell.addView(tv);

        //  Tap → open that day in view_task
        cell.setOnClickListener(v -> {
            Intent i = new Intent(dashboard.this, view_task.class);
            i.putExtra("year", currentCalendar.get(Calendar.YEAR));
            i.putExtra("month", currentCalendar.get(Calendar.MONTH) + 1);
            i.putExtra("day", day);
            startActivity(i);
        });

        gridCalendar.addView(cell);
    }

    // ===== Stats logic (unchanged but works live) =====

    private void loadTodayProgress() {
        if (currentUser == null) return;

        String uid = currentUser.getUid();
        String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Calendar.getInstance().getTime());

        db.collection("Tasks").document(uid)
                .collection("dates").document(todayKey)
                .collection("items")
                .get()
                .addOnSuccessListener(snapshot -> {
                    int total = snapshot.size();
                    int done = 0;
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Boolean completed = doc.getBoolean("completed");
                        if (completed != null && completed) done++;
                    }
                    txtTodayProgress.setText(done + " / " + total + " Tasks Done");
                });
    }

    private void loadCurrentStreak() {
        Calendar cal = Calendar.getInstance();
        calculateStreak(cal, 0);
    }

    private void calculateStreak(Calendar cal, int streak) {
        String uid = currentUser.getUid();
        String key = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());

        db.collection("Tasks").document(uid)
                .collection("dates").document(key)
                .collection("items")
                .get()
                .addOnSuccessListener(snapshot -> {
                    boolean allDone = !snapshot.isEmpty();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Boolean completed = doc.getBoolean("completed");
                        if (completed == null || !completed) allDone = false;
                    }

                    if (allDone) {
                        cal.add(Calendar.DAY_OF_MONTH, -1);
                        calculateStreak(cal, streak + 1);
                    } else {
                        txtStreakCount.setText(streak + " Days");
                    }
                });
    }

    private void loadMonthlyCompletion() {
        if (currentUser == null) return;

        String uid = currentUser.getUid();
        int month = currentCalendar.get(Calendar.MONTH) + 1;
        int year = currentCalendar.get(Calendar.YEAR);

        db.collection("Tasks").document(uid)
                .collection("dates")
                .get()
                .addOnSuccessListener(dateSnapshots -> {
                    int total = 0, done = 0;

                    for (QueryDocumentSnapshot dateDoc : dateSnapshots) {
                        String[] parts = dateDoc.getId().split("-");
                        int y = Integer.parseInt(parts[0]);
                        int m = Integer.parseInt(parts[1]);

                        if (y == year && m == month) {
                            // You can aggregate here same as calendar
                        }
                    }

                    if (total == 0) txtMonthlyCompletion.setText("0%");
                    else txtMonthlyCompletion.setText((done * 100 / total) + "%");
                });
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void showLogoutDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_logout, null);
        builder.setView(view);

        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnLogout = view.findViewById(R.id.btnLogout);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(dashboard.this, login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            dialog.dismiss();
        });

        dialog.show();
    }
}
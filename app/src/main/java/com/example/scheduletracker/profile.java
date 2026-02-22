package com.example.scheduletracker;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class profile extends AppCompatActivity {

    MaterialToolbar toolbarProfile;

    FirebaseUser currentUser;
    ImageView imgProfile;
    TextView tvUserName, tvUserEmail, tvTotalTasks, tvCompletedTasks, tvStreak;

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        toolbarProfile = findViewById(R.id.toolbarProfile);
        setSupportActionBar(toolbarProfile);
        toolbarProfile.setNavigationOnClickListener(v -> finish());

        imgProfile = findViewById(R.id.imgProfile);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        tvTotalTasks = findViewById(R.id.tvTotalTasks);
        tvCompletedTasks = findViewById(R.id.tvCompletedTasks);
        tvStreak = findViewById(R.id.tvStreak);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        currentUser = mAuth.getCurrentUser();

        //load profile
        loadUserProfile();

        //load stats
        loadTaskStats();

        //load streak
        loadCurrentStreak();
    }

    private void loadUserProfile() {
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvUserEmail.setText(user.getEmail());

        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        String photoUrl = doc.getString("photoUrl");

                        if (name != null && !name.isEmpty()) tvUserName.setText(name);
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(photoUrl)
                                    .circleCrop()              // makes image circular
                                    .placeholder(R.drawable.bg_circle)
                                    .into(imgProfile);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                );
    }

    private void loadTaskStats() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Tasks")
                .document(uid)
                .collection("dates")
                .get()
                .addOnSuccessListener(dateSnapshots -> {

                    int[] totalTasks = {0};
                    int[] completedTasks = {0};
                    int[] pendingDates = {dateSnapshots.size()};

                    if (pendingDates[0] == 0) {
                        tvTotalTasks.setText("0");
                        tvCompletedTasks.setText("0");
                        return;
                    }

                    for (QueryDocumentSnapshot dateDoc : dateSnapshots) {
                        db.collection("Tasks")
                                .document(uid)
                                .collection("dates")
                                .document(dateDoc.getId())
                                .collection("items")
                                .get()
                                .addOnSuccessListener(items -> {

                                    for (QueryDocumentSnapshot item : items) {
                                        totalTasks[0]++;
                                        Boolean completed = item.getBoolean("completed");
                                        if (completed != null && completed) completedTasks[0]++;
                                    }

                                    pendingDates[0]--;
                                    if (pendingDates[0] == 0) {
                                        tvTotalTasks.setText(String.valueOf(totalTasks[0]));
                                        tvCompletedTasks.setText(String.valueOf(completedTasks[0]));
                                    }
                                });
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load task stats", Toast.LENGTH_SHORT).show()
                );
    }

    private void loadCurrentStreak() {
        if (currentUser == null) return;

        Calendar cal = Calendar.getInstance();
        calculateStreak(cal, 0);
    }

    private void calculateStreak(Calendar cal, int streak) {
        String uid = currentUser.getUid();
        String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(cal.getTime());

        FirebaseFirestore.getInstance()
                .collection("Tasks")
                .document(uid)
                .collection("dates")
                .document(dateKey)
                .collection("items")
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (snapshot.isEmpty()) {
                        tvStreak.setText(streak + " 🔥");
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
                        calculateStreak(cal, streak + 1);
                    } else {
                        tvStreak.setText(streak + " 🔥");
                    }
                })
                .addOnFailureListener(e -> tvStreak.setText("0 🔥"));
    }
}
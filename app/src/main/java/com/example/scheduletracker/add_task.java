package com.example.scheduletracker;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class add_task extends AppCompatActivity {

    EditText etTaskTitle, etTaskDesc;
    TextView txtStartDate, txtEndDate;
    LinearLayout layoutStartDate, layoutEndDate;
    RadioGroup rgPriority;
    MaterialButton btnSaveTask;

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    Calendar startCal = Calendar.getInstance();
    Calendar endCal = Calendar.getInstance();
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbarAddTask);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Bind views
        etTaskTitle = findViewById(R.id.etTaskTitle);
        etTaskDesc = findViewById(R.id.etTaskDesc);
        txtStartDate = findViewById(R.id.txtStartDate);
        txtEndDate = findViewById(R.id.txtEndDate);
        layoutStartDate = findViewById(R.id.layoutStartDate);
        layoutEndDate = findViewById(R.id.layoutEndDate);
        rgPriority = findViewById(R.id.rgPriority);
        btnSaveTask = findViewById(R.id.btnSaveTask);

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Date pickers
        layoutStartDate.setOnClickListener(v -> pickDate(startCal, txtStartDate));
        layoutEndDate.setOnClickListener(v -> pickDate(endCal, txtEndDate));

        // Save
        btnSaveTask.setOnClickListener(v -> saveTaskRangeToFirestore());
    }

    private void pickDate(Calendar cal, TextView target) {
        new DatePickerDialog(this, (view, y, m, d) -> {
            cal.set(y, m, d);
            target.setText(dateFormat.format(cal.getTime()));
        },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void saveTaskRangeToFirestore() {
        String title = etTaskTitle.getText().toString().trim();
        String desc = etTaskDesc.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            etTaskTitle.setError("Title required");
            return;
        }

        if (rgPriority.getCheckedRadioButtonId() == -1) {
            Toast.makeText(this, "Select priority", Toast.LENGTH_SHORT).show();
            return;
        }

        if (txtStartDate.getText().toString().contains("Select") ||
                txtEndDate.getText().toString().contains("Select")) {
            Toast.makeText(this, "Select start & end date", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        if (startCal.after(endCal)) {
            Toast.makeText(this, "Start date cannot be after end date", Toast.LENGTH_SHORT).show();
            return;
        }

        String priority = ((RadioButton) findViewById(rgPriority.getCheckedRadioButtonId()))
                .getText().toString();

        String uid = mAuth.getCurrentUser().getUid();

        WriteBatch batch = db.batch();
        Calendar loopCal = (Calendar) startCal.clone();

        while (!loopCal.after(endCal)) {
            String dateKey = dateFormat.format(loopCal.getTime());

            // 1. Reference to the parent date document
            DocumentReference dateDocRef = db.collection("Tasks")
                    .document(uid)
                    .collection("dates")
                    .document(dateKey);

            // 2. Reference to the new task item document in the subcollection
            DocumentReference taskItemRef = dateDocRef.collection("items").document();

            // Create the task data
            Map<String, Object> task = new HashMap<>();
            task.put("title", title);
            task.put("desc", desc);
            task.put("priority", priority);
            task.put("completed", false);
            task.put("date", dateKey);
            task.put("userId", uid);

            // Add operations to the batch
            // Explicitly create/merge the date document to ensure it exists for queries
            batch.set(dateDocRef, new HashMap<>(), SetOptions.merge());
            // Create the task item document
            batch.set(taskItemRef, task);

            loopCal.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Commit the batch
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Tasks added successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(add_task.this, dashboard.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error adding tasks: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}

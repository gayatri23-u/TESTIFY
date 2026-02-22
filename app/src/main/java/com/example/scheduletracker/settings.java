package com.example.scheduletracker;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class settings extends AppCompatActivity {

    MaterialToolbar toolbarSettings;
    View rowProfile, rowEditProfile, rowChangePassword, rowDeleteAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Toolbar
        toolbarSettings = findViewById(R.id.toolbarSettings);
        setSupportActionBar(toolbarSettings);
        toolbarSettings.setNavigationOnClickListener(v -> finish());

        // Rows
        rowProfile = findViewById(R.id.rowProfile);
        rowEditProfile = findViewById(R.id.rowEditProfile);
        rowChangePassword = findViewById(R.id.rowChangePassword);
        rowDeleteAccount = findViewById(R.id.rowDeleteAccount);

        // Clicks
        rowProfile.setOnClickListener(v ->
                startActivity(new Intent(settings.this, profile.class))
        );

        rowEditProfile.setOnClickListener(v ->
                startActivity(new Intent(settings.this, edit_profile.class))
        );

        rowChangePassword.setOnClickListener(v ->
                startActivity(new Intent(settings.this, change_password.class))
        );

        rowDeleteAccount.setOnClickListener(v -> showDeleteDialog());
    }

    private void showDeleteDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account?")
                .setMessage("This will permanently delete your account and all your data. This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> showReAuthDialog())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showReAuthDialog() {
        final EditText etPassword = new EditText(this);
        etPassword.setHint("Enter your password");
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Confirm Password")
                .setMessage("Please enter your password to continue.")
                .setView(etPassword)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String password = etPassword.getText().toString().trim();
                    if (!password.isEmpty()) {
                        reAuthenticateAndDelete(password);
                    } else {
                        Toast.makeText(this, "Password required", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void reAuthenticateAndDelete(String password) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);

        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    // Now delete account
                    user.delete()
                            .addOnSuccessListener(aVoid1 -> {
                                Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(settings.this, login.class));
                                finishAffinity();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to delete account. Try again.", Toast.LENGTH_SHORT).show()
                            );
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Wrong password. Re-authentication failed.", Toast.LENGTH_SHORT).show()
                );
    }
}
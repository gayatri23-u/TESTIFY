package com.example.scheduletracker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class edit_profile extends AppCompatActivity {

    //  Your Cloudinary config
    private static final String CLOUD_NAME = "dz7sqdpxa";
    private static final String UPLOAD_PRESET = "skillhub_profile";

    MaterialToolbar toolbarEditProfile;
    ImageView imgEditProfile;
    TextView btnChangePhoto;
    TextInputEditText etFullName, etEmail;
    MaterialButton btnSaveProfile;

    FirebaseAuth mAuth;
    FirebaseFirestore db;
    Uri selectedImageUri = null;

    ActivityResultLauncher<Intent> imagePickerLauncher;
    OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        toolbarEditProfile = findViewById(R.id.toolbarEditProfile);
        setSupportActionBar(toolbarEditProfile);
        toolbarEditProfile.setNavigationOnClickListener(v -> finish());

        imgEditProfile = findViewById(R.id.imgEditProfile);
        btnChangePhoto = findViewById(R.id.btnChangePhoto);
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) etEmail.setText(user.getEmail());

        loadUserProfile();

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        imgEditProfile.setImageURI(selectedImageUri);
                    }
                }
        );

        btnChangePhoto.setOnClickListener(v -> openGallery());
        btnSaveProfile.setOnClickListener(v -> saveProfile());
    }

    private void loadUserProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        String photoUrl = doc.getString("photoUrl");
                        if (name != null) etFullName.setText(name);
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(photoUrl)
                                    .circleCrop()
                                    .into(imgEditProfile);
                        }
                    }
                });
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void saveProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String name = etFullName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImageUri != null) {
            uploadToCloudinary(user.getUid(), name, selectedImageUri);
        } else {
            updateUserData(user.getUid(), name, null);
        }
    }

    private void uploadToCloudinary(String uid, String name, Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            byte[] bytes = getBytes(inputStream);

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                            "file",
                            "profile.jpg",
                            RequestBody.create(bytes, MediaType.parse("image/*"))
                    )
                    .addFormDataPart("upload_preset", UPLOAD_PRESET)
                    .build();

            Request request = new Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload")
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() ->
                            Toast.makeText(edit_profile.this, "Image upload failed", Toast.LENGTH_SHORT).show()
                    );
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        runOnUiThread(() ->
                                Toast.makeText(edit_profile.this, "Upload error: " + response.code(), Toast.LENGTH_SHORT).show()
                        );
                        return;
                    }

                    String res = response.body().string();
                    try {
                        JSONObject json = new JSONObject(res);
                        String secureUrl = json.getString("secure_url");
                        runOnUiThread(() -> updateUserData(uid, name, secureUrl));
                    } catch (Exception e) {
                        runOnUiThread(() ->
                                Toast.makeText(edit_profile.this, "Response parse error", Toast.LENGTH_SHORT).show()
                        );
                    }
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "Could not read image", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUserData(String uid, String name, String photoUrl) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        if (photoUrl != null) map.put("photoUrl", photoUrl);

        db.collection("users").document(uid)
                .set(map)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                );
    }

    private byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[4096];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }
}
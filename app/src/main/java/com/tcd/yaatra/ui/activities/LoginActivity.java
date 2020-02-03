package com.tcd.yaatra.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.tcd.yaatra.R;
import com.tcd.yaatra.databinding.ActivityLoginBinding;
import com.tcd.yaatra.ui.viewmodels.LoginActivityViewModel;

import javax.inject.Inject;

public class LoginActivity extends BaseActivity<ActivityLoginBinding> {

    @Inject
    LoginActivityViewModel loginActivityViewModel;

    SharedPreferences preferences;
    SharedPreferences.Editor editor;


    @Override
    int getLayoutResourceId() {
        return R.layout.activity_login;
    }

    @Override
    public void initEventHandlers() {
        super.initEventHandlers();
        layoutDataBinding.login.setOnClickListener(view -> handleOnLoginButtonClick());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.preferences = getApplicationContext().getSharedPreferences("LoginPref", 0); // 0 - for private mode
        this.editor = preferences.edit();
    }

    private void handleOnLoginButtonClick() {


        String username = layoutDataBinding.username.getText().toString();
        String password = layoutDataBinding.password.getText().toString();

        loginActivityViewModel.authenticateUser(username, password)
                .observe(this, loginResponse -> {
                    switch (loginResponse.getState()) {
                        case LOADING:
                            layoutDataBinding.progressBarOverlay.setVisibility(View.VISIBLE);
                            break;

                        case SUCCESS:
                            layoutDataBinding.progressBarOverlay.setVisibility(View.GONE);

                            Intent myIntent = new Intent(LoginActivity.this, DailyCommuteActivity.class);
                            this.editor.putString("token", loginResponse.getData().getAuthToken());
                            this.editor.commit();
                            startActivity(myIntent);
                            break;

                        case FAILURE:
                            layoutDataBinding.progressBarOverlay.setVisibility(View.GONE);
                            Toast.makeText(this, "Invalid Credentials", Toast.LENGTH_SHORT).show();
                            break;
                    }
                });
    }

}

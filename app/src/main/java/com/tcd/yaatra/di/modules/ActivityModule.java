package com.tcd.yaatra.di.modules;

import com.tcd.yaatra.ui.activities.LaunchActivity;
import com.tcd.yaatra.ui.activities.LoginActivity;
import com.tcd.yaatra.ui.activities.MenuContainerActivity;
import com.tcd.yaatra.ui.activities.RegisterActivity;

import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public interface ActivityModule {

    @ContributesAndroidInjector
    LoginActivity contributeLoginActivity();

    @ContributesAndroidInjector
    LaunchActivity contributeLaunchActivity();

    @ContributesAndroidInjector
    MenuContainerActivity contributeMenuContainerActivity();

    @ContributesAndroidInjector
    RegisterActivity contributeRegisterActivity();
}
